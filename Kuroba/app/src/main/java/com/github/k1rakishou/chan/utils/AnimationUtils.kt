package com.github.k1rakishou.chan.utils

import android.animation.Animator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.ScaleAnimation
import com.github.k1rakishou.chan.ui.widget.SimpleAnimatorListener

object AnimationUtils {

  @JvmStatic
  fun animateViewScale(view: View, zoomOut: Boolean, duration: Int) {
    val scaleAnimation: ScaleAnimation
    val normalScale = 1.0f
    val zoomOutScale = 0.8f
    scaleAnimation = if (zoomOut) {
      ScaleAnimation(
        normalScale,
        zoomOutScale,
        normalScale,
        zoomOutScale,
        ScaleAnimation.RELATIVE_TO_SELF,
        0.5f,
        ScaleAnimation.RELATIVE_TO_SELF,
        0.5f
      )
    } else {
      ScaleAnimation(
        zoomOutScale,
        normalScale,
        zoomOutScale,
        normalScale,
        ScaleAnimation.RELATIVE_TO_SELF,
        0.5f,
        ScaleAnimation.RELATIVE_TO_SELF,
        0.5f
      )
    }
    scaleAnimation.duration = duration.toLong()
    scaleAnimation.fillAfter = true
    scaleAnimation.interpolator = AccelerateDecelerateInterpolator()
    view.startAnimation(scaleAnimation)
  }

  @JvmOverloads
  @JvmStatic
  fun View.fadeOut(
    duration: Long,
    animator: ValueAnimator?,
    onEnd: (() -> Unit)? = null
  ): ValueAnimator? {
    animator?.end()

    if (visibility == View.GONE) {
      return animator
    }

    return ValueAnimator.ofFloat(1f, 0f).apply {
      this.duration = duration
      addUpdateListener { animation ->
        alpha = animation.animatedValue as Float
      }
      addListener(object : SimpleAnimatorListener() {
        override fun onAnimationStart(animation: Animator?) {
          alpha = 1f
        }

        override fun onAnimationEnd(animation: Animator?) {
          alpha = 0f
          setVisibilityFast(View.GONE)
          onEnd?.invoke()
        }
      })
      start()
    }
  }

  @JvmOverloads
  @JvmStatic
  fun View.fadeIn(
    duration: Long,
    animator: ValueAnimator?,
    onEnd: (() -> Unit)? = null
  ): ValueAnimator? {
    animator?.end()

    if (visibility == View.VISIBLE) {
      return animator
    }

    return ValueAnimator.ofFloat(0f, 1f).apply {
      this.duration = duration
      addUpdateListener { animation ->
        alpha = animation.animatedValue as Float
      }
      addListener(object : SimpleAnimatorListener() {
        override fun onAnimationStart(animation: Animator?) {
          alpha = 0f
        }

        override fun onAnimationEnd(animation: Animator?) {
          alpha = 1f
          setVisibilityFast(View.VISIBLE)
          onEnd?.invoke()
        }
      })
      start()
    }
  }

}