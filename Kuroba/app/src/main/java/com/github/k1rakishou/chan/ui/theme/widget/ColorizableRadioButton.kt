package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.google.android.material.radiobutton.MaterialRadioButton
import javax.inject.Inject

class ColorizableRadioButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  val defStyleAttr: Int = R.attr.radioButtonStyle
) : MaterialRadioButton(context, attrs, defStyleAttr), IColorizableWidget {

  @Inject
  protected lateinit var themeEngine: ThemeEngine

  init {
    if (!isInEditMode) {
      AndroidUtils.extractStartActivityComponent(context)
        .inject(this)
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
        themeEngine.chanTheme.defaultColors.controlNormalColor,
        themeEngine.chanTheme.getControlDisabledColor(themeEngine.chanTheme.defaultColors.controlNormalColor),
        themeEngine.chanTheme.getControlDisabledColor(themeEngine.chanTheme.defaultColors.controlNormalColor),
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
          AndroidUtils.manipulateColor(themeEngine.chanTheme.textColorPrimary, 1.2f),
          themeEngine.chanTheme.textColorPrimary,
          themeEngine.chanTheme.getDisabledTextColor(themeEngine.chanTheme.textColorPrimary),
          themeEngine.chanTheme.getDisabledTextColor(themeEngine.chanTheme.textColorPrimary),
          themeEngine.chanTheme.textColorPrimary
        )
      )
    )
  }
}