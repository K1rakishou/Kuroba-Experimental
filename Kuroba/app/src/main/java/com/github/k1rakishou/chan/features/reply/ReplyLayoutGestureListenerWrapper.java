package com.github.k1rakishou.chan.features.reply;

import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

public abstract class ReplyLayoutGestureListenerWrapper extends GestureDetector.SimpleOnGestureListener {

    @Override
    public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
        if (e1 == null || e2 == null) {
            return false;
        }

        return scroll(e1, e2, distanceX, distanceY);
    }

    @Override
    public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
        if (e1 == null || e2 == null) {
            return false;
        }

        return fling(e1, e2, velocityX, velocityY);
    }

    public abstract boolean scroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY);
    public abstract boolean fling(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY);

}
