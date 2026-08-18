package com.vlad.homelibrary.scan;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class CoverPerspectiveCrop {

    private static final int MAX_OUT_SIDE = 1600;
    private static final int MIN_OUT_SIDE = 64;

    private CoverPerspectiveCrop() {
    }

    @Nullable
    public static Bitmap crop(@NonNull Bitmap source, @NonNull float[] quad) {
        if (source.isRecycled() || quad.length < 8) {
            return null;
        }

        float tlx = quad[0];
        float tly = quad[1];
        float trx = quad[2];
        float tryY = quad[3];
        float brx = quad[4];
        float bry = quad[5];
        float blx = quad[6];
        float bly = quad[7];

        float widthTop = distance(tlx, tly, trx, tryY);
        float widthBottom = distance(blx, bly, brx, bry);
        float heightLeft = distance(tlx, tly, blx, bly);
        float heightRight = distance(trx, tryY, brx, bry);

        int outW = Math.round(Math.max(widthTop, widthBottom));
        int outH = Math.round(Math.max(heightLeft, heightRight));
        if (outW < MIN_OUT_SIDE || outH < MIN_OUT_SIDE) {
            return null;
        }

        int maxSide = Math.max(outW, outH);
        if (maxSide > MAX_OUT_SIDE) {
            float scale = MAX_OUT_SIDE / (float) maxSide;
            outW = Math.max(MIN_OUT_SIDE, Math.round(outW * scale));
            outH = Math.max(MIN_OUT_SIDE, Math.round(outH * scale));
        }

        Matrix matrix = new Matrix();
        boolean mapped = matrix.setPolyToPoly(
                new float[]{tlx, tly, trx, tryY, brx, bry, blx, bly},
                0,
                new float[]{0, 0, outW, 0, outW, outH, 0, outH},
                0,
                4
        );
        if (!mapped) {
            return null;
        }

        Bitmap output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, matrix, paint);
        return output;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.hypot(dx, dy);
    }
}
