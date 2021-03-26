package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import com.github.k1rakishou.chan.ui.widget.DisableableLayout
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.IColorizableWidget
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.android.material.tabs.TabLayout
import javax.inject.Inject

class ColorizableTabLayout @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : TabLayout(context, attributeSet), IColorizableWidget {
  private var disableableLayout: DisableableLayout? = null

  @Inject
  protected lateinit var themeEngine: ThemeEngine

  init {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(context)
        .inject(this)
    }
  }

  fun setDisableableLayoutHandler(disableableLayout: DisableableLayout) {
    this.disableableLayout = disableableLayout
  }

  fun removeDisableableLayoutHandler() {
    this.disableableLayout = null
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    if (!isInEditMode) {
      applyColors()
    }
  }

  override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
    if (disableableLayout?.isLayoutEnabled() != false) {
      return super.onInterceptTouchEvent(event)
    }

    return true
  }

  override fun onTouchEvent(event: MotionEvent?): Boolean {
    if (disableableLayout?.isLayoutEnabled() != false) {
      return super.onTouchEvent(event)
    }

    return false
  }

  override fun applyColors() {
    setBackgroundColor(themeEngine.chanTheme.primaryColor)

    val normalTextColor = if (ThemeEngine.isNearToFullyBlackColor(themeEngine.chanTheme.primaryColor)) {
      Color.DKGRAY
    } else {
      ThemeEngine.manipulateColor(themeEngine.chanTheme.primaryColor, .7f)
    }

    setTabTextColors(
      normalTextColor,
      Color.WHITE
    )

    setSelectedTabIndicatorColor(Color.WHITE)
  }
}