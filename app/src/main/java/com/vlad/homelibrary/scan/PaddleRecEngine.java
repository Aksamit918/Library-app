package com.vlad.homelibrary.scan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

public final class PaddleRecEngine implements AutoCloseable {

    private static final String TAG = "PaddleRec";

    public static final class LineResult {
        public final String text;
        public final float confidence;

        public LineResult(String text, float confidence) {
            this.text = text;
            this.confidence = confidence;
        }
    }

    private static final int DEFAULT_REC_HEIGHT = 48;
    private static final int MAX_WIDTH = 3200;
    private static final int MIN_WIDTH = 16;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final List<String> characterTable;
    private final String inputName;
    private final int inputHeight;

    private PaddleRecEngine(OrtEnvironment env,
                            OrtSession session,
                            List<String> characterTable,
                            String inputName,
                            int inputHeight) {
        this.env = env;
        this.session = session;
        this.characterTable = characterTable;
        this.inputName = inputName;
        this.inputHeight = inputHeight;
    }

    @NonNull
    public static PaddleRecEngine open(Context context,
                                       PaddleOcrModelManager models,
                                       PaddleOcrModelManager.ScriptPack pack) throws Exception {
        File modelFile = models.getRecModelFile(context, pack);
        File dictFile = models.getDictFile(context, pack);
        if (!modelFile.exists() || !dictFile.exists()) {
            throw new IllegalStateException("Missing OCR pack: " + pack.id);
        }

        List<String> dict = loadDictionary(dictFile);
        List<String> characterTable = new ArrayList<>(dict.size() + 2);
        characterTable.add("");
        characterTable.addAll(dict);
        boolean dictHasSpace = false;
        for (String ch : dict) {
            if (" ".equals(ch)) {
                dictHasSpace = true;
                break;
            }
        }
        if (!dictHasSpace) {
            characterTable.add(" ");
        }

        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        OrtSession session = env.createSession(modelFile.getAbsolutePath(), options);

        Map.Entry<String, NodeInfo> inputEntry = session.getInputInfo().entrySet().iterator().next();
        String inputName = inputEntry.getKey();
        int height = DEFAULT_REC_HEIGHT;
        Object info = inputEntry.getValue().getInfo();
        if (info instanceof TensorInfo) {
            long[] shape = ((TensorInfo) info).getShape();
            if (shape != null && shape.length >= 4) {
                if (shape[1] == 3 && shape[2] > 0) {
                    height = (int) shape[2];
                } else if (shape[3] == 3 && shape[1] > 0) {
                    height = (int) shape[1];
                } else if (shape[2] > 0 && shape[2] <= 64) {
                    height = (int) shape[2];
                }
            }
        }

        Log.i(TAG, "Opened " + pack.id + " h=" + height + " chars=" + characterTable.size());
        return new PaddleRecEngine(env, session, characterTable, inputName, height);
    }

    @NonNull
    public LineResult recognizeLine(@NonNull Bitmap lineBitmap) throws Exception {
        float[][][][] nchw = preprocessToNchw(lineBitmap, inputHeight);
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, nchw);
             OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
            Object value = result.get(0).getValue();
            float[][] sequence = extractSequence(value);
            if (sequence == null) {
                Log.w(TAG, "Unexpected output type: "
                        + (value == null ? "null" : value.getClass().getName()));
                return new LineResult("", 0f);
            }
            return decodeSequence(sequence);
        }
    }

    @NonNull
    public List<LineResult> recognizeImage(@NonNull Bitmap bitmap) throws Exception {
        return recognizeImage(bitmap, false);
    }

    @NonNull
    public List<LineResult> recognizeImage(@NonNull Bitmap bitmap, boolean alreadyLineCrop)
            throws Exception {
        LineResult whole = recognizeLine(bitmap);
        if (alreadyLineCrop) {
            LineResult best = whole;
            Bitmap[] variants = new Bitmap[]{
                    OcrImagePrep.contrastStretchBrightInk(bitmap),
                    OcrImagePrep.contrastStretch(bitmap),
                    OcrImagePrep.invert(OcrImagePrep.contrastStretchBrightInk(bitmap))
            };
            try {
                for (Bitmap variant : variants) {
                    LineResult alt = recognizeLine(variant);
                    if (score(alt) > score(best)) {
                        best = alt;
                    }
                }
            } finally {
                for (Bitmap variant : variants) {
                    if (variant != bitmap && variant != null && !variant.isRecycled()) {
                        variant.recycle();
                    }
                }
            }
            return Collections.singletonList(best);
        }

        List<Bitmap> lines = splitIntoTextLines(bitmap);
        if (lines.isEmpty()) {
            Bitmap bright = OcrImagePrep.contrastStretchBrightInk(bitmap);
            try {
                LineResult alt = recognizeLine(bright);
                if (score(alt) > score(whole)) {
                    return Collections.singletonList(alt);
                }
            } finally {
                if (bright != bitmap && !bright.isRecycled()) {
                    bright.recycle();
                }
            }
            return Collections.singletonList(whole);
        }

        List<LineResult> splitResults = new ArrayList<>();
        float splitScore = 0f;
        try {
            for (Bitmap line : lines) {
                LineResult result = recognizeLine(line);
                Bitmap bright = OcrImagePrep.contrastStretchBrightInk(line);
                try {
                    LineResult alt = recognizeLine(bright);
                    if (score(alt) > score(result)) {
                        result = alt;
                    }
                } finally {
                    if (bright != line && !bright.isRecycled()) {
                        bright.recycle();
                    }
                }
                if (!result.text.isBlank()) {
                    splitResults.add(result);
                    splitScore += score(result);
                }
            }
        } finally {
            for (Bitmap line : lines) {
                if (line != bitmap && !line.isRecycled()) {
                    line.recycle();
                }
            }
        }

        float wholeScore = score(whole);
        if (splitResults.size() >= 2 && splitScore >= wholeScore * 0.75f) {
            return splitResults;
        }
        if (splitResults.isEmpty() || wholeScore >= splitScore) {
            return Collections.singletonList(whole);
        }
        return splitResults;
    }

    private static float score(LineResult result) {
        if (result == null || result.text == null || result.text.isBlank()) {
            return 0f;
        }
        return result.confidence * Math.max(1, result.text.length());
    }

    @Nullable
    private static float[][] extractSequence(Object value) throws Exception {
        if (value instanceof float[][][]) {
            return ((float[][][]) value)[0];
        }
        if (value instanceof float[][]) {
            return (float[][]) value;
        }
        if (value instanceof OnnxTensor) {
            return extractSequence(((OnnxTensor) value).getValue());
        }
        return null;
    }

    private LineResult decodeSequence(float[][] sequence) {
        if (sequence.length == 0) {
            return new LineResult("", 0f);
        }
        int numClasses = sequence[0].length;
        if (numClasses > 0 && characterTable.size() != numClasses) {
            Log.w(TAG, "Char table size " + characterTable.size()
                    + " != model classes " + numClasses);
        }

        StringBuilder text = new StringBuilder();
        float scoreSum = 0f;
        int scoreCount = 0;
        int prevIndex = -1;

        for (float[] step : sequence) {
            int bestIndex = 0;
            float bestScore = step[0];
            for (int i = 1; i < step.length; i++) {
                if (step[i] > bestScore) {
                    bestScore = step[i];
                    bestIndex = i;
                }
            }
            if (bestIndex == prevIndex) {
                continue;
            }
            prevIndex = bestIndex;
            if (bestIndex == 0 || bestIndex >= characterTable.size() || bestIndex >= numClasses) {
                continue;
            }
            text.append(characterTable.get(bestIndex));
            float conf = bestScore;
            if (conf > 1f || conf < 0f) {
                conf = softmaxProb(step, bestIndex);
            }
            scoreSum += conf;
            scoreCount++;
        }

        float confidence = scoreCount == 0 ? 0f : scoreSum / scoreCount;
        if (confidence > 1f) {
            confidence = 1f;
        }
        return new LineResult(text.toString().trim(), confidence);
    }

    private static float softmaxProb(float[] logits, int index) {
        float max = logits[0];
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > max) {
                max = logits[i];
            }
        }
        double sum = 0;
        for (float logit : logits) {
            sum += Math.exp(logit - max);
        }
        if (sum <= 0) {
            return 0f;
        }
        return (float) (Math.exp(logits[index] - max) / sum);
    }

    private static float[][][][] preprocessToNchw(Bitmap source, int targetHeight) {
        int srcW = Math.max(1, source.getWidth());
        int srcH = Math.max(1, source.getHeight());
        int targetWidth = Math.round(srcW * (targetHeight / (float) srcH));
        targetWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, targetWidth));
        targetWidth = Math.max(MIN_WIDTH, (targetWidth + 7) / 8 * 8);

        Bitmap scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
        int[] pixels = new int[targetWidth * targetHeight];
        scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight);
        if (scaled != source && !scaled.isRecycled()) {
            scaled.recycle();
        }

        float[][][][] nchw = new float[1][3][targetHeight][targetWidth];
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int color = pixels[y * targetWidth + x];
                float r = ((color >> 16) & 0xFF) / 255f;
                float g = ((color >> 8) & 0xFF) / 255f;
                float b = (color & 0xFF) / 255f;
                nchw[0][0][y][x] = (b - 0.5f) / 0.5f;
                nchw[0][1][y][x] = (g - 0.5f) / 0.5f;
                nchw[0][2][y][x] = (r - 0.5f) / 0.5f;
            }
        }
        return nchw;
    }

    static List<Bitmap> splitIntoTextLines(Bitmap source) {
        List<Bitmap> lines = new ArrayList<>();
        int width = source.getWidth();
        int height = source.getHeight();
        if (width < 8 || height < 16) {
            return lines;
        }

        Bitmap gray = toGray(source);
        int[] pixels = new int[width * height];
        gray.getPixels(pixels, 0, width, 0, 0, width, height);
        if (gray != source && !gray.isRecycled()) {
            gray.recycle();
        }

        int[] projection = new int[height];
        for (int y = 0; y < height; y++) {
            int darkness = 0;
            for (int x = 0; x < width; x++) {
                if ((pixels[y * width + x] & 0xFF) < 190) {
                    darkness++;
                }
            }
            projection[y] = darkness;
        }

        int threshold = Math.max(3, width / 50);
        boolean inLine = false;
        int start = 0;
        for (int y = 0; y < height; y++) {
            boolean ink = projection[y] >= threshold;
            if (ink && !inLine) {
                inLine = true;
                start = y;
            } else if (!ink && inLine) {
                inLine = false;
                maybeAddLine(source, lines, start, y, width, height);
            }
        }
        if (inLine) {
            maybeAddLine(source, lines, start, height, width, height);
        }

        if (lines.size() <= 1 || lines.size() > 10) {
            for (Bitmap line : lines) {
                if (!line.isRecycled()) {
                    line.recycle();
                }
            }
            lines.clear();
        }
        return lines;
    }

    private static void maybeAddLine(Bitmap source, List<Bitmap> lines,
                                     int start, int end, int width, int height) {
        if (end - start < Math.max(10, height / 40)) {
            return;
        }
        int top = Math.max(0, start - 2);
        int bottom = Math.min(height, end + 2);
        lines.add(Bitmap.createBitmap(source, 0, top, width, bottom - top));
    }

    private static Bitmap toGray(Bitmap source) {
        Bitmap gray = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(gray);
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(source, 0, 0, paint);
        return gray;
    }

    private static List<String> loadDictionary(File dictFile) throws Exception {
        List<String> chars = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(dictFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                chars.add(line);
            }
        }
        return chars;
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (Exception ignored) {
        }
    }
}
