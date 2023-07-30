package com.github.k1rakishou.chan.features.media_viewer.helper

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.github.k1rakishou.chan.utils.setVisibilityFast

object FullMediaAppearAnimationHelper {
  private val fullMediaAppearInterpolator = DecelerateInterpolator(3f)

  fun fullMediaAppearAnimation(
    prevActiveView: View?,
    activeView: View?,
    isSpoiler: Boolean,
    onAnimationEnd: () -> Unit
  ): AnimatorSet? {
    if (activeView == null || prevActiveView == null) {
      onAnimationEnd()
      return null
    }

    if (isSpoiler) {
      activeView.setVisibilityFast(View.VISIBLE)
      prevActiveView.setVisibilityFast(View.INVISIBLE)
      activeView.alpha = 1f
      onAnimationEnd()
      return null
    }

    val appearanceAnimation = ValueAnimator.ofFloat(0f, 1f)

    appearanceAnimation.addUpdateListener { animation: ValueAnimator ->
      val alpha = animation.animatedValue as Float
      activeView.alpha = alpha
    }

    val animatorSet = AnimatorSet()
    animatorSet.addListener(object : AnimatorListenerAdapter() {
      override fun onAnimationStart(animation: Animator) {
        super.onAnimationStart(animation)

        prevActiveView.alpha = 1f
        activeView.alpha = 0f
        activeView.setVisibilityFast(View.VISIBLE)
      }

      override fun onAnimationEnd(animation: Animator) {
        super.onAnimationEnd(animation)

        prevActiveView.setVisibilityFast(View.INVISIBLE)
        activeView.alpha = 1f

        onAnimationEnd()
      }

      override fun onAnimationCancel(animation: Animator) {
        super.onAnimationCancel(animation)

        prevActiveView.setVisibilityFast(View.INVISIBLE)
        activeView.alpha = 1f
      }
    })

    animatorSet.play(appearanceAnimation)
    animatorSet.interpolator = fullMediaAppearInterpolator
    animatorSet.duration = 200
    animatorSet.start()

    return animatorSet
  }

}