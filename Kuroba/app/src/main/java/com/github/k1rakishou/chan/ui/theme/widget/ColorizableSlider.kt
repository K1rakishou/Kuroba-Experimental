package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.google.android.material.slider.Slider
import javax.inject.Inject

class ColorizableSlider @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  val defStyleAttr: Int = R.attr.sliderStyle
) : Slider(context, attrs, defStyleAttr), IColorizableWidget {

  @Inject
  protected lateinit var themeEngine: ThemeEngine

  init {
    if (!isInEditMode) {
      Chan.inject(this)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    applyColors()
  }

  override fun applyColors() {
    if (isInEditMode) {
      return
    }

    haloTintList = ColorStateList.valueOf(themeEngine.chanTheme.textColorPrimary)

    thumbTintList = ColorStateList(
      arrayOf(
        intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
        intArrayOf(android.R.attr.state_enabled),
        intArrayOf(-android.R.attr.state_enabled),
        intArrayOf()
      ),
      intArrayOf(
        AndroidUtils.manipulateColor(themeEngine.chanTheme.accentColor, 1.2f),
        themeEngine.chanTheme.accentColor,
        AndroidUtils.manipulateColor(themeEngine.chanTheme.textColorPrimary, .6f),
        themeEngine.chanTheme.accentColor
      )
    )

    trackTintList = ColorStateList(
      arrayOf(
        intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
        intArrayOf(android.R.attr.state_enabled),
        intArrayOf(-android.R.attr.state_enabled),
        intArrayOf(),
      ),
      intArrayOf(
        themeEngine.chanTheme.accentColor,
        AndroidUtils.manipulateColor(themeEngine.chanTheme.textColorPrimary, .6f),
        AndroidUtils.manipulateColor(themeEngine.chanTheme.textColorPrimary, .3f),
        AndroidUtils.manipulateColor(themeEngine.chanTheme.textColorPrimary, .6f)
      )
    )
  }

}