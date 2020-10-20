package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.google.android.material.chip.Chip
import javax.inject.Inject

class ColorizableChip @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  val defStyleAttr: Int = R.attr.chipStyle
) : Chip(context, attrs, defStyleAttr), IColorizableWidget {

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

    val isBackColorDark = AndroidUtils.isDarkColor(themeEngine.chanTheme.backColor)

    val chipBackColor = if (isBackColorDark) {
      AndroidUtils.manipulateColor(themeEngine.chanTheme.backColor, 1.4f)
    } else {
      AndroidUtils.manipulateColor(themeEngine.chanTheme.backColor, .6f)
    }

    chipBackgroundColor = ColorStateList(
      arrayOf(
        intArrayOf(android.R.attr.state_checked, android.R.attr.state_enabled),
        intArrayOf(-android.R.attr.state_checked, android.R.attr.state_enabled),
        intArrayOf(android.R.attr.state_checked, -android.R.attr.state_enabled),
        intArrayOf(-android.R.attr.state_checked, -android.R.attr.state_enabled),
        intArrayOf()
      ),
      intArrayOf(
        themeEngine.chanTheme.accentColor,
        chipBackColor,
        themeEngine.chanTheme.getControlDisabledColor(chipBackColor),
        themeEngine.chanTheme.getControlDisabledColor(chipBackColor),
        chipBackColor
      )
    )

    highlightColor = themeEngine.chanTheme.accentColor

    val textColor = if (AndroidUtils.isDarkColor(chipBackColor)) {
      Color.WHITE
    } else {
      Color.BLACK
    }

    setTextColor(textColor)
  }

}