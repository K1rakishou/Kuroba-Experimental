package com.github.k1rakishou.chan.ui.animation

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.github.k1rakishou.chan.ui.widget.SimpleAnimatorListener

object PostCellAnimator {

  @JvmStatic
  fun createUnseenPostIndicatorFadeAnimation() = UnseenPostIndicatorFadeAnimation()

  class UnseenPostIndicatorFadeAnimation : BaseAnimation() {

    fun start(alphaFunc: (Float) -> Unit, onAnimationEndFunc: () -> Unit) {
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
      private const val ALPHA_ANIMATION_DELAY_MS = 2_000L
      private const val ALPHA_ANIMATION_DURATION = 2_000L

      const val ANIMATIONS_TOTAL_TIME = ALPHA_ANIMATION_DELAY_MS + ALPHA_ANIMATION_DURATION
    }
  }

}