package com.vlad.homelibrary.scan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;

public final class CoverImageLoader {

    private static final int MAX_SIDE = 2048;

    private CoverImageLoader() {
    }

    @Nullable
    public static Bitmap load(@NonNull Context context, @NonNull Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    return null;
                }
                BitmapFactory.decodeStream(in, null, bounds);
            }

            int width = bounds.outWidth;
            int height = bounds.outHeight;
            if (width <= 0 || height <= 0) {
                return null;
            }

            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
            decode.inSampleSize = sampleSize(width, height, MAX_SIDE);

            Bitmap decoded;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    return null;
                }
                decoded = BitmapFactory.decodeStream(in, null, decode);
            }
            if (decoded == null) {
                return null;
            }

            Bitmap oriented = applyRotation(decoded, readRotation(context, uri));
            return scaleDown(oriented, MAX_SIDE);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static int sampleSize(int width, int height, int maxSide) {
        int side = Math.max(width, height);
        int sample = 1;
        while (side / (sample * 2) >= maxSide) {
            sample *= 2;
        }
        return sample;
    }

    private static int readRotation(@NonNull Context context, @NonNull Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return 0;
            }
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    @NonNull
    private static Bitmap applyRotation(@NonNull Bitmap bitmap, int rotation) {
        if (rotation == 0) {
            return ensureArgb8888(bitmap);
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return ensureArgb8888(rotated);
    }

    @NonNull
    private static Bitmap scaleDown(@NonNull Bitmap bitmap, int maxSide) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int side = Math.max(width, height);
        if (side <= maxSide) {
            return ensureArgb8888(bitmap);
        }
        float scale = maxSide / (float) side;
        int outW = Math.max(1, Math.round(width * scale));
        int outH = Math.max(1, Math.round(height * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, outW, outH, true);
        if (scaled != bitmap && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return ensureArgb8888(scaled);
    }

    @NonNull
    private static Bitmap ensureArgb8888(@NonNull Bitmap bitmap) {
        if (bitmap.getConfig() == Bitmap.Config.ARGB_8888) {
            return bitmap;
        }
        Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        if (copy == null) {
            return bitmap;
        }
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return copy;
    }
}
