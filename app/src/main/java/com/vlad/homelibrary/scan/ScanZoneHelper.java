package com.vlad.homelibrary.scan;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;
import androidx.camera.view.transform.CoordinateTransform;
import androidx.camera.view.transform.ImageProxyTransformFactory;
import androidx.camera.view.transform.OutputTransform;

public final class ScanZoneHelper {

    private ScanZoneHelper() {
    }

    @Nullable
    public static Bitmap cropToScanZone(@Nullable Bitmap fullBitmap,
                                       PreviewView previewView,
                                       View scanFrame) {
        if (fullBitmap == null || previewView.getWidth() == 0 || previewView.getHeight() == 0) {
            return fullBitmap;
        }

        RectF zone = getZoneInViewCoordinates(previewView, scanFrame);
        return cropToRect(fullBitmap, previewView, zone);
    }

    /**
     * Crops a still photo in an ImageView to a freehand path in view coordinates,
     * accounting for fitCenter letterboxing.
     */
    @Nullable
    public static Bitmap cropToFreehandPath(@Nullable Bitmap fullBitmap,
                                           android.widget.ImageView imageView,
                                           Path pathInView,
                                           RectF boundsInView) {
        if (fullBitmap == null
                || imageView.getWidth() == 0
                || imageView.getHeight() == 0
                || pathInView == null
                || boundsInView == null
                || boundsInView.isEmpty()) {
            return null;
        }

        RectF displayRect = getImageDisplayRect(imageView, fullBitmap.getWidth(), fullBitmap.getHeight());
        if (displayRect == null || displayRect.isEmpty()) {
            return null;
        }

        float scaleX = fullBitmap.getWidth() / displayRect.width();
        float scaleY = fullBitmap.getHeight() / displayRect.height();

        Matrix toBitmap = new Matrix();
        toBitmap.setTranslate(-displayRect.left, -displayRect.top);
        toBitmap.postScale(scaleX, scaleY);

        Path scaledPath = new Path(pathInView);
        scaledPath.transform(toBitmap);

        RectF bitmapBounds = new RectF();
        scaledPath.computeBounds(bitmapBounds, true);
        if (bitmapBounds.isEmpty()) {
            return null;
        }

        int left = clamp(Math.round(bitmapBounds.left), 0, fullBitmap.getWidth() - 1);
        int top = clamp(Math.round(bitmapBounds.top), 0, fullBitmap.getHeight() - 1);
        int right = clamp(Math.round(bitmapBounds.right), left + 1, fullBitmap.getWidth());
        int bottom = clamp(Math.round(bitmapBounds.bottom), top + 1, fullBitmap.getHeight());
        int width = right - left;
        int height = bottom - top;

        Matrix translate = new Matrix();
        translate.setTranslate(-left, -top);
        scaledPath.transform(translate);

        Bitmap cropped = Bitmap.createBitmap(fullBitmap, left, top, width, height);
        Bitmap masked = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(masked);
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        int save = canvas.saveLayer(0, 0, width, height, null);
        canvas.drawPath(scaledPath, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(cropped, 0, 0, paint);
        paint.setXfermode(null);
        canvas.restoreToCount(save);

        if (cropped != fullBitmap && !cropped.isRecycled()) {
            cropped.recycle();
        }
        return masked;
    }

    @Nullable
    private static RectF getImageDisplayRect(android.widget.ImageView imageView, int bitmapWidth, int bitmapHeight) {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) {
            return null;
        }
        float viewWidth = imageView.getWidth();
        float viewHeight = imageView.getHeight();
        float scale = Math.min(viewWidth / bitmapWidth, viewHeight / bitmapHeight);
        float drawnWidth = bitmapWidth * scale;
        float drawnHeight = bitmapHeight * scale;
        float left = (viewWidth - drawnWidth) / 2f;
        float top = (viewHeight - drawnHeight) / 2f;
        return new RectF(left, top, left + drawnWidth, top + drawnHeight);
    }

