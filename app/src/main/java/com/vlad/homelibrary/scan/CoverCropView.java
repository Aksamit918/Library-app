package com.vlad.homelibrary.scan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CoverCropView extends View {

    private static final int TL = 0;
    private static final int TR = 1;
    private static final int BR = 2;
    private static final int BL = 3;

    private final RectF imageDisplayRect = new RectF();
    private final Matrix imageDrawMatrix = new Matrix();
    private final PointF[] corners = {
            new PointF(), new PointF(), new PointF(), new PointF()
    };
    private final PointF[] viewCorners = {
            new PointF(), new PointF(), new PointF(), new PointF()
    };
    private final float[] scratchQuad = new float[8];
    private final Path quadPath = new Path();
    private final Path dimPath = new Path();
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgeHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Nullable
    private Bitmap photo;
    private boolean adjustEnabled = false;
    private int dragCorner = -1;
    private int dragEdge = -1;
    private float lastX;
    private float lastY;
    private final float handleRadius;
    private final float edgeHitWidth;
    private final float minSidePx;
    private final float cornerHandleRadius;
    private final float edgeHandleRadius;

    public CoverCropView(Context context) {
        this(context, null);
    }

    public CoverCropView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CoverCropView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setBackgroundColor(Color.BLACK);

        dimPaint.setStyle(Paint.Style.FILL);
        dimPaint.setColor(0x99000000);

        DashPathEffect dashes = new DashPathEffect(new float[]{dp(10), dp(7)}, 0);

        lineShadowPaint.setStyle(Paint.Style.STROKE);
        lineShadowPaint.setStrokeWidth(dp(5));
        lineShadowPaint.setColor(0xCC000000);
        lineShadowPaint.setStrokeJoin(Paint.Join.ROUND);
        lineShadowPaint.setPathEffect(dashes);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2.5f));
        linePaint.setColor(0xFFE8EAFF);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setPathEffect(dashes);

        handleFillPaint.setStyle(Paint.Style.FILL);
        handleFillPaint.setColor(Color.WHITE);

        handleStrokePaint.setStyle(Paint.Style.STROKE);
        handleStrokePaint.setStrokeWidth(dp(2));
        handleStrokePaint.setColor(0xFF6366F1);

        edgeHandlePaint.setStyle(Paint.Style.FILL);
        edgeHandlePaint.setColor(0xFF6366F1);

        handleRadius = dp(28);
        edgeHitWidth = dp(26);
        minSidePx = dp(56);
        cornerHandleRadius = dp(11);
        edgeHandleRadius = dp(7);
    }

    public void setPhoto(@Nullable Bitmap bitmap) {
        photo = bitmap;
        if (bitmap != null && !bitmap.isRecycled()) {
            setBitmapQuad(BookCoverDetector.defaultQuad(bitmap.getWidth(), bitmap.getHeight()));
        }
        updateImageTransform();
        invalidate();
    }

    public void clearPhoto() {
        photo = null;
        imageDisplayRect.setEmpty();
        imageDrawMatrix.reset();
        dragCorner = -1;
        dragEdge = -1;
        invalidate();
    }

    public void setAdjustEnabled(boolean enabled) {
        adjustEnabled = enabled;
        if (!enabled) {
            dragCorner = -1;
            dragEdge = -1;
        }
    }

    public void setBitmapQuad(@NonNull float[] quad) {
        if (quad.length < 8) {
            return;
        }
        corners[TL].set(quad[0], quad[1]);
        corners[TR].set(quad[2], quad[3]);
        corners[BR].set(quad[4], quad[5]);
        corners[BL].set(quad[6], quad[7]);
        invalidate();
    }

    @NonNull
    public float[] getBitmapQuad() {
        scratchQuad[0] = corners[TL].x;
        scratchQuad[1] = corners[TL].y;
        scratchQuad[2] = corners[TR].x;
        scratchQuad[3] = corners[TR].y;
        scratchQuad[4] = corners[BR].x;
        scratchQuad[5] = corners[BR].y;
        scratchQuad[6] = corners[BL].x;
        scratchQuad[7] = corners[BL].y;
        return scratchQuad;
    }

    @Nullable
    public Bitmap cropSelected() {
        if (photo == null || photo.isRecycled()) {
            return null;
        }
        return CoverPerspectiveCrop.crop(photo, getBitmapQuad());
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
        if (photo == null || photo.isRecycled() || imageDisplayRect.isEmpty()) {
            return;
        }
        canvas.drawBitmap(photo, imageDrawMatrix, imagePaint);
        updateViewCorners();

        quadPath.reset();
        quadPath.moveTo(viewCorners[TL].x, viewCorners[TL].y);
        quadPath.lineTo(viewCorners[TR].x, viewCorners[TR].y);
        quadPath.lineTo(viewCorners[BR].x, viewCorners[BR].y);
        quadPath.lineTo(viewCorners[BL].x, viewCorners[BL].y);
        quadPath.close();

        dimPath.reset();
        dimPath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        dimPath.addPath(quadPath);
        dimPath.setFillType(Path.FillType.EVEN_ODD);
        canvas.drawPath(dimPath, dimPaint);
        canvas.drawPath(quadPath, lineShadowPaint);
        canvas.drawPath(quadPath, linePaint);

        for (int i = 0; i < 4; i++) {
            PointF a = viewCorners[i];
            PointF b = viewCorners[(i + 1) % 4];
            canvas.drawCircle((a.x + b.x) / 2f, (a.y + b.y) / 2f, edgeHandleRadius, edgeHandlePaint);
        }
        for (PointF corner : viewCorners) {
            canvas.drawCircle(corner.x, corner.y, cornerHandleRadius, handleFillPaint);
            canvas.drawCircle(corner.x, corner.y, cornerHandleRadius, handleStrokePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!adjustEnabled || !isEnabled() || photo == null || photo.isRecycled()) {
            return false;
        }

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                updateViewCorners();
                dragCorner = nearestCorner(x, y);
                dragEdge = dragCorner >= 0 ? -1 : nearestEdge(x, y);
                if (dragCorner < 0 && dragEdge < 0) {
                    return false;
                }
                lastX = x;
                lastY = y;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (dragCorner < 0 && dragEdge < 0) {
                    return false;
                }
                float dx = x - lastX;
                float dy = y - lastY;
                lastX = x;
                lastY = y;
                applyDrag(dx, dy);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragCorner = -1;
                dragEdge = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;

            default:
                return false;
        }
    }

    private void applyDrag(float viewDx, float viewDy) {
        if (imageDisplayRect.isEmpty() || photo == null) {
            return;
        }
        float scale = imageDisplayRect.width() / photo.getWidth();
        if (scale <= 0f) {
            return;
        }
        float dx = viewDx / scale;
        float dy = viewDy / scale;

        float[] before = copyCorners();
        if (dragCorner >= 0) {
            corners[dragCorner].offset(dx, dy);
            clampCorner(corners[dragCorner]);
        } else if (dragEdge >= 0) {
            int a = dragEdge;
            int b = (dragEdge + 1) % 4;
            corners[a].offset(dx, dy);
            corners[b].offset(dx, dy);
            clampCorner(corners[a]);
            clampCorner(corners[b]);
        }

        if (!isValidQuad()) {
            restoreCorners(before);
            return;
        }
        invalidate();
    }

    private boolean isValidQuad() {
        float[] q = getBitmapQuad();
        Boolean positive = null;
        for (int i = 0; i < 4; i++) {
            int a = i * 2;
            int b = ((i + 1) % 4) * 2;
            int c = ((i + 2) % 4) * 2;
            float cross = (q[b] - q[a]) * (q[c + 1] - q[b + 1])
                    - (q[b + 1] - q[a + 1]) * (q[c] - q[b]);
            if (Math.abs(cross) < 8f) {
                return false;
            }
            boolean pos = cross > 0f;
            if (positive == null) {
                positive = pos;
            } else if (positive != pos) {
                return false;
            }
        }

        float minSideBitmap = minSidePx;
        if (!imageDisplayRect.isEmpty() && photo != null) {
            float scale = imageDisplayRect.width() / photo.getWidth();
            if (scale > 0f) {
                minSideBitmap = minSidePx / scale;
            }
        }
        for (int i = 0; i < 4; i++) {
            PointF a = corners[i];
            PointF b = corners[(i + 1) % 4];
            if (Math.hypot(a.x - b.x, a.y - b.y) < minSideBitmap) {
                return false;
            }
        }
        return true;
    }

    private void clampCorner(@NonNull PointF corner) {
        if (photo == null) {
            return;
        }
        corner.x = Math.max(0f, Math.min(photo.getWidth() - 1f, corner.x));
        corner.y = Math.max(0f, Math.min(photo.getHeight() - 1f, corner.y));
    }

    @NonNull
    private float[] copyCorners() {
        return new float[]{
                corners[0].x, corners[0].y,
                corners[1].x, corners[1].y,
                corners[2].x, corners[2].y,
                corners[3].x, corners[3].y
        };
    }

    private void restoreCorners(@NonNull float[] values) {
        for (int i = 0; i < 4; i++) {
            corners[i].set(values[i * 2], values[i * 2 + 1]);
        }
    }

    private int nearestCorner(float x, float y) {
        int best = -1;
        float bestDist = handleRadius;
        for (int i = 0; i < 4; i++) {
            float d = (float) Math.hypot(x - viewCorners[i].x, y - viewCorners[i].y);
            if (d <= bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private int nearestEdge(float x, float y) {
        int best = -1;
        float bestDist = edgeHitWidth;
        for (int i = 0; i < 4; i++) {
            PointF a = viewCorners[i];
            PointF b = viewCorners[(i + 1) % 4];
            float d = distanceToSegment(x, y, a.x, a.y, b.x, b.y);
            if (d <= bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private static float distanceToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len2 = dx * dx + dy * dy;
        if (len2 < 1f) {
            return (float) Math.hypot(px - x1, py - y1);
        }
        float t = ((px - x1) * dx + (py - y1) * dy) / len2;
        t = Math.max(0f, Math.min(1f, t));
        return (float) Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    private void updateViewCorners() {
        if (photo == null || imageDisplayRect.isEmpty()) {
            return;
        }
        float scale = imageDisplayRect.width() / photo.getWidth();
        for (int i = 0; i < 4; i++) {
            viewCorners[i].set(
                    imageDisplayRect.left + corners[i].x * scale,
                    imageDisplayRect.top + corners[i].y * scale
            );
        }
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
