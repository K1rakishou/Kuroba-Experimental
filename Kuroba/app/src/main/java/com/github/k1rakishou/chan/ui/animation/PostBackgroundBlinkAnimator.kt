package com.github.k1rakishou.chan.ui.animation

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.animation.ValueAnimator.REVERSE
import android.view.animation.LinearInterpolator
import com.github.k1rakishou.chan.ui.widget.SimpleAnimatorListener

object PostBackgroundBlinkAnimator {

  @JvmStatic
  fun createPostBackgroundBlinkAnimation() = PostBackgroundBlinkAnimation()

  class PostBackgroundBlinkAnimation : BaseAnimation() {

    fun start(startColor: Int, endColor: Int, colorUpdateFunc: (Int) -> Unit, onAnimationEndFunc: () -> Unit) {
      end()

      animatorSet = AnimatorSet().apply {
        val colorAnimation = ValueAnimator.ofArgb(startColor, endColor).apply {
          addUpdateListener { valueAnimator ->
            colorUpdateFunc.invoke(valueAnimator.animatedValue as Int)
          }
          addListener(object : SimpleAnimatorListener() {
            override fun onAnimationEnd(animation: Animator?) {
              onAnimationEndFunc.invoke()
            }
          })

          duration = 250L
          repeatCount = 4
          repeatMode = REVERSE
          interpolator = LinearInterpolator()
        }

        play(colorAnimation)
        start()
      }
    }

  }

}