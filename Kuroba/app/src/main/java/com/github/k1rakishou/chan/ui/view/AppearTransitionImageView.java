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
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AccelerateInterpolator;

import androidx.appcompat.widget.AppCompatImageView;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

public class AppearTransitionImageView extends AppCompatImageView {
    private static final String TAG = "TransitionImageView";
    private static final int ANIMATION_DURATION = 150;
    private static final AccelerateInterpolator INTERPOLATOR = new AccelerateInterpolator(3f);

    private final PointF globalRevealStartPosition = new PointF(0f, 0f);

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

    public void setWindowLocation(int lastTouchPosX, int lastTouchPosy) {
        int[] myLoc = new int[2];
        getLocationInWindow(myLoc);
        float globalOffsetX = lastTouchPosX - myLoc[0];
        float globalOffsetY = lastTouchPosy - myLoc[1];

        globalRevealStartPosition.set(globalOffsetX, globalOffsetY);
    }

    public void runAppearAnimation(
            View controllerRootView,
            int startBackgroundColor,
            int finalBackgroundColor,
            Function1<Integer, Unit> backgroundColorFunc,
            Function0<Unit> onEndFunc
    ) {
        int cx = controllerRootView.getWidth() / 2;
        int cy = controllerRootView.getHeight() / 2;

        float finalRadius = (float) Math.hypot(cx, cy);

        AnimatorSet animatorSet = new AnimatorSet();

        Animator circularRevealAnimation = ViewAnimationUtils.createCircularReveal(
                controllerRootView,
                (int) globalRevealStartPosition.x,
                (int) globalRevealStartPosition.y,
                0f,
                finalRadius
        );

        ValueAnimator backgroundColorAnimation = ValueAnimator.ofArgb(startBackgroundColor, finalBackgroundColor);
        backgroundColorAnimation.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            backgroundColorFunc.invoke(color);
        });

        animatorSet.playTogether(circularRevealAnimation, backgroundColorAnimation);
        animatorSet.setDuration(ANIMATION_DURATION);
        animatorSet.setInterpolator(INTERPOLATOR);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onEndFunc.invoke();
            }
        });

        setVisibility(View.VISIBLE);
        animatorSet.start();
    }

}
