package com.vlad.homelibrary.scan;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * Text overlay that never claims touches so a sibling (e.g. freehand selection)
 * underneath can still receive them.
 */
public class PassThroughTextView extends AppCompatTextView {

    public PassThroughTextView(@NonNull Context context) {
        super(context);
    }

    public PassThroughTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public PassThroughTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }
}
