package com.vlad.homelibrary.scan;

import android.graphics.Matrix;
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
}
