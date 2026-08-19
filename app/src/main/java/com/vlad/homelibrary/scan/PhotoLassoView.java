package com.vlad.homelibrary.scan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class PhotoLassoView extends View {

    public interface SelectionListener {
        void onRegionSelected(@Nullable Bitmap croppedRegion);

        void onSelectionTooSmall();
    }

    private final Path drawPath = new Path();
    private final Path closedPath = new Path();
    private final RectF pathBounds = new RectF();
    private final RectF imageDisplayRect = new RectF();
    private final Matrix imageDrawMatrix = new Matrix();
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Nullable
    private Bitmap photo;
    @Nullable
    private SelectionListener listener;

    private boolean drawingEnabled = false;
    private boolean isDrawing = false;
    private boolean hasClosedSelection = false;
    private float lastX;
    private float lastY;
    private final float touchTolerance;
    private final float minSelectionSizePx;

    public PhotoLassoView(Context context) {
        this(context, null);
    }

    public PhotoLassoView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PhotoLassoView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setBackgroundColor(Color.BLACK);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(3));
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);

        dimPaint.setStyle(Paint.Style.FILL);
        dimPaint.setColor(0x99000000);

        clearPaint.setStyle(Paint.Style.FILL);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        touchTolerance = dp(4);
        minSelectionSizePx = dp(48);
    }

    public void setSelectionListener(@Nullable SelectionListener listener) {
        this.listener = listener;
    }

    public void setPhoto(@Nullable Bitmap bitmap) {
        photo = bitmap;
        clearSelection();
        updateImageTransform();
        invalidate();
    }

    public void clearPhoto() {
        photo = null;
        clearSelection();
        imageDisplayRect.setEmpty();
        imageDrawMatrix.reset();
        invalidate();
    }

    public void setDrawingEnabled(boolean enabled) {
        drawingEnabled = enabled;
        if (!enabled) {
            isDrawing = false;
        }
    }

    public void clearSelection() {
        drawPath.reset();
        closedPath.reset();
        hasClosedSelection = false;
        isDrawing = false;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateImageTransform();
    }

    private void updateImageTransform() {
        imageDrawMatrix.reset();
        imageDisplayRect.setEmpty();
        if (photo == null || photo.isRecycled() || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        float viewW = getWidth();
        float viewH = getHeight();
        float bitmapW = photo.getWidth();
        float bitmapH = photo.getHeight();
        float scale = Math.min(viewW / bitmapW, viewH / bitmapH);
        float drawnW = bitmapW * scale;
        float drawnH = bitmapH * scale;
        float left = (viewW - drawnW) / 2f;
        float top = (viewH - drawnH) / 2f;
        imageDisplayRect.set(left, top, left + drawnW, top + drawnH);
        imageDrawMatrix.setScale(scale, scale);
        imageDrawMatrix.postTranslate(left, top);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (photo != null && !photo.isRecycled() && !imageDisplayRect.isEmpty()) {
            canvas.drawBitmap(photo, imageDrawMatrix, imagePaint);
        }

        if (hasClosedSelection && !closedPath.isEmpty()) {
            int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
            canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
            canvas.drawPath(closedPath, clearPaint);
            canvas.restoreToCount(save);
            canvas.drawPath(closedPath, strokePaint);
            return;
        }

        if (!drawPath.isEmpty()) {
            canvas.drawPath(drawPath, strokePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!drawingEnabled || !isEnabled() || photo == null) {
            return false;
        }

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                hasClosedSelection = false;
                closedPath.reset();
                drawPath.reset();
                drawPath.moveTo(x, y);
                lastX = x;
                lastY = y;
                isDrawing = true;
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!isDrawing) {
                    return false;
                }
                float dx = Math.abs(x - lastX);
                float dy = Math.abs(y - lastY);
                if (dx >= touchTolerance || dy >= touchTolerance) {
                    drawPath.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f);
                    lastX = x;
                    lastY = y;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (!isDrawing) {
                    return false;
                }
                isDrawing = false;
                clearSelection();
                return true;

            case MotionEvent.ACTION_UP:
                if (!isDrawing) {
                    return false;
                }
                isDrawing = false;
                drawPath.lineTo(x, y);
                drawPath.close();
                closedPath.set(drawPath);
                closedPath.computeBounds(pathBounds, true);

                if (pathBounds.width() < minSelectionSizePx || pathBounds.height() < minSelectionSizePx) {
                    clearSelection();
                    if (listener != null) {
                        listener.onSelectionTooSmall();
                    }
                    return true;
                }

                hasClosedSelection = true;
                invalidate();

                if (listener != null) {
                    listener.onRegionSelected(cropClosedSelection());
                }
                return true;

            default:
                return false;
        }
    }

    @Nullable
    private Bitmap cropClosedSelection() {
        if (photo == null || photo.isRecycled() || imageDisplayRect.isEmpty() || closedPath.isEmpty()) {
            return null;
        }

        Matrix viewToBitmap = new Matrix();
        if (!imageDrawMatrix.invert(viewToBitmap)) {
            return null;
        }

        Path bitmapPath = new Path(closedPath);
        bitmapPath.transform(viewToBitmap);

        RectF bitmapBounds = new RectF();
        bitmapPath.computeBounds(bitmapBounds, true);
        if (bitmapBounds.isEmpty()) {
            return null;
        }
        if (!bitmapBounds.intersect(0, 0, photo.getWidth(), photo.getHeight())) {
            return null;
        }
        if (bitmapBounds.width() < 8 || bitmapBounds.height() < 8) {
            return null;
        }

        int left = Math.max(0, (int) Math.floor(bitmapBounds.left));
        int top = Math.max(0, (int) Math.floor(bitmapBounds.top));
        int right = Math.min(photo.getWidth(), (int) Math.ceil(bitmapBounds.right));
        int bottom = Math.min(photo.getHeight(), (int) Math.ceil(bitmapBounds.bottom));
        int width = right - left;
        int height = bottom - top;
        if (width < 8 || height < 8) {
            return null;
        }

        Path maskPath = new Path(bitmapPath);
        maskPath.setFillType(Path.FillType.WINDING);
        maskPath.offset(-left, -top);

        Bitmap cropped = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas photoCanvas = new Canvas(cropped);
        photoCanvas.drawBitmap(photo, -left, -top, imagePaint);

        Bitmap mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas maskCanvas = new Canvas(mask);
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.WHITE);
        maskCanvas.drawPath(maskPath, fillPaint);
        boolean maskUsable = maskCoverage(mask) >= 0.18f;
        if (maskUsable) {
            Paint dstIn = new Paint();
            dstIn.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            photoCanvas.drawBitmap(mask, 0, 0, dstIn);
            photoCanvas.drawColor(Color.WHITE, PorterDuff.Mode.DST_OVER);
        }
        mask.recycle();

        int pad = 16;
        Bitmap padded = Bitmap.createBitmap(width + pad * 2, height + pad * 2, Bitmap.Config.ARGB_8888);
        Canvas out = new Canvas(padded);
        out.drawColor(Color.WHITE);
        out.drawBitmap(cropped, pad, pad, imagePaint);
        cropped.recycle();
        return padded;
    }

    private static float maskCoverage(@NonNull Bitmap mask) {
        int width = mask.getWidth();
        int height = mask.getHeight();
        int stepX = Math.max(1, width / 80);
        int stepY = Math.max(1, height / 80);
        int[] row = new int[width];
        int opaque = 0;
        int total = 0;
        for (int y = 0; y < height; y += stepY) {
            mask.getPixels(row, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x += stepX) {
                total++;
                if ((row[x] >>> 24) > 120) {
                    opaque++;
                }
            }
        }
        return total == 0 ? 0f : opaque / (float) total;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
