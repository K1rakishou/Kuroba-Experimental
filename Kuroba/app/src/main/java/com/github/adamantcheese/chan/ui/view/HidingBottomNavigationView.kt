package com.github.adamantcheese.chan.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.ui.toolbar.Toolbar
import com.github.adamantcheese.chan.ui.toolbar.Toolbar.ToolbarCollapseCallback
import com.google.android.material.bottomnavigation.BottomNavigationView

class HidingBottomNavigationView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : BottomNavigationView(context, attrs, defStyleAttr), ToolbarCollapseCallback {
  private var attachedToWindow = false
  private var toolbar: Toolbar? = null
  private var attachedToToolbar = false
  private var isTranslationLocked = false
  private var isCollapseLocked = false

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

    onCollapseAnimation(true)

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

    onCollapseAnimation(false)
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
    if (isCollapseLocked) {
      return
    }

    val translation = (getTotalHeight() * offset)
    if (translation.toInt() == translationY.toInt()) {
      return
    }

    val diff = kotlin.math.abs(translation - translationY)
    if (diff >= height) {
      animate()
        .translationY(translation)
        .setDuration(300)
        .setStartDelay(0)
        .setInterpolator(SLOWDOWN)
        .start()
    } else {
      translationY = translation
    }
  }

  override fun onCollapseAnimation(collapse: Boolean) {
    if (isTranslationLocked) {
      return
    }

    val translation = if (collapse) {
      getTotalHeight().toFloat()
    } else {
      0f
    }

    if (translation.toInt() == translationY.toInt()) {
      return
    }

    animate()
      .translationY(translation)
      .setDuration(300)
      .setStartDelay(0)
      .setInterpolator(SLOWDOWN)
      .start()
  }

  private fun getTotalHeight(): Int {
    return height + (layoutParams as MarginLayoutParams).bottomMargin
  }

  companion object {
    private val SLOWDOWN = DecelerateInterpolator(2f)
  }
}