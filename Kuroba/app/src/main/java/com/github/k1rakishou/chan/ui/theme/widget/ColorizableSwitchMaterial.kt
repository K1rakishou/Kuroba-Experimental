package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.IColorizableWidget
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.manipulateColor
import com.google.android.material.switchmaterial.SwitchMaterial
import javax.inject.Inject

class ColorizableSwitchMaterial @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = androidx.appcompat.R.attr.switchStyle
) : SwitchMaterial(context, attrs, defStyleAttr),
  IColorizableWidget {

  @Inject
  protected lateinit var themeEngine: ThemeEngine

  init {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(context)
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

    thumbTintList = ColorStateList(
      arrayOf(
        intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked),
        intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked),
        intArrayOf(-android.R.attr.state_enabled, android.R.attr.state_checked),
        intArrayOf(-android.R.attr.state_enabled, -android.R.attr.state_checked),
      ),
      intArrayOf(
        themeEngine.chanTheme.accentColor,
        manipulateColor(themeEngine.chanTheme.defaultColors.controlNormalColor, 1.2f),
        themeEngine.chanTheme.getControlDisabledColor(themeEngine.chanTheme.accentColor),
        themeEngine.chanTheme.getControlDisabledColor(themeEngine.chanTheme.defaultColors.controlNormalColor),
      )
    )

    trackTintList = ColorStateList(
      arrayOf(
        intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked),
        intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked),
        intArrayOf(-android.R.attr.state_enabled, android.R.attr.state_checked),
        intArrayOf(-android.R.attr.state_enabled, -android.R.attr.state_checked),
      ),
      intArrayOf(
        manipulateColor(themeEngine.chanTheme.accentColor, .6f),
        manipulateColor(themeEngine.chanTheme.defaultColors.controlNormalColor, .6f),
        themeEngine.chanTheme.getControlDisabledColor(themeEngine.chanTheme.accentColor),
        themeEngine.chanTheme.getControlDisabledColor(themeEngine.chanTheme.defaultColors.controlNormalColor),
      )
    )
  }

}