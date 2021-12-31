package com.github.k1rakishou.chan.features.image_saver

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.animation.LinearInterpolator
import com.github.k1rakishou.chan.ui.animation.BaseAnimation
import com.github.k1rakishou.core_themes.ThemeEngine

object RootDirBackgroundAnimationFactory {

  @JvmStatic
  fun createRootDirBackgroundAnimation(
    themeEngine: ThemeEngine,
    updateBackgroundColorFunc: (Int) -> Unit
  ): RootDirBackgroundAnimation {
    return RootDirBackgroundAnimation(themeEngine, updateBackgroundColorFunc)
  }

  class RootDirBackgroundAnimation(
    private val themeEngine: ThemeEngine,
    private val updateBackgroundColorFunc: (Int) -> Unit
  ) : BaseAnimation() {
    private var rootDirAnimationLockedAndTurnedOff = false

    private val from = FloatArray(3)
    private val to = FloatArray(3)
    private val hsv = FloatArray(3)

    fun start() {
      if (isRunning()) {
        return
      }

      end()

      if (rootDirAnimationLockedAndTurnedOff) {
        updateBackgroundColorFunc.invoke(themeEngine.chanTheme.backColor)
        return
      }

      Color.colorToHSV(themeEngine.chanTheme.backColor, from)
      Color.colorToHSV(themeEngine.chanTheme.accentColor, to)

      animatorSet = AnimatorSet().apply {
        val colorAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
          addUpdateListener { valueAnimator ->
            hsv[0] = from[0] + (to[0] - from[0]) * (valueAnimator.animatedValue as Float)
            hsv[1] = from[1] + (to[1] - from[1]) * (valueAnimator.animatedValue as Float)
            hsv[2] = from[2] + (to[2] - from[2]) * (valueAnimator.animatedValue as Float)

            updateBackgroundColorFunc.invoke(Color.HSVToColor(hsv))
          }

          duration = ALPHA_ANIMATION_DURATION
          interpolator = LinearInterpolator()
          repeatCount = ValueAnimator.INFINITE
          repeatMode = ValueAnimator.REVERSE
        }

        play(colorAnimation)
        start()
      }
    }

    fun stopAndLock() {
      if (rootDirAnimationLockedAndTurnedOff) {
        return
      }

      rootDirAnimationLockedAndTurnedOff = true
      end()

      updateBackgroundColorFunc.invoke(themeEngine.chanTheme.backColor)
    }

    fun unlock() {
      rootDirAnimationLockedAndTurnedOff = false
    }

    companion object {
      private const val ALPHA_ANIMATION_DURATION = 2_000L
    }
  }

}