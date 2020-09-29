package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.google.android.material.checkbox.MaterialCheckBox
import javax.inject.Inject

class ColorizableCheckBox @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  val defStyleAttr: Int = R.attr.checkboxStyle
) : MaterialCheckBox(context, attrs, defStyleAttr), IColorizableWidget {

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

    buttonTintList = ColorStateList(
      arrayOf(
        intArrayOf(android.R.attr.state_checked, android.R.attr.state_enabled),
        intArrayOf(-android.R.attr.state_checked, android.R.attr.state_enabled),
        intArrayOf(android.R.attr.state_checked, -android.R.attr.state_enabled),
        intArrayOf(-android.R.attr.state_checked, -android.R.attr.state_enabled),
        intArrayOf()
      ),
      intArrayOf(
        themeEngine.chanTheme.accentColor,
        themeEngine.chanTheme.textPrimaryColor,
        AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, .5f),
        AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, .5f),
        themeEngine.chanTheme.accentColor
      )
    )

    setTextColor(
      ColorStateList(
        arrayOf(
          intArrayOf(android.R.attr.state_checked, android.R.attr.state_enabled),
          intArrayOf(-android.R.attr.state_checked, android.R.attr.state_enabled),
          intArrayOf(android.R.attr.state_checked, -android.R.attr.state_enabled),
          intArrayOf(-android.R.attr.state_checked, -android.R.attr.state_enabled),
          intArrayOf()
        ),
        intArrayOf(
          AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, 1.2f),
          themeEngine.chanTheme.textPrimaryColor,
          AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, .5f),
          AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, .5f),
          themeEngine.chanTheme.textPrimaryColor
        )
      )
    )
  }

}