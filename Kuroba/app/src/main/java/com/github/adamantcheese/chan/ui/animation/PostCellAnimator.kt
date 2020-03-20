package com.github.adamantcheese.chan.ui.animation

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

object PostCellAnimator {

    @JvmStatic
    fun createUnseenPostIndicatorFadeAnimation() = UnseenPostIndicatorFadeAnimation()

    class UnseenPostIndicatorFadeAnimation : BaseAnimation() {

        fun start(alphaFunc: (Float) -> Unit) {
            end()

            animatorSet = AnimatorSet().apply {
                val alphaAnimation = ValueAnimator.ofFloat(1f, 0f).apply {
                    addUpdateListener { valueAnimator ->
                        alphaFunc(valueAnimator.animatedValue as Float)
                    }

                    startDelay = ALPHA_ANIMATION_DELAY_MS
                    duration = ALPHA_ANIMATION_DURATION
                    interpolator = LinearInterpolator()
                }

                play(alphaAnimation)
                start()
            }
        }

        companion object {
            private const val ALPHA_ANIMATION_DELAY_MS = 5_000L
            private const val ALPHA_ANIMATION_DURATION = 3_000L
        }
    }

}