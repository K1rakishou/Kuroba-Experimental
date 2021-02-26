package com.github.k1rakishou.chan.ui.view

import android.animation.Animator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.toolbar.Toolbar.ToolbarCollapseCallback
import com.github.k1rakishou.chan.ui.widget.SimpleAnimatorListener
import com.github.k1rakishou.common.updatePaddings
import com.google.android.material.bottomnavigation.BottomNavigationView
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

  private var interceptTouchEventListener: ((MotionEvent) -> Boolean)? = null
  private var touchEventListener: ((MotionEvent) -> Boolean)? = null

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
    return alpha >= 0.99f
  }

  override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
    if (!isFullyVisible()) {
      // Steal event from children
      return true
    }

    if (ev != null) {
      val result = interceptTouchEventListener?.invoke(ev)
      if (result == true) {
        return true
      }
    }

    return super.onInterceptTouchEvent(ev)
  }

  override fun onTouchEvent(event: MotionEvent?): Boolean {
    if (!isFullyVisible()) {
      // Pass event to other views
      return false
    }

    if (event != null) {
      val result = touchEventListener?.invoke(event)
      if (result == true) {
        return true
      }
    }

    return super.onTouchEvent(event)
  }

  fun setOnOuterInterceptTouchEventListener(listener: (MotionEvent) -> Boolean) {
    this.interceptTouchEventListener = listener
  }

  fun setOnOuterTouchEventListener(listener: (MotionEvent) -> Boolean) {
    this.touchEventListener = listener
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

    val newAlpha = 1f - offset
    if (newAlpha == alpha) {
      return
    }

    if (animating) {
      return
    }

    this.alpha = newAlpha
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

    val newAlpha = if (collapse) {
      0f
    } else {
      1f
    }

    if (newAlpha == alpha) {
      return
    }

    animate().cancel()

    animate()
      .alpha(newAlpha)
      .setDuration(Toolbar.TOOLBAR_ANIMATION_DURATION_MS)
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

    val newAlpha = 1f - lastCollapseTranslationOffset
    if (newAlpha == alpha) {
      return
    }

    animate().cancel()

    animate()
      .alpha(newAlpha)
      .setDuration(Toolbar.TOOLBAR_ANIMATION_DURATION_MS)
      .setInterpolator(Toolbar.TOOLBAR_ANIMATION_INTERPOLATOR)
      .start()
  }
}