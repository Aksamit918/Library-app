package com.vlad.homelibrary.scan;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class OcrImagePrep {

    private OcrImagePrep() {
    }

    public static final class TextBand {
        public final Bitmap bitmap;
        public final int top;

        TextBand(Bitmap bitmap, int top) {
            this.bitmap = bitmap;
            this.top = top;
        }
    }

    @NonNull
    public static Bitmap fitForOcr(@NonNull Bitmap source, int minSide, int maxSide) {
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        int sideMin = Math.min(srcW, srcH);
        int sideMax = Math.max(srcW, srcH);
        float scale = 1f;
        if (sideMin < minSide) {
            scale = minSide / (float) Math.max(1, sideMin);
        }
        if (sideMax * scale > maxSide) {
            scale = maxSide / (float) Math.max(1, sideMax);
        }
        if (Math.abs(scale - 1f) < 0.01f) {
            return source;
        }
        int width = Math.max(1, Math.round(srcW * scale));
        int height = Math.max(1, Math.round(srcH * scale));
        return Bitmap.createScaledBitmap(source, width, height, true);
    }

    @NonNull
    public static Bitmap toGray(@NonNull Bitmap source) {
        Bitmap gray = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(gray);
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(source, 0, 0, paint);
        return gray;
    }

    @NonNull
    public static Bitmap invert(@NonNull Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int a = color & 0xFF000000;
            int r = 255 - ((color >> 16) & 0xFF);
            int g = 255 - ((color >> 8) & 0xFF);
            int b = 255 - (color & 0xFF);
            pixels[i] = a | (r << 16) | (g << 8) | b;
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    @NonNull
    public static Bitmap minChannelGray(@NonNull Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        int min = 255;
        int max = 0;
        int[] gray = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int v = Math.min((color >> 16) & 0xFF, Math.min((color >> 8) & 0xFF, color & 0xFF));
            gray[i] = v;
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
        }
        return grayFromStretched(gray, width, height, min, max);
    }

    @NonNull
    public static Bitmap chromaAsDarkGray(@NonNull Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        int min = 255;
        int max = 0;
        int[] gray = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            int chroma = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
            int v = 255 - chroma;
            gray[i] = v;
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
        }
        return grayFromStretched(gray, width, height, min, max);
    }

    @NonNull
    private static Bitmap grayFromStretched(int[] gray, int width, int height, int min, int max) {
        int range = Math.max(1, max - min);
        int[] out = new int[gray.length];
        for (int i = 0; i < gray.length; i++) {
            int v = (gray[i] - min) * 255 / range;
            out[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(out, 0, width, 0, 0, width, height);
        return result;
    }

    public static boolean isMostlyDark(@NonNull Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int insetX = Math.max(0, width / 12);
        int insetY = Math.max(0, height / 12);
        int stepX = Math.max(1, width / 48);
        int stepY = Math.max(1, height / 48);
        int dark = 0;
        int bright = 0;
        long sum = 0;
        int count = 0;
        int[] pixels = new int[width];
        for (int y = insetY; y < height - insetY; y += stepY) {
            source.getPixels(pixels, 0, width, 0, y, width, 1);
            for (int x = insetX; x < width - insetX; x += stepX) {
                int color = pixels[x];
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                int luma = (r * 30 + g * 59 + b * 11) / 100;
                sum += luma;
                count++;
                if (luma <= 80) {
                    dark++;
                } else if (luma >= 150) {
                    bright++;
                }
            }
        }
        if (count == 0) {
            return false;
        }
        return dark * 5 >= count * 2 || (dark > bright && sum / count < 120);
    }

    @NonNull
    public static Bitmap ensureDarkTextOnLight(@NonNull Bitmap source) {
        if (!isMostlyDark(source)) {
            return source;
        }
        return invertMaxChannel(source);
    }

    @NonNull
    public static Bitmap enhanceForOcr(@NonNull Bitmap source) {
        boolean darkBackground = isMostlyDark(source);
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        int min = 255;
        int max = 0;
        int[] gray = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            int v = darkBackground
                    ? 255 - Math.max(r, Math.max(g, b))
                    : (r * 30 + g * 59 + b * 11) / 100;
            gray[i] = v;
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
        }
        int p1 = histogramPercentile(gray, 0.02f);
        int p99 = histogramPercentile(gray, 0.98f);
        if (p99 - p1 < 24) {
            p1 = min;
            p99 = max;
        }
        return grayFromStretched(gray, width, height, p1, p99);
    }

    @NonNull
    private static Bitmap invertMaxChannel(@NonNull Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        int min = 255;
        int max = 0;
        int[] gray = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            int v = 255 - Math.max(r, Math.max(g, b));
            gray[i] = v;
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
        }
        return grayFromStretched(gray, width, height, min, max);
    }

    private static int histogramPercentile(@NonNull int[] values, float fraction) {
        int[] hist = new int[256];
        for (int value : values) {
            hist[Math.max(0, Math.min(255, value))]++;
        }
        int target = Math.max(1, Math.round(values.length * fraction));
        int acc = 0;
        for (int i = 0; i < hist.length; i++) {
            acc += hist[i];
            if (acc >= target) {
                return i;
            }
        }
        return 255;
    }

    public static boolean looksLikeNumberBand(int bandWidth, int bandHeight, int imageWidth, int imageHeight) {
        if (bandWidth < 12 || bandHeight < 8) {
            return false;
        }
        float aspect = bandWidth / (float) Math.max(1, bandHeight);
        return aspect >= 1.15f
                && aspect <= 5.8f
                && bandWidth * 100 <= imageWidth * 46
                && bandHeight * 100 <= imageHeight * 38;
    }

    private static boolean isInkPixel(int r, int g, int b, boolean darkBackground) {
        int maxc = Math.max(r, Math.max(g, b));
        int minc = Math.min(r, Math.min(g, b));
        int luma = (r * 30 + g * 59 + b * 11) / 100;
        if (darkBackground) {
            return luma >= 155 || (maxc >= 165 && maxc - minc >= 28);
        }
        return luma <= 92;
    }

    @NonNull
    public static List<TextBand> detectTextBands(@NonNull Bitmap source) {
        List<TextBand> bands = new ArrayList<>();
        int width = source.getWidth();
        int height = source.getHeight();
        if (width < 16 || height < 16) {
            return bands;
        }

        boolean darkBackground = isMostlyDark(source);
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        int[] projection = new int[height];
        for (int y = 0; y < height; y++) {
            int ink = 0;
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int color = pixels[row + x];
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                if (isInkPixel(r, g, b, darkBackground)) {
                    ink++;
                }
            }
            projection[y] = ink;
        }

        int[] smooth = new int[height];
        for (int y = 0; y < height; y++) {
            int sum = projection[y];
            int n = 1;
            if (y > 0) {
                sum += projection[y - 1];
                n++;
            }
            if (y + 1 < height) {
                sum += projection[y + 1];
                n++;
            }
            smooth[y] = sum / n;
        }

        int maxInk = 1;
        for (int value : smooth) {
            if (value > maxInk) {
                maxInk = value;
            }
        }
        int threshold = Math.max(Math.max(4, width / 60), maxInk / 14);

        boolean inBand = false;
        int start = 0;
        List<int[]> ranges = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            boolean ink = smooth[y] >= threshold;
            if (ink && !inBand) {
                inBand = true;
                start = y;
            } else if (!ink && inBand) {
                inBand = false;
                ranges.add(new int[]{start, y});
            }
        }
        if (inBand) {
            ranges.add(new int[]{start, height});
        }

        List<int[]> merged = new ArrayList<>();
        for (int[] range : ranges) {
            if (merged.isEmpty()) {
                merged.add(range);
                continue;
            }
            int[] prev = merged.get(merged.size() - 1);
            int gap = range[0] - prev[1];
            int prevH = prev[1] - prev[0];
            if (gap <= Math.max(3, prevH / 5)) {
                prev[1] = range[1];
            } else {
                merged.add(range);
            }
        }

        int minH = Math.max(10, height / 80);
        int maxH = Math.max(minH + 1, Math.round(height * 0.42f));
        for (int[] range : merged) {
            int bandH = range[1] - range[0];
            if (bandH < minH || bandH > maxH) {
                continue;
            }
            int pad = Math.max(3, bandH / 10);
            int top = Math.max(0, range[0] - pad);
            int bottom = Math.min(height, range[1] + pad);
            Rect crop = cropInkBounds(pixels, width, height, top, bottom, darkBackground);
            if (crop.width() < 12 || crop.height() < 8) {
                continue;
            }
            if (crop.height() > crop.width() * 0.55f && crop.height() > Math.max(40, height / 8)) {
                continue;
            }
            if (isSideDecoration(crop, width, height) || isSparseBand(pixels, width, crop, darkBackground)) {
                continue;
            }
            List<Rect> columns = splitInkColumns(
                    pixels, width, height, crop, darkBackground);
            for (Rect column : columns) {
                if (column.width() < 12 || column.height() < 8) {
                    continue;
                }
                if (isSideDecoration(column, width, height)) {
                    continue;
                }
                Bitmap bitmap = Bitmap.createBitmap(
                        source, column.left, column.top, column.width(), column.height());
                bands.add(new TextBand(bitmap, column.top));
                if (bands.size() >= 12) {
                    return bands;
                }
            }
        }
        return bands;
    }

    private static boolean isSideDecoration(@NonNull Rect crop, int imageWidth, int imageHeight) {
        if (crop.width() * 100 >= imageWidth * 32) {
            return false;
        }
        boolean onSide = crop.left * 100 <= imageWidth * 12
                || crop.right * 100 >= imageWidth * 88;
        if (!onSide) {
            return false;
        }
        float aspect = crop.width() / (float) Math.max(1, crop.height());
        return aspect < 1.7f || crop.height() * 100 >= imageHeight * 22;
    }

    private static boolean isSparseBand(int[] pixels, int width, @NonNull Rect crop, boolean darkBackground) {
        int ink = 0;
        int samples = 0;
        int stepX = Math.max(1, crop.width() / 64);
        int stepY = Math.max(1, crop.height() / 24);
        for (int y = crop.top; y < crop.bottom; y += stepY) {
            int row = y * width;
            for (int x = crop.left; x < crop.right; x += stepX) {
                int color = pixels[row + x];
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                samples++;
                if (isInkPixel(r, g, b, darkBackground)) {
                    ink++;
                }
            }
        }
        return samples > 0 && ink * 20 < samples;
    }

    @NonNull
    private static Rect cropInkBounds(int[] pixels, int width, int height, int top, int bottom,
                                      boolean darkBackground) {
        int minX = width;
        int maxX = 0;
        for (int y = top; y < bottom; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int color = pixels[row + x];
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                if (isInkPixel(r, g, b, darkBackground)) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                }
            }
        }
        if (maxX < minX) {
            return new Rect(0, top, width, bottom);
        }
        int pad = Math.max(4, (maxX - minX) / 40);
        int left = Math.max(0, minX - pad);
        int right = Math.min(width, maxX + 1 + pad);
        return new Rect(left, top, right, bottom);
    }

    @NonNull
    private static List<Rect> splitInkColumns(int[] pixels,
                                              int width,
                                              int height,
                                              @NonNull Rect crop,
                                              boolean darkBackground) {
        int[] projection = new int[crop.width()];
        int maxInk = 1;
        for (int y = crop.top; y < crop.bottom && y < height; y++) {
            int row = y * width;
            for (int x = crop.left; x < crop.right && x < width; x++) {
                int color = pixels[row + x];
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                if (isInkPixel(r, g, b, darkBackground)) {
                    int idx = x - crop.left;
                    projection[idx]++;
                    if (projection[idx] > maxInk) {
                        maxInk = projection[idx];
                    }
                }
            }
        }
        int threshold = Math.max(2, Math.min(crop.height() / 10, maxInk / 6));
        int minGap = Math.max(10, crop.height() / 4);
        List<int[]> ranges = new ArrayList<>();
        boolean inCol = false;
        int start = 0;
        for (int x = 0; x < projection.length; x++) {
            boolean ink = projection[x] >= threshold;
            if (ink && !inCol) {
                inCol = true;
                start = x;
            } else if (!ink && inCol) {
                inCol = false;
                ranges.add(new int[]{start, x});
            }
        }
        if (inCol) {
            ranges.add(new int[]{start, projection.length});
        }

        List<int[]> merged = new ArrayList<>();
        for (int[] range : ranges) {
            if (merged.isEmpty()) {
                merged.add(range);
                continue;
            }
            int[] prev = merged.get(merged.size() - 1);
            int gap = range[0] - prev[1];
            if (gap < minGap) {
                prev[1] = range[1];
            } else {
                merged.add(range);
            }
        }

        List<Rect> columns = new ArrayList<>();
        if (merged.size() < 2) {
            columns.add(crop);
            return columns;
        }
        int pad = Math.max(3, crop.height() / 12);
        for (int[] range : merged) {
            if (range[1] - range[0] < 12) {
                continue;
            }
            int left = Math.max(crop.left, crop.left + range[0] - pad);
            int right = Math.min(crop.right, crop.left + range[1] + pad);
            columns.add(new Rect(left, crop.top, right, crop.bottom));
        }
        if (columns.size() < 2) {
            columns.clear();
            columns.add(crop);
        }
        return columns;
    }

    public static void recycleQuietly(Bitmap bitmap, Bitmap original) {
        if (bitmap != null && bitmap != original && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