    @Nullable
    private static Bitmap cropToRect(Bitmap fullBitmap, PreviewView previewView, RectF zone) {
        float scaleX = (float) fullBitmap.getWidth() / previewView.getWidth();
        float scaleY = (float) fullBitmap.getHeight() / previewView.getHeight();

        int left = Math.round(zone.left * scaleX);
        int top = Math.round(zone.top * scaleY);
        int width = Math.round(zone.width() * scaleX);
        int height = Math.round(zone.height() * scaleY);

        left = clamp(left, 0, fullBitmap.getWidth() - 1);
        top = clamp(top, 0, fullBitmap.getHeight() - 1);
        width = clamp(width, 1, fullBitmap.getWidth() - left);
        height = clamp(height, 1, fullBitmap.getHeight() - top);

        return Bitmap.createBitmap(fullBitmap, left, top, width, height);
    }

    public static boolean isBarcodeInsideScanZone(Rect barcodeBounds,
                                                 ImageProxy imageProxy,
                                                 PreviewView previewView,
                                                 View scanFrame) {
        if (barcodeBounds == null || previewView.getWidth() == 0 || scanFrame.getWidth() == 0) {
            return false;
        }

        RectF mapped = mapImageRectToPreviewView(barcodeBounds, imageProxy, previewView);
        if (mapped == null) {
            return isBarcodeInsideScanZoneFallback(barcodeBounds, imageProxy, previewView, scanFrame);
        }

        RectF zone = getZoneInViewCoordinates(previewView, scanFrame);
        return zone.contains(mapped.centerX(), mapped.centerY());
    }

    @Nullable
    private static RectF mapImageRectToPreviewView(Rect barcodeBounds,
                                                   ImageProxy imageProxy,
                                                   PreviewView previewView) {
        try {
            ImageProxyTransformFactory factory = new ImageProxyTransformFactory();
            factory.setUsingRotationDegrees(true);
            OutputTransform source = factory.getOutputTransform(imageProxy);
            OutputTransform target = previewView.getOutputTransform();
            if (source == null || target == null) {
                return null;
            }

            Matrix matrix = new Matrix();
            new CoordinateTransform(source, target).transform(matrix);
            RectF mapped = new RectF(barcodeBounds);
            matrix.mapRect(mapped);
            return mapped;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBarcodeInsideScanZoneFallback(Rect barcodeBounds,
                                                          ImageProxy imageProxy,
                                                          PreviewView previewView,
                                                          View scanFrame) {
        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        int imageWidth = imageProxy.getWidth();
        int imageHeight = imageProxy.getHeight();
        if (rotation == 90 || rotation == 270) {
            int tmp = imageWidth;
            imageWidth = imageHeight;
            imageHeight = tmp;
        }

        float scaleX = (float) previewView.getWidth() / imageWidth;
        float scaleY = (float) previewView.getHeight() / imageHeight;
        float scale = Math.max(scaleX, scaleY);
        float offsetX = (previewView.getWidth() - imageWidth * scale) / 2f;
        float offsetY = (previewView.getHeight() - imageHeight * scale) / 2f;

        float centerX = barcodeBounds.exactCenterX() * scale + offsetX;
        float centerY = barcodeBounds.exactCenterY() * scale + offsetY;
        RectF zone = getZoneInViewCoordinates(previewView, scanFrame);
        return zone.contains(centerX, centerY);
    }

    private static RectF getZoneInViewCoordinates(View previewView, View scanFrame) {
        int[] previewLoc = new int[2];
        int[] frameLoc = new int[2];
        previewView.getLocationOnScreen(previewLoc);
        scanFrame.getLocationOnScreen(frameLoc);

        float left = frameLoc[0] - previewLoc[0];
        float top = frameLoc[1] - previewLoc[1];
        return new RectF(left, top, left + scanFrame.getWidth(), top + scanFrame.getHeight());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
