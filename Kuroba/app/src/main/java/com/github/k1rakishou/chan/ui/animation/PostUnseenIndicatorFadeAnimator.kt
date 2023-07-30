package com.github.k1rakishou.chan.ui.animation

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.github.k1rakishou.chan.ui.widget.SimpleAnimatorListener

object PostUnseenIndicatorFadeAnimator {
  const val ANIMATION_DURATION = 4_000L

  @JvmStatic
  fun createUnseenPostIndicatorFadeAnimation() = UnseenPostIndicatorFadeAnimation()

  fun calcAlphaFromRemainingTime(remainingTime: Int): Float {
    return (remainingTime.toFloat() / ANIMATION_DURATION.toFloat()).coerceIn(0f, 1f)
  }

  class UnseenPostIndicatorFadeAnimation : BaseAnimation() {

    fun start(remainingTime: Int, alphaFunc: (Float) -> Unit, onAnimationEndFunc: () -> Unit) {
      end()

      val startAlpha = calcAlphaFromRemainingTime(remainingTime)

      if (startAlpha <= 0f || remainingTime <= 0) {
        alphaFunc.invoke(0f)
        onAnimationEndFunc.invoke()
        return
      }

      animatorSet = AnimatorSet().apply {
        val alphaAnimation = ValueAnimator.ofFloat(startAlpha, 0f).apply {
          addUpdateListener { valueAnimator ->
            alphaFunc.invoke(valueAnimator.animatedValue as Float)
          }
          addListener(object : SimpleAnimatorListener() {
            override fun onAnimationEnd(animation: Animator) {
              onAnimationEndFunc.invoke()
            }
          })

          duration = remainingTime.toLong()
          interpolator = LinearInterpolator()
        }

        play(alphaAnimation)
        start()
      }
    }
  }

}