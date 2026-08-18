package com.vlad.homelibrary.scan;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class BookCoverDetector {

    private static final int WORK_MAX_SIDE = 360;
    private static final float DEFAULT_INSET = 0.08f;

    private BookCoverDetector() {
    }

    @NonNull
    public static float[] detect(@NonNull Bitmap bitmap) {
        int srcW = bitmap.getWidth();
        int srcH = bitmap.getHeight();
        if (srcW < 16 || srcH < 16) {
            return insetQuad(srcW, srcH, DEFAULT_INSET);
        }

        int maxSide = Math.max(srcW, srcH);
        float downScale = Math.min(1f, WORK_MAX_SIDE / (float) maxSide);
        int workW = Math.max(16, Math.round(srcW * downScale));
        int workH = Math.max(16, Math.round(srcH * downScale));

        Bitmap work = bitmap;
        boolean recycleWork = false;
        if (workW != srcW || workH != srcH) {
            work = Bitmap.createScaledBitmap(bitmap, workW, workH, true);
            recycleWork = work != bitmap;
        }

        int[] pixels = new int[workW * workH];
        work.getPixels(pixels, 0, workW, 0, 0, workW, workH);
        if (recycleWork && !work.isRecycled()) {
            work.recycle();
        }

        int[] gray = new int[workW * workH];
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            gray[i] = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100;
        }
        gray = gaussianBlur(gray, workW, workH);

        Gradients gradients = sobel(gray, workW, workH);
        boolean[] edges = canny(gradients, workW, workH);

        float[] best = null;
        float bestScore = -1f;

        float[] hough = detectByHough(edges, gradients.magnitude, workW, workH);
        float houghScore = scoreQuad(hough, gradients.magnitude, workW, workH);
        if (houghScore > bestScore) {
            best = hough;
            bestScore = houghScore;
        }

        float[] contour = detectByContour(edges, workW, workH);
        float contourScore = scoreQuad(contour, gradients.magnitude, workW, workH);
        if (contourScore > bestScore) {
            best = contour;
            bestScore = contourScore;
        }

        float[] sides = detectBySideScan(gradients.magnitude, workW, workH);
        float sideScore = scoreQuad(sides, gradients.magnitude, workW, workH);
        if (sideScore > bestScore) {
            best = sides;
            bestScore = sideScore;
        }

        if (best == null || bestScore < 0.08f) {
            return insetQuad(srcW, srcH, DEFAULT_INSET);
        }

        return scaleQuad(best, srcW / (float) workW, srcH / (float) workH, srcW, srcH);
    }

    @NonNull
    public static float[] defaultQuad(int width, int height) {
        return insetQuad(width, height, DEFAULT_INSET);
    }

    @Nullable
    private static float[] detectByHough(@NonNull boolean[] edges, @NonNull float[] magnitude, int w, int h) {
        int nTheta = 180;
        int maxRho = (int) Math.ceil(Math.hypot(w, h));
        int rhoCount = maxRho * 2 + 1;
        int[] acc = new int[nTheta * rhoCount];

        double[] cos = new double[nTheta];
        double[] sin = new double[nTheta];
        for (int t = 0; t < nTheta; t++) {
            double rad = Math.toRadians(t);
            cos[t] = Math.cos(rad);
            sin[t] = Math.sin(rad);
        }

        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int i = row + x;
                if (!edges[i] || magnitude[i] < 20f) {
                    continue;
                }
                for (int t = 0; t < nTheta; t++) {
                    int rho = (int) Math.round(x * cos[t] + y * sin[t]) + maxRho;
                    if (rho >= 0 && rho < rhoCount) {
                        acc[t * rhoCount + rho]++;
                    }
                }
            }
        }

        int peakThreshold = Math.max(18, (int) (0.22f * strongest(acc)));
        List<HoughLine> lines = localMaxima(acc, nTheta, rhoCount, maxRho, peakThreshold);
        if (lines.size() < 4) {
            return null;
        }

        List<HoughLine> vertical = new ArrayList<>();
        List<HoughLine> horizontal = new ArrayList<>();
        for (HoughLine line : lines) {
            if (line.theta < 35 || line.theta > 145) {
                vertical.add(line);
            } else if (line.theta > 55 && line.theta < 125) {
                horizontal.add(line);
            }
        }
        if (vertical.size() < 2 || horizontal.size() < 2) {
            return null;
        }

        vertical.sort(Comparator.comparingInt(a -> a.votes));
        horizontal.sort(Comparator.comparingInt(a -> a.votes));

        HoughLine left = null;
        HoughLine right = null;
        int bestVsep = -1;
        int fromV = Math.max(0, vertical.size() - 8);
        for (int i = fromV; i < vertical.size(); i++) {
            for (int j = i + 1; j < vertical.size(); j++) {
                int sep = Math.abs(vertical.get(i).rho - vertical.get(j).rho);
                if (sep > bestVsep) {
                    bestVsep = sep;
                    HoughLine a = vertical.get(i);
                    HoughLine b = vertical.get(j);
                    if (xIntercept(a, h / 2f) <= xIntercept(b, h / 2f)) {
                        left = a;
                        right = b;
                    } else {
                        left = b;
                        right = a;
                    }
                }
            }
        }

        HoughLine top = null;
        HoughLine bottom = null;
        int bestHsep = -1;
        int fromH = Math.max(0, horizontal.size() - 8);
        for (int i = fromH; i < horizontal.size(); i++) {
            for (int j = i + 1; j < horizontal.size(); j++) {
                int sep = Math.abs(horizontal.get(i).rho - horizontal.get(j).rho);
                if (sep > bestHsep) {
                    bestHsep = sep;
                    HoughLine a = horizontal.get(i);
                    HoughLine b = horizontal.get(j);
                    if (yIntercept(a, w / 2f) <= yIntercept(b, w / 2f)) {
                        top = a;
                        bottom = b;
                    } else {
                        top = b;
                        bottom = a;
                    }
                }
            }
        }

        if (left == null || right == null || top == null || bottom == null) {
            return null;
        }
        if (bestVsep < w * 0.18f || bestHsep < h * 0.18f) {
            return null;
        }

        float[] tl = intersect(top, left);
        float[] tr = intersect(top, right);
        float[] br = intersect(bottom, right);
        float[] bl = intersect(bottom, left);
        if (tl == null || tr == null || br == null || bl == null) {
            return null;
        }
        return new float[]{tl[0], tl[1], tr[0], tr[1], br[0], br[1], bl[0], bl[1]};
    }

    @Nullable
    private static float[] detectByContour(@NonNull boolean[] edges, int w, int h) {
        boolean[] dilated = dilate(edges, w, h);
        dilated = dilate(dilated, w, h);
        boolean[] visited = new boolean[w * h];
        float[] bestQuad = null;
        float bestArea = 0f;
        int[] stack = new int[w * h];

        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int start = row + x;
                if (!dilated[start] || visited[start]) {
                    continue;
                }
                List<Point> component = flood(dilated, visited, stack, w, h, start);
                if (component.size() < 40) {
                    continue;
                }
                Point[] hull = convexHull(component);
                if (hull.length < 4) {
                    continue;
                }
                Point[] approx = approxQuad(hull);
                if (approx == null || approx.length != 4) {
                    continue;
                }
                float[] quad = orderQuad(
                        approx[0].x, approx[0].y,
                        approx[1].x, approx[1].y,
                        approx[2].x, approx[2].y,
                        approx[3].x, approx[3].y
                );
                float area = quadArea(quad);
                if (area > bestArea) {
                    bestArea = area;
                    bestQuad = quad;
                }
            }
        }
        return bestQuad;
    }

    @Nullable
    private static float[] detectBySideScan(@NonNull float[] magnitude, int w, int h) {
        float[] rowEnergy = new float[h];
        float[] colEnergy = new float[w];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            float sum = 0f;
            for (int x = 0; x < w; x++) {
                float m = magnitude[row + x];
                sum += m;
                colEnergy[x] += m;
            }
            rowEnergy[y] = sum;
        }
        smooth1d(rowEnergy);
        smooth1d(colEnergy);

        int top = strongestFromStart(rowEnergy, 2, Math.max(3, h / 3), true);
        int bottom = strongestFromStart(rowEnergy, h - 3, Math.min(h - 3, (h * 2) / 3), false);
        int left = strongestFromStart(colEnergy, 2, Math.max(3, w / 3), true);
        int right = strongestFromStart(colEnergy, w - 3, Math.min(w - 3, (w * 2) / 3), false);

        if (right - left < w * 0.22f || bottom - top < h * 0.22f) {
            return null;
        }
        return new float[]{left, top, right, top, right, bottom, left, bottom};
    }

    private static int strongestFromStart(float[] energy, int start, int limit, boolean forward) {
        int best = start;
        float bestVal = -1f;
        if (forward) {
            int end = Math.min(limit, energy.length - 1);
            for (int i = start; i <= end; i++) {
                if (energy[i] > bestVal) {
                    bestVal = energy[i];
                    best = i;
                }
            }
        } else {
            int end = Math.max(limit, 0);
            for (int i = start; i >= end; i--) {
                if (energy[i] > bestVal) {
                    bestVal = energy[i];
                    best = i;
                }
            }
        }
        return best;
    }

    private static void smooth1d(float[] values) {
        if (values.length < 3) {
            return;
        }
        float[] copy = values.clone();
        for (int i = 1; i < values.length - 1; i++) {
            values[i] = (copy[i - 1] + copy[i] * 2f + copy[i + 1]) / 4f;
        }
    }

    private static float scoreQuad(@Nullable float[] quad, @NonNull float[] magnitude, int w, int h) {
        if (quad == null || !isUsableQuad(quad, w, h)) {
            return -1f;
        }
        float area = quadArea(quad);
        float areaFrac = area / (w * (float) h);
        if (areaFrac < 0.08f || areaFrac > 0.97f) {
            return -1f;
        }
        float rectangularity = rectangularity(quad);
        float edgeStrength = edgeStrength(quad, magnitude, w, h);
        return areaFrac * (0.35f + 0.65f * rectangularity) * (0.4f + 0.6f * edgeStrength);
    }

    private static boolean isUsableQuad(@NonNull float[] q, int w, int h) {
        for (int i = 0; i < 8; i += 2) {
            if (q[i] < -w * 0.08f || q[i] > w * 1.08f
                    || q[i + 1] < -h * 0.08f || q[i + 1] > h * 1.08f) {
                return false;
            }
        }
        clampQuad(q, w, h);
        return isConvex(q) && quadArea(q) > 16f;
    }

    private static float rectangularity(@NonNull float[] q) {
        float acc = 0f;
        for (int i = 0; i < 4; i++) {
            int a = i * 2;
            int b = ((i + 1) % 4) * 2;
            int c = ((i + 2) % 4) * 2;
            float ux = q[b] - q[a];
            float uy = q[b + 1] - q[a + 1];
            float vx = q[c] - q[b];
            float vy = q[c + 1] - q[b + 1];
            float ul = (float) Math.hypot(ux, uy);
            float vl = (float) Math.hypot(vx, vy);
            if (ul < 1f || vl < 1f) {
                return 0f;
            }
            acc += Math.abs((ux * vx + uy * vy) / (ul * vl));
        }
        return Math.max(0f, 1f - acc / 4f);
    }

    private static float edgeStrength(@NonNull float[] q, @NonNull float[] magnitude, int w, int h) {
        float sum = 0f;
        int samples = 0;
        for (int e = 0; e < 4; e++) {
            float x1 = q[e * 2];
            float y1 = q[e * 2 + 1];
            float x2 = q[((e + 1) % 4) * 2];
            float y2 = q[((e + 1) % 4) * 2 + 1];
            int steps = 24;
            for (int s = 0; s <= steps; s++) {
                float t = s / (float) steps;
                int x = Math.round(x1 + (x2 - x1) * t);
                int y = Math.round(y1 + (y2 - y1) * t);
                if (x >= 0 && x < w && y >= 0 && y < h) {
                    sum += magnitude[y * w + x];
                    samples++;
                }
            }
        }
        if (samples == 0) {
            return 0f;
        }
        return Math.min(1f, (sum / samples) / 80f);
    }

    @NonNull
    private static int[] gaussianBlur(@NonNull int[] src, int w, int h) {
        int[] tmp = new int[src.length];
        int[] dst = new int[src.length];
        int[] k = {1, 4, 6, 4, 1};
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int acc = 0;
                int div = 0;
                for (int i = -2; i <= 2; i++) {
                    int xx = clamp(x + i, 0, w - 1);
                    int weight = k[i + 2];
                    acc += src[row + xx] * weight;
                    div += weight;
                }
                tmp[row + x] = acc / div;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int acc = 0;
                int div = 0;
                for (int i = -2; i <= 2; i++) {
                    int yy = clamp(y + i, 0, h - 1);
                    int weight = k[i + 2];
                    acc += tmp[yy * w + x] * weight;
                    div += weight;
                }
                dst[y * w + x] = acc / div;
            }
        }
        return dst;
    }

    @NonNull
    private static Gradients sobel(@NonNull int[] gray, int w, int h) {
        float[] mag = new float[w * h];
        int[] dir = new int[w * h];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int gx = -gray[(y - 1) * w + (x - 1)] + gray[(y - 1) * w + (x + 1)]
                        - 2 * gray[y * w + (x - 1)] + 2 * gray[y * w + (x + 1)]
                        - gray[(y + 1) * w + (x - 1)] + gray[(y + 1) * w + (x + 1)];
                int gy = -gray[(y - 1) * w + (x - 1)] - 2 * gray[(y - 1) * w + x] - gray[(y - 1) * w + (x + 1)]
                        + gray[(y + 1) * w + (x - 1)] + 2 * gray[(y + 1) * w + x] + gray[(y + 1) * w + (x + 1)];
                int i = y * w + x;
                mag[i] = (float) Math.hypot(gx, gy);
                double angle = Math.toDegrees(Math.atan2(gy, gx));
                if (angle < 0) {
                    angle += 180;
                }
                if (angle < 22.5 || angle >= 157.5) {
                    dir[i] = 0;
                } else if (angle < 67.5) {
                    dir[i] = 1;
                } else if (angle < 112.5) {
                    dir[i] = 2;
                } else {
                    dir[i] = 3;
                }
            }
        }
        return new Gradients(mag, dir);
    }

    @NonNull
    private static boolean[] canny(@NonNull Gradients gradients, int w, int h) {
        float[] mag = gradients.magnitude;
        int[] dir = gradients.direction;
        boolean[] nms = new boolean[w * h];
        float maxMag = 0f;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                float m = mag[i];
                if (m > maxMag) {
                    maxMag = m;
                }
                float n1;
                float n2;
                switch (dir[i]) {
                    case 0:
                        n1 = mag[i - 1];
                        n2 = mag[i + 1];
                        break;
                    case 1:
                        n1 = mag[(y - 1) * w + (x + 1)];
                        n2 = mag[(y + 1) * w + (x - 1)];
                        break;
                    case 2:
                        n1 = mag[(y - 1) * w + x];
                        n2 = mag[(y + 1) * w + x];
                        break;
                    default:
                        n1 = mag[(y - 1) * w + (x - 1)];
                        n2 = mag[(y + 1) * w + (x + 1)];
                        break;
                }
                nms[i] = m >= n1 && m >= n2 && m > 0f;
            }
        }

        float high = Math.max(28f, maxMag * 0.18f);
        float low = high * 0.4f;
        boolean[] strong = new boolean[w * h];
        boolean[] weak = new boolean[w * h];
        for (int i = 0; i < nms.length; i++) {
            if (!nms[i]) {
                continue;
            }
            if (mag[i] >= high) {
                strong[i] = true;
            } else if (mag[i] >= low) {
                weak[i] = true;
            }
        }

        boolean[] out = new boolean[w * h];
        int[] stack = new int[w * h];
        int top = 0;
        for (int i = 0; i < strong.length; i++) {
            if (strong[i]) {
                out[i] = true;
                stack[top++] = i;
            }
        }
        while (top > 0) {
            int i = stack[--top];
            int x = i % w;
            int y = i / w;
            for (int dy = -1; dy <= 1; dy++) {
                int yy = y + dy;
                if (yy < 0 || yy >= h) {
                    continue;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int xx = x + dx;
                    if (xx < 0 || xx >= w) {
                        continue;
                    }
                    int n = yy * w + xx;
                    if (!out[n] && weak[n]) {
                        out[n] = true;
                        stack[top++] = n;
                    }
                }
            }
        }
        return out;
    }

    @NonNull
    private static boolean[] dilate(@NonNull boolean[] src, int w, int h) {
        boolean[] dst = new boolean[src.length];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                boolean on = false;
                for (int dy = -1; dy <= 1 && !on; dy++) {
                    int row = (y + dy) * w;
                    for (int dx = -1; dx <= 1; dx++) {
                        if (src[row + x + dx]) {
                            on = true;
                            break;
                        }
                    }
                }
                dst[y * w + x] = on;
            }
        }
        return dst;
    }

    @NonNull
    private static List<Point> flood(
            @NonNull boolean[] mask,
            @NonNull boolean[] visited,
            @NonNull int[] stack,
            int w,
            int h,
            int start
    ) {
        List<Point> points = new ArrayList<>();
        int top = 0;
        stack[top++] = start;
        visited[start] = true;
        while (top > 0) {
            int i = stack[--top];
            int x = i % w;
            int y = i / w;
            points.add(new Point(x, y));
            for (int dy = -1; dy <= 1; dy++) {
                int yy = y + dy;
                if (yy < 0 || yy >= h) {
                    continue;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int xx = x + dx;
                    if (xx < 0 || xx >= w) {
                        continue;
                    }
                    int n = yy * w + xx;
                    if (!visited[n] && mask[n]) {
                        visited[n] = true;
                        stack[top++] = n;
                    }
                }
            }
        }
        return points;
    }

    @NonNull
    private static Point[] convexHull(@NonNull List<Point> pts) {
        Point[] points = pts.toArray(new Point[0]);
        Arrays.sort(points, (a, b) -> a.x == b.x ? Integer.compare(a.y, b.y) : Integer.compare(a.x, b.x));
        List<Point> lower = new ArrayList<>();
        for (Point p : points) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), p) <= 0) {
                lower.remove(lower.size() - 1);
            }
            lower.add(p);
        }
        List<Point> upper = new ArrayList<>();
        for (int i = points.length - 1; i >= 0; i--) {
            Point p = points[i];
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), p) <= 0) {
                upper.remove(upper.size() - 1);
            }
            upper.add(p);
        }
        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return lower.toArray(new Point[0]);
    }

    private static long cross(Point o, Point a, Point b) {
        return (long) (a.x - o.x) * (b.y - o.y) - (long) (a.y - o.y) * (b.x - o.x);
    }

    @Nullable
    private static Point[] approxQuad(@NonNull Point[] hull) {
        if (hull.length == 4) {
            return hull;
        }
        if (hull.length < 4) {
            return null;
        }
        float peri = 0f;
        for (int i = 0; i < hull.length; i++) {
            Point a = hull[i];
            Point b = hull[(i + 1) % hull.length];
            peri += (float) Math.hypot(a.x - b.x, a.y - b.y);
        }
        float[] fractions = {0.02f, 0.03f, 0.04f, 0.06f, 0.08f, 0.1f, 0.12f};
        for (float frac : fractions) {
            Point[] approx = rdpClosed(hull, frac * peri);
            if (approx.length == 4) {
                return approx;
            }
        }
        return extrema4(hull);
    }

    @NonNull
    private static Point[] rdpClosed(@NonNull Point[] ring, float epsilon) {
        int n = ring.length;
        boolean[] keep = new boolean[n];
        keep[0] = true;
        int farthest = 1;
        float far = -1f;
        for (int i = 1; i < n; i++) {
            float d = (float) Math.hypot(ring[i].x - ring[0].x, ring[i].y - ring[0].y);
            if (d > far) {
                far = d;
                farthest = i;
            }
        }
        keep[farthest] = true;
        rdp(ring, 0, farthest, epsilon, keep);
        rdp(ring, farthest, n, epsilon, keep);

        List<Point> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                out.add(ring[i]);
            }
        }
        return out.toArray(new Point[0]);
    }

    private static void rdp(@NonNull Point[] ring, int start, int end, float epsilon, @NonNull boolean[] keep) {
        int n = ring.length;
        if (end <= start + 1 && !(start == 0 && end == n)) {
            return;
        }
        Point a = ring[start % n];
        Point b = ring[end % n];
        float maxDist = -1f;
        int index = -1;
        int last = end < n ? end : n;
        for (int i = start + 1; i < last; i++) {
            float d = pointToSegment(ring[i], a, b);
            if (d > maxDist) {
                maxDist = d;
                index = i;
            }
        }
        if (index >= 0 && maxDist > epsilon) {
            keep[index] = true;
            rdp(ring, start, index, epsilon, keep);
            rdp(ring, index, end, epsilon, keep);
        }
    }

    private static float pointToSegment(Point p, Point a, Point b) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float len2 = dx * dx + dy * dy;
        if (len2 < 1f) {
            return (float) Math.hypot(p.x - a.x, p.y - a.y);
        }
        float t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2;
        t = Math.max(0f, Math.min(1f, t));
        float x = a.x + t * dx;
        float y = a.y + t * dy;
        return (float) Math.hypot(p.x - x, p.y - y);
    }

    @NonNull
    private static Point[] extrema4(@NonNull Point[] hull) {
        Point tl = hull[0];
        Point tr = hull[0];
        Point br = hull[0];
        Point bl = hull[0];
        int minSum = hull[0].x + hull[0].y;
        int maxSum = minSum;
        int minDiff = hull[0].x - hull[0].y;
        int maxDiff = minDiff;
        for (Point p : hull) {
            int sum = p.x + p.y;
            int diff = p.x - p.y;
            if (sum < minSum) {
                minSum = sum;
                tl = p;
            }
            if (sum > maxSum) {
                maxSum = sum;
                br = p;
            }
            if (diff > maxDiff) {
                maxDiff = diff;
                tr = p;
            }
            if (diff < minDiff) {
                minDiff = diff;
                bl = p;
            }
        }
        return new Point[]{tl, tr, br, bl};
    }

    @NonNull
    private static List<HoughLine> localMaxima(
            @NonNull int[] acc,
            int nTheta,
            int rhoCount,
            int maxRho,
            int threshold
    ) {
        List<HoughLine> lines = new ArrayList<>();
        for (int t = 0; t < nTheta; t++) {
            for (int r = 1; r < rhoCount - 1; r++) {
                int votes = acc[t * rhoCount + r];
                if (votes < threshold) {
                    continue;
                }
                boolean peak = true;
                for (int dt = -2; dt <= 2 && peak; dt++) {
                    int tt = t + dt;
                    if (tt < 0) {
                        tt += nTheta;
                    } else if (tt >= nTheta) {
                        tt -= nTheta;
                    }
                    for (int dr = -2; dr <= 2; dr++) {
                        int rr = r + dr;
                        if (rr < 0 || rr >= rhoCount || (dt == 0 && dr == 0)) {
                            continue;
                        }
                        if (acc[tt * rhoCount + rr] > votes) {
                            peak = false;
                            break;
                        }
                    }
                }
                if (peak) {
                    lines.add(new HoughLine(t, r - maxRho, votes));
                }
            }
        }
        lines.sort((a, b) -> Integer.compare(b.votes, a.votes));
        if (lines.size() > 24) {
            return new ArrayList<>(lines.subList(0, 24));
        }
        return lines;
    }

    private static int strongest(@NonNull int[] acc) {
        int max = 1;
        for (int v : acc) {
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    @Nullable
    private static float[] intersect(@NonNull HoughLine a, @NonNull HoughLine b) {
        double aRad = Math.toRadians(a.theta);
        double bRad = Math.toRadians(b.theta);
        double cosA = Math.cos(aRad);
        double sinA = Math.sin(aRad);
        double cosB = Math.cos(bRad);
        double sinB = Math.sin(bRad);
        double det = cosA * sinB - cosB * sinA;
        if (Math.abs(det) < 1e-6) {
            return null;
        }
        float x = (float) ((a.rho * sinB - b.rho * sinA) / det);
        float y = (float) ((cosA * b.rho - cosB * a.rho) / det);
        return new float[]{x, y};
    }

    private static float xIntercept(@NonNull HoughLine line, float y) {
        double rad = Math.toRadians(line.theta);
        double cos = Math.cos(rad);
        if (Math.abs(cos) < 1e-6) {
            return 0f;
        }
        return (float) ((line.rho - y * Math.sin(rad)) / cos);
    }

    private static float yIntercept(@NonNull HoughLine line, float x) {
        double rad = Math.toRadians(line.theta);
        double sin = Math.sin(rad);
        if (Math.abs(sin) < 1e-6) {
            return 0f;
        }
        return (float) ((line.rho - x * Math.cos(rad)) / sin);
    }

    @NonNull
    private static float[] orderQuad(
            float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3
    ) {
        float[] xs = {x0, x1, x2, x3};
        float[] ys = {y0, y1, y2, y3};
        int tl = 0;
        int tr = 0;
        int br = 0;
        int bl = 0;
        float minSum = xs[0] + ys[0];
        float maxSum = minSum;
        float minDiff = xs[0] - ys[0];
        float maxDiff = minDiff;
        for (int i = 0; i < 4; i++) {
            float sum = xs[i] + ys[i];
            float diff = xs[i] - ys[i];
            if (sum < minSum) {
                minSum = sum;
                tl = i;
            }
            if (sum > maxSum) {
                maxSum = sum;
                br = i;
            }
            if (diff > maxDiff) {
                maxDiff = diff;
                tr = i;
            }
            if (diff < minDiff) {
                minDiff = diff;
                bl = i;
            }
        }
        return new float[]{xs[tl], ys[tl], xs[tr], ys[tr], xs[br], ys[br], xs[bl], ys[bl]};
    }

    private static float quadArea(@NonNull float[] q) {
        float area = 0f;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            area += q[i * 2] * q[j * 2 + 1] - q[j * 2] * q[i * 2 + 1];
        }
        return Math.abs(area) * 0.5f;
    }

    private static boolean isConvex(@NonNull float[] q) {
        Boolean positive = null;
        for (int i = 0; i < 4; i++) {
            int a = i * 2;
            int b = ((i + 1) % 4) * 2;
            int c = ((i + 2) % 4) * 2;
            float cross = (q[b] - q[a]) * (q[c + 1] - q[b + 1])
                    - (q[b + 1] - q[a + 1]) * (q[c] - q[b]);
            if (Math.abs(cross) < 1f) {
                return false;
            }
            boolean pos = cross > 0f;
            if (positive == null) {
                positive = pos;
            } else if (positive != pos) {
                return false;
            }
        }
        return true;
    }

    private static void clampQuad(@NonNull float[] q, int w, int h) {
        for (int i = 0; i < 8; i += 2) {
            q[i] = clampF(q[i], 0, w - 1);
            q[i + 1] = clampF(q[i + 1], 0, h - 1);
        }
    }

    @NonNull
    private static float[] scaleQuad(@NonNull float[] q, float scaleX, float scaleY, int srcW, int srcH) {
        float[] out = new float[8];
        for (int i = 0; i < 8; i += 2) {
            out[i] = clampF(q[i] * scaleX, 0, srcW - 1);
            out[i + 1] = clampF(q[i + 1] * scaleY, 0, srcH - 1);
        }
        if (!isConvex(out)) {
            return insetQuad(srcW, srcH, DEFAULT_INSET);
        }
        return out;
    }

    @NonNull
    private static float[] insetQuad(int width, int height, float inset) {
        float left = width * inset;
        float top = height * inset;
        float right = width * (1f - inset);
        float bottom = height * (1f - inset);
        return new float[]{left, top, right, top, right, bottom, left, bottom};
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampF(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Gradients {
        final float[] magnitude;
        final int[] direction;

        Gradients(float[] magnitude, int[] direction) {
            this.magnitude = magnitude;
            this.direction = direction;
        }
    }

    private static final class HoughLine {
        final int theta;
        final int rho;
        final int votes;

        HoughLine(int theta, int rho, int votes) {
            this.theta = theta;
            this.rho = rho;
            this.votes = votes;
        }
    }
}
