package com.github.k1rakishou.chan.ui.view

import android.animation.Animator
import android.content.Context
import android.util.AttributeSet
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.toolbar.Toolbar.ToolbarCollapseCallback
import com.github.k1rakishou.chan.ui.widget.SimpleAnimatorListener
import com.github.k1rakishou.common.updatePaddings
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlin.math.abs
import kotlin.math.max

class HidingBottomNavigationView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : BottomNavigationView(context, attrs, defStyleAttr), ToolbarCollapseCallback {
  private var attachedToWindow = false
  private var toolbar: Toolbar? = null
  private var attachedToToolbar = false

  private var animating = false
  private var lastCollapseTranslationOffset = 0f
  private var isTranslationLocked = false
  private var isCollapseLocked = false
  private var maxViewHeight: Int = 0

  init {
    setOnApplyWindowInsetsListener(null)
  }

  fun updateMaxViewHeight(height: Int) {
    maxViewHeight = max(height, maxViewHeight)
  }

  fun updateBottomPadding(padding: Int) {
    updatePaddings(bottom = padding)
  }

  fun setToolbar(toolbar: Toolbar) {
    this.toolbar = toolbar

    if (attachedToWindow && !attachedToToolbar) {
      toolbar.addCollapseCallback(this)
      attachedToToolbar = true
    }
  }

  fun hide(lockTranslation: Boolean, lockCollapse: Boolean) {
    if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
      throw IllegalStateException("The nav bar should always be visible when using SPLIT layout")
    }

    onCollapseAnimationInternal(collapse = true, isFromToolbarCallbacks = false)

    if (lockTranslation) {
      isTranslationLocked = true
    }

    if (lockCollapse) {
      isCollapseLocked = true
    }
  }

  fun show(unlockTranslation: Boolean, unlockCollapse: Boolean) {
    if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
      throw IllegalStateException("The nav bar should always be visible when using SPLIT layout")
    }

    if (unlockTranslation) {
      isTranslationLocked = false
    }

    if (unlockCollapse) {
      isCollapseLocked = false
    }

    onCollapseAnimationInternal(collapse = false, isFromToolbarCallbacks = false)
  }

  fun resetState(unlockTranslation: Boolean, unlockCollapse: Boolean) {
    isTranslationLocked = !unlockTranslation
    isCollapseLocked = !unlockCollapse

    restoreHeightWithAnimation()
  }

  fun isFullyVisible(): Boolean {
    return translationY.toInt() == 0
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    attachedToWindow = true

    if (toolbar != null && !attachedToToolbar) {
      toolbar?.addCollapseCallback(this)
      attachedToToolbar = true
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    attachedToWindow = false

    if (attachedToToolbar) {
      toolbar?.removeCollapseCallback(this)
      attachedToToolbar = false
    }
  }

  override fun onCollapseTranslation(offset: Float) {
    lastCollapseTranslationOffset = offset

    if (isCollapseLocked) {
      return
    }

    val translation = (maxViewHeight * offset)
    if (translation.toInt() == translationY.toInt()) {
      return
    }

    if (animating) {
      return
    }

    val diff = abs(translation - translationY)
    if (diff >= height) {
      animate()
        .translationY(translation)
        .setDuration(Toolbar.TOOLBAR_ANIMATION_DURATION_MS)
        .setStartDelay(0)
        .setInterpolator(Toolbar.TOOLBAR_ANIMATION_INTERPOLATOR)
        .start()
    } else {
      translationY = translation
    }
  }

  override fun onCollapseAnimation(collapse: Boolean) {
    onCollapseAnimationInternal(collapse, true)
  }

  private fun onCollapseAnimationInternal(collapse: Boolean, isFromToolbarCallbacks: Boolean) {
    if (isFromToolbarCallbacks) {
      lastCollapseTranslationOffset = if (collapse) {
        1f
      } else {
        0f
      }
    }

    if (isTranslationLocked) {
      return
    }

    val translation = if (collapse) {
      maxViewHeight.toFloat()
    } else {
      0f
    }

    if (translation.toInt() == translationY.toInt()) {
      return
    }

    animate().cancel()

    animate()
      .translationY(translation)
      .setDuration(Toolbar.TOOLBAR_ANIMATION_DURATION_MS)
      .setStartDelay(0)
      .setInterpolator(Toolbar.TOOLBAR_ANIMATION_INTERPOLATOR)
      .setListener(object : SimpleAnimatorListener() {
        override fun onAnimationEnd(animation: Animator?) {
          animating = false
        }

        override fun onAnimationCancel(animation: Animator?) {
          animating = false
        }

        override fun onAnimationStart(animation: Animator?) {
          animating = true
        }
      })
      .start()
  }

  private fun restoreHeightWithAnimation() {
    if (isTranslationLocked || isCollapseLocked) {
      return
    }

    val translation = lastCollapseTranslationOffset * maxViewHeight.toFloat()
    if (translation.toInt() == translationY.toInt()) {
      return
    }

    animate().cancel()

    animate()
      .translationY(translation)
      .setDuration(Toolbar.TOOLBAR_ANIMATION_DURATION_MS)
      .setStartDelay(0)
      .setInterpolator(Toolbar.TOOLBAR_ANIMATION_INTERPOLATOR)
      .start()
  }
}