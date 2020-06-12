package com.github.adamantcheese.chan.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.view.View;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;

public class AnimationUtils {

    public static void animateStatusBar(
            Window window,
            boolean in,
            final int originalColor,
            final int duration
    ) {
        final int targetColor = calculateTargetColor(in, originalColor);

        ValueAnimator statusBar = ValueAnimator.ofArgb(
                originalColor,
                targetColor
        );

        statusBar.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            window.setStatusBarColor(color);
        });
        statusBar.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);

                window.setStatusBarColor(originalColor);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);

                window.setStatusBarColor(targetColor);
            }
        });
        statusBar
                .setDuration(duration)
                .setInterpolator(new LinearInterpolator());

        statusBar.start();
    }

    private static int calculateTargetColor(boolean in, int originalColor) {
        float progress = in ? 0.5f : 0f;

        int r = (int) ((1f - progress) * Color.red(originalColor));
        int g = (int) ((1f - progress) * Color.green(originalColor));
        int b = (int) ((1f - progress) * Color.blue(originalColor));

        return Color.argb(255, r, g, b);
    }

    public static void animateViewScale(View view, boolean zoomOut, int duration) {
        ScaleAnimation scaleAnimation;
        final float normalScale = 1.0f;
        final float zoomOutScale = 0.8f;

        if (zoomOut) {
            scaleAnimation = new ScaleAnimation(
                    normalScale,
                    zoomOutScale,
                    normalScale,
                    zoomOutScale,
                    ScaleAnimation.RELATIVE_TO_SELF,
                    0.5f,
                    ScaleAnimation.RELATIVE_TO_SELF,
                    0.5f
            );
        } else {
            scaleAnimation = new ScaleAnimation(
                    zoomOutScale,
                    normalScale,
                    zoomOutScale,
                    normalScale,
                    ScaleAnimation.RELATIVE_TO_SELF,
                    0.5f,
                    ScaleAnimation.RELATIVE_TO_SELF,
                    0.5f
            );
        }

        scaleAnimation.setDuration(duration);
        scaleAnimation.setFillAfter(true);
        scaleAnimation.setInterpolator(new AccelerateDecelerateInterpolator());

        view.startAnimation(scaleAnimation);
    }
}
