package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.google.android.material.switchmaterial.SwitchMaterial
import javax.inject.Inject

class ColorizableSwitchMaterial @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = R.attr.switchStyle
) : SwitchMaterial(context, attrs, defStyleAttr), IColorizableWidget {

  @Inject
  protected lateinit var themeEngine: ThemeEngine

  init {
    if (!isInEditMode) {
      Chan.inject(this)
    }
  }

  override fun onFinishInflate() {
    super.onFinishInflate()

    applyColors()
  }

  override fun applyColors() {
    thumbTintList = ColorStateList(
      arrayOf(
        intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked),
        intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked),
        intArrayOf(-android.R.attr.state_enabled, android.R.attr.state_checked),
        intArrayOf(-android.R.attr.state_enabled, -android.R.attr.state_checked),
      ),
      intArrayOf(
        themeEngine.chanTheme.accentColor,
        themeEngine.chanTheme.textPrimaryColor,
        AndroidUtils.manipulateColor(themeEngine.chanTheme.accentColor, .6f),
        AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, .6f),
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
        AndroidUtils.manipulateColor(themeEngine.chanTheme.accentColor, .6f),
        AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, .6f),
        AndroidUtils.manipulateColor(themeEngine.chanTheme.accentColor, .3f),
        AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, .3f),
      )
    )
  }

}