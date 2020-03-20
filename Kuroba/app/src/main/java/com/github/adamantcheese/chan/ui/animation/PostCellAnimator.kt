package com.github.adamantcheese.chan.ui.animation

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.github.adamantcheese.chan.ui.widget.SimpleAnimatorListener
import com.github.adamantcheese.common.ModularFunction
import com.github.adamantcheese.common.VoidFunction

object PostCellAnimator {

    @JvmStatic
    fun createUnseenPostIndicatorFadeAnimation() = UnseenPostIndicatorFadeAnimation()

    class UnseenPostIndicatorFadeAnimation : BaseAnimation() {

        fun start(alphaFunc: ModularFunction<Float>, onAnimationEndFunc: VoidFunction) {
            end()

            animatorSet = AnimatorSet().apply {
                val alphaAnimation = ValueAnimator.ofFloat(1f, 0f).apply {
                    addUpdateListener { valueAnimator ->
                        alphaFunc.invoke(valueAnimator.animatedValue as Float)
                    }
                    addListener(object : SimpleAnimatorListener() {
                        override fun onAnimationEnd(animation: Animator?) {
                            onAnimationEndFunc.invoke()
                        }
                    })

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