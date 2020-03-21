package com.github.adamantcheese.chan.ui.animation

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.github.adamantcheese.common.ModularFunction

object PostImageThumbnailViewAnimator {

    @JvmStatic
    fun createThumbnailPrefetchProgressIndicatorAnimation() = ThumbnailPrefetchProgressIndicatorAnimation()

    class ThumbnailPrefetchProgressIndicatorAnimation : BaseAnimation() {

        fun start(rotationFunc: ModularFunction<Float>) {
            end()

            animatorSet = AnimatorSet().apply {
                val alphaAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
                    addUpdateListener { valueAnimator ->
                        rotationFunc.invoke(valueAnimator.animatedValue as Float)
                    }

                    duration = ALPHA_ANIMATION_DURATION
                    interpolator = LinearInterpolator()
                    repeatCount = ValueAnimator.INFINITE
                }

                play(alphaAnimation)
                start()
            }
        }

        companion object {
            private const val ALPHA_ANIMATION_DURATION = 1_000L
        }
    }

}