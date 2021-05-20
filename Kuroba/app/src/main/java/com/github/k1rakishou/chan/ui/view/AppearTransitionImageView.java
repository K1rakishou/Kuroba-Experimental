/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewAnimationUtils;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

public class AppearTransitionImageView extends AppCompatImageView {
    private static final String TAG = "TransitionImageView";

    private PointF globalRevealStartPosition = new PointF(0f, 0f);

    public AppearTransitionImageView(Context context) {
        super(context);
        init();
    }

    public AppearTransitionImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AppearTransitionImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setScaleType(ScaleType.FIT_CENTER);
    }

    public void setBitmap(Bitmap bitmap) {
        super.setImageBitmap(bitmap);
    }

    public void setWindowLocation(Point lastTouchCoordinates) {
        int[] myLoc = new int[2];
        getLocationInWindow(myLoc);
        float globalOffsetX = lastTouchCoordinates.x - myLoc[0];
        float globalOffsetY = lastTouchCoordinates.y - myLoc[1];

        globalRevealStartPosition.set(globalOffsetX, globalOffsetY);
    }

    public Animator runAppearAnimation(View controllerRootView, Function0<Unit> onEndFunc) {
        int cx = controllerRootView.getWidth() / 2;
        int cy = controllerRootView.getHeight() / 2;

        float finalRadius = (float) Math.hypot(cx, cy);

        Animator circularReveal = ViewAnimationUtils.createCircularReveal(
                controllerRootView,
                (int) globalRevealStartPosition.x,
                (int) globalRevealStartPosition.y,
                0f,
                finalRadius
        );

        circularReveal.setDuration(200);
        circularReveal.setInterpolator(new FastOutSlowInInterpolator());

        setVisibility(View.VISIBLE);

        circularReveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onEndFunc.invoke();
            }
        });

        circularReveal.start();

        return circularReveal;
    }

}
