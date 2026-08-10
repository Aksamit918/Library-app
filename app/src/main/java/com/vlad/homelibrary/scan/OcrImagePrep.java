package com.vlad.homelibrary.scan;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import androidx.annotation.NonNull;

public final class OcrImagePrep {

    private OcrImagePrep() {
    }

    @NonNull
    public static Bitmap upscaleMinSide(@NonNull Bitmap source, int minSide) {
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        int side = Math.min(srcW, srcH);
        if (side >= minSide) {
            return source;
        }
        float scale = minSide / (float) Math.max(1, side);
        int width = Math.max(1, Math.round(srcW * scale));
        int height = Math.max(1, Math.round(srcH * scale));
        return Bitmap.createScaledBitmap(source, width, height, true);
    }

    @NonNull
    public static Bitmap contrastStretch(@NonNull Bitmap source) {
        return contrastStretchChannel(source, ChannelMode.MIN);
    }

    @NonNull
    public static Bitmap contrastStretchBrightInk(@NonNull Bitmap source) {
        return contrastStretchChannel(source, ChannelMode.MAX);
    }

    @NonNull
    public static Bitmap contrastStretchLuma(@NonNull Bitmap source) {
        return contrastStretchChannel(source, ChannelMode.LUMA);
    }

    private enum ChannelMode { MIN, MAX, LUMA }

    @NonNull
    private static Bitmap contrastStretchChannel(@NonNull Bitmap source, ChannelMode mode) {
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
            int v;
            switch (mode) {
                case MAX:
                    v = Math.max(r, Math.max(g, b));
                    break;
                case LUMA:
                    v = (r * 30 + g * 59 + b * 11) / 100;
                    break;
                case MIN:
                default:
                    v = Math.min(r, Math.min(g, b));
                    break;
            }
            gray[i] = v;
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
        }

        int range = Math.max(1, max - min);
        int[] out = new int[pixels.length];
        for (int i = 0; i < gray.length; i++) {
            int v = (gray[i] - min) * 255 / range;
            out[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(out, 0, width, 0, 0, width, height);
        return result;
    }

    @NonNull
    public static Bitmap binarize(@NonNull Bitmap source) {
        Bitmap stretched = contrastStretchLuma(source);
        int width = stretched.getWidth();
        int height = stretched.getHeight();
        int[] pixels = new int[width * height];
        stretched.getPixels(pixels, 0, width, 0, 0, width, height);
        if (stretched != source && !stretched.isRecycled()) {
            stretched.recycle();
        }

        long sum = 0;
        for (int color : pixels) {
            sum += color & 0xFF;
        }
        int mean = (int) (sum / Math.max(1, pixels.length));
        int threshold = Math.max(90, Math.min(180, mean));

        for (int i = 0; i < pixels.length; i++) {
            int v = pixels[i] & 0xFF;
            int out = v < threshold ? 0 : 255;
            pixels[i] = 0xFF000000 | (out << 16) | (out << 8) | out;
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
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
    public static Bitmap padForRec(@NonNull Bitmap source, int padPx) {
        if (padPx <= 0) {
            return source;
        }
        int width = source.getWidth() + padPx * 2;
        int height = source.getHeight() + padPx * 2;
        Bitmap padded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(padded);
        canvas.drawColor(0xFFFFFFFF);
        canvas.drawBitmap(source, padPx, padPx, null);
        return padded;
    }
}
