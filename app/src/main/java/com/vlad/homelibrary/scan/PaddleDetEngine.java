package com.vlad.homelibrary.scan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public final class PaddleDetEngine implements AutoCloseable {

    private static final String TAG = "PaddleDet";

    private static final int LIMIT_SIDE = 960;
    private static final float DB_THRESH = 0.3f;
    private static final float BOX_THRESH = 0.55f;
    private static final float UNCLIP_RATIO = 1.6f;
    private static final int MIN_BOX_SIDE = 8;
    private static final int MAX_BOXES = 32;

    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    public static final class TextBox {
        public final Rect bounds;
        public final float score;

        public TextBox(Rect bounds, float score) {
            this.bounds = bounds;
            this.score = score;
        }
    }

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;

    private PaddleDetEngine(OrtEnvironment env, OrtSession session, String inputName) {
        this.env = env;
        this.session = session;
        this.inputName = inputName;
    }

    @NonNull
    public static PaddleDetEngine open(Context context, PaddleOcrModelManager models) throws Exception {
        File modelFile = models.getDetModelFile(context);
        if (!modelFile.exists() || modelFile.length() < PaddleOcrModelManager.DET_MIN_BYTES) {
            throw new IllegalStateException("Missing detection model");
        }

        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        OrtSession session = env.createSession(modelFile.getAbsolutePath(), options);

        Map.Entry<String, NodeInfo> inputEntry = session.getInputInfo().entrySet().iterator().next();
        Log.i(TAG, "Opened det model input=" + inputEntry.getKey());
        return new PaddleDetEngine(env, session, inputEntry.getKey());
    }

    @NonNull
    public List<TextBox> detect(@NonNull Bitmap bitmap) throws Exception {
        int srcW = bitmap.getWidth();
        int srcH = bitmap.getHeight();
        if (srcW < 8 || srcH < 8) {
            return Collections.emptyList();
        }

        ResizeInfo resize = computeResize(srcW, srcH, LIMIT_SIDE);
        float[][][][] nchw = preprocess(bitmap, resize);

        try (OnnxTensor tensor = OnnxTensor.createTensor(env, nchw);
             OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
            float[][] map = extractProbMap(result.get(0).getValue());
            if (map == null) {
                Log.w(TAG, "Unexpected det output");
                return Collections.emptyList();
            }
            maybeApplySigmoid(map);
            List<TextBox> boxes = boxesFromProbMap(map, resize, srcW, srcH);
            boxes = mergeOverlapping(boxes);
            Log.i(TAG, "Detected " + boxes.size() + " text boxes");
            return boxes;
        }
    }

    private static void maybeApplySigmoid(float[][] map) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (float[] row : map) {
            for (float v : row) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        if (min >= 0f && max <= 1.01f) {
            return;
        }
        for (int y = 0; y < map.length; y++) {
            for (int x = 0; x < map[y].length; x++) {
                float v = map[y][x];
                map[y][x] = 1f / (1f + (float) Math.exp(-v));
            }
        }
    }

    private static List<TextBox> mergeOverlapping(List<TextBox> input) {
        if (input.size() <= 1) {
            return input;
        }
        List<TextBox> boxes = new ArrayList<>(input);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < boxes.size(); i++) {
                for (int j = i + 1; j < boxes.size(); j++) {
                    Rect a = boxes.get(i).bounds;
                    Rect b = boxes.get(j).bounds;
                    if (!shouldMerge(a, b)) {
                        continue;
                    }
                    Rect merged = new Rect(a);
                    merged.union(b);
                    float score = Math.max(boxes.get(i).score, boxes.get(j).score);
                    boxes.set(i, new TextBox(merged, score));
                    boxes.remove(j);
                    changed = true;
                    break;
                }
                if (changed) {
                    break;
                }
            }
        }
        boxes.sort(Comparator
                .comparingInt((TextBox box) -> box.bounds.top)
                .thenComparingInt(box -> box.bounds.left));
        return boxes;
    }

    private static boolean shouldMerge(Rect a, Rect b) {
        Rect inter = new Rect();
        boolean overlaps = inter.setIntersect(a, b);
        if (overlaps) {
            int interArea = inter.width() * inter.height();
            int minArea = Math.min(a.width() * a.height(), b.width() * b.height());
            if (minArea > 0 && interArea >= minArea * 0.4f) {
                return true;
            }
        }
        int midAy = (a.top + a.bottom) / 2;
        int midBy = (b.top + b.bottom) / 2;
        int maxH = Math.max(a.height(), b.height());
        if (Math.abs(midAy - midBy) > maxH * 0.45f) {
            return false;
        }
        int gap = Math.max(a.left, b.left) - Math.min(a.right, b.right);
        return gap <= Math.max(8, maxH / 2);
    }

    @Nullable
    private static float[][] extractProbMap(Object value) throws Exception {
        if (value instanceof float[][][][]) {
            return ((float[][][][]) value)[0][0];
        }
        if (value instanceof float[][][]) {
            return ((float[][][]) value)[0];
        }
        if (value instanceof float[][]) {
            return (float[][]) value;
        }
        if (value instanceof OnnxTensor) {
            return extractProbMap(((OnnxTensor) value).getValue());
        }
        return null;
    }

    private static List<TextBox> boxesFromProbMap(float[][] map,
                                                  ResizeInfo resize,
                                                  int srcW,
                                                  int srcH) {
        int h = map.length;
        int w = map[0].length;
        boolean[][] visited = new boolean[h][w];
        List<TextBox> boxes = new ArrayList<>();

        int[] dx = {1, -1, 0, 0, 1, 1, -1, -1};
        int[] dy = {0, 0, 1, -1, 1, -1, 1, -1};

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (visited[y][x] || map[y][x] < DB_THRESH) {
                    continue;
                }

                int minX = x;
                int maxX = x;
                int minY = y;
                int maxY = y;
                float scoreSum = 0f;
                int scoreCount = 0;

                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, y});
                visited[y][x] = true;

                while (!queue.isEmpty()) {
                    int[] p = queue.removeFirst();
                    int cx = p[0];
                    int cy = p[1];
                    scoreSum += map[cy][cx];
                    scoreCount++;
                    if (cx < minX) minX = cx;
                    if (cx > maxX) maxX = cx;
                    if (cy < minY) minY = cy;
                    if (cy > maxY) maxY = cy;

                    for (int i = 0; i < 8; i++) {
                        int nx = cx + dx[i];
                        int ny = cy + dy[i];
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h || visited[ny][nx]) {
                            continue;
                        }
                        if (map[ny][nx] < DB_THRESH) {
                            continue;
                        }
                        visited[ny][nx] = true;
                        queue.add(new int[]{nx, ny});
                    }
                }

                if (scoreCount < 12) {
                    continue;
                }
                float score = scoreSum / scoreCount;
                if (score < BOX_THRESH) {
                    continue;
                }

                float boxW = maxX - minX + 1f;
                float boxH = maxY - minY + 1f;
                float expand = ((boxW + boxH) * 0.5f) * (UNCLIP_RATIO - 1f) * 0.5f;
                float left = minX - expand;
                float top = minY - expand;
                float right = maxX + 1f + expand;
                float bottom = maxY + 1f + expand;

                float sx = left / resize.scale;
                float sy = top / resize.scale;
                float ex = right / resize.scale;
                float ey = bottom / resize.scale;

                int l = clamp((int) Math.floor(sx), 0, srcW - 1);
                int t = clamp((int) Math.floor(sy), 0, srcH - 1);
                int r = clamp((int) Math.ceil(ex), l + 1, srcW);
                int b = clamp((int) Math.ceil(ey), t + 1, srcH);
                if (r - l < MIN_BOX_SIDE || b - t < MIN_BOX_SIDE) {
                    continue;
                }
                boxes.add(new TextBox(new Rect(l, t, r, b), score));
            }
        }

        boxes.sort(Comparator
                .comparingInt((TextBox box) -> box.bounds.top / Math.max(12, srcH / 40))
                .thenComparingInt(box -> box.bounds.left));

        if (boxes.size() > MAX_BOXES) {
            boxes.sort((a, b) -> Float.compare(b.score, a.score));
            boxes = new ArrayList<>(boxes.subList(0, MAX_BOXES));
            boxes.sort(Comparator
                    .comparingInt((TextBox box) -> box.bounds.top / Math.max(12, srcH / 40))
                    .thenComparingInt(box -> box.bounds.left));
        }
        return boxes;
    }

    private static float[][][][] preprocess(Bitmap source, ResizeInfo resize) {
        Bitmap scaled = Bitmap.createScaledBitmap(source, resize.resizedW, resize.resizedH, true);
        int[] pixels = new int[resize.resizedW * resize.resizedH];
        scaled.getPixels(pixels, 0, resize.resizedW, 0, 0, resize.resizedW, resize.resizedH);
        if (scaled != source && !scaled.isRecycled()) {
            scaled.recycle();
        }

        float[][][][] nchw = new float[1][3][resize.netH][resize.netW];
        for (int y = 0; y < resize.netH; y++) {
            for (int x = 0; x < resize.netW; x++) {
                float b;
                float g;
                float r;
                if (x < resize.resizedW && y < resize.resizedH) {
                    int color = pixels[y * resize.resizedW + x];
                    r = ((color >> 16) & 0xFF) / 255f;
                    g = ((color >> 8) & 0xFF) / 255f;
                    b = (color & 0xFF) / 255f;
                } else {
                    r = g = b = 0f;
                }
                nchw[0][0][y][x] = (b - MEAN[0]) / STD[0];
                nchw[0][1][y][x] = (g - MEAN[1]) / STD[1];
                nchw[0][2][y][x] = (r - MEAN[2]) / STD[2];
            }
        }
        return nchw;
    }

    private static ResizeInfo computeResize(int srcW, int srcH, int limitSide) {
        float scale = 1f;
        int longSide = Math.max(srcW, srcH);
        if (longSide > limitSide) {
            scale = limitSide / (float) longSide;
        }
        int resizedW = Math.max(32, Math.round(srcW * scale));
        int resizedH = Math.max(32, Math.round(srcH * scale));
        int netW = (resizedW + 31) / 32 * 32;
        int netH = (resizedH + 31) / 32 * 32;
        float mapScale = resizedW / (float) srcW;
        return new ResizeInfo(resizedW, resizedH, netW, netH, mapScale);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (Exception ignored) {
        }
    }

    private static final class ResizeInfo {
        final int resizedW;
        final int resizedH;
        final int netW;
        final int netH;
        final float scale;

        ResizeInfo(int resizedW, int resizedH, int netW, int netH, float scale) {
            this.resizedW = resizedW;
            this.resizedH = resizedH;
            this.netW = netW;
            this.netH = netH;
            this.scale = scale;
        }
    }
}
