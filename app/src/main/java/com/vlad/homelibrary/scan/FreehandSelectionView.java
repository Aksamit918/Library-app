package com.vlad.homelibrary.scan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Overlay for drawing a freehand lasso; reports the closed path on finger lift.
 */
public class FreehandSelectionView extends View {

    public interface SelectionListener {
        void onSelectionComplete(Path path, RectF boundsInView);

        void onSelectionTooSmall();
    }

    private final Path drawPath = new Path();
    private final Path closedPath = new Path();
    private final RectF pathBounds = new RectF();
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private SelectionListener listener;
    private boolean drawingEnabled = true;
    private boolean isDrawing = false;
    private boolean hasClosedSelection = false;
    private float lastX;
    private float lastY;
    private final float touchTolerance;
    private final float minSelectionSizePx;

    public FreehandSelectionView(Context context) {
        this(context, null);
    }

    public FreehandSelectionView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FreehandSelectionView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayerType(LAYER_TYPE_HARDWARE, null);

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
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

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
        if (!drawingEnabled || !isEnabled()) {
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

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
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
                    Path pathCopy = new Path(closedPath);
                    RectF boundsCopy = new RectF(pathBounds);
                    listener.onSelectionComplete(pathCopy, boundsCopy);
                }
                return true;

            default:
                return false;
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
