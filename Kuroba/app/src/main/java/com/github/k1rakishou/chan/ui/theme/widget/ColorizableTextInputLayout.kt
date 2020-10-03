package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.google.android.material.textfield.TextInputLayout
import javax.inject.Inject

class ColorizableTextInputLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = R.attr.textInputStyle
) : TextInputLayout(context, attrs, defStyleAttr), IColorizableWidget {

  @Inject
  protected lateinit var themeEngine: ThemeEngine

  init {
    if (!isInEditMode) {
      Chan.inject(this)
    }

    boxBackgroundMode = BOX_BACKGROUND_OUTLINE
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    applyColors()
  }

  override fun applyColors() {
    if (isInEditMode) {
      return
    }

    boxStrokeErrorColor = ColorStateList(
      arrayOf(
        intArrayOf(-android.R.attr.state_enabled),
        intArrayOf(android.R.attr.state_enabled),
      ),
      intArrayOf(
        themeEngine.chanTheme.getControlDisabledColor(themeEngine.chanTheme.defaultColors.controlNormalColor),
        themeEngine.chanTheme.errorColor
      )
    )

    counterOverflowTextColor = ColorStateList(
      arrayOf(
        intArrayOf(-android.R.attr.state_enabled),
        intArrayOf(android.R.attr.state_enabled),
      ),
      intArrayOf(
        themeEngine.chanTheme.getDisabledTextColor(themeEngine.chanTheme.textColorPrimary),
        themeEngine.chanTheme.errorColor
      )
    )

    val colorStateList = ColorStateList(
      arrayOf(
        intArrayOf(-android.R.attr.state_enabled),
        intArrayOf(android.R.attr.state_focused, android.R.attr.state_enabled),
        intArrayOf()
      ),
      intArrayOf(
        themeEngine.chanTheme.textColorHint,
        themeEngine.chanTheme.accentColor,
        themeEngine.chanTheme.textColorSecondary,
      )
    )

    hintTextColor = colorStateList
    defaultHintTextColor = colorStateList
    counterTextColor = colorStateList

    setBoxStrokeColorStateList(
      ColorStateList(
        arrayOf(
          intArrayOf(-android.R.attr.state_enabled),
          intArrayOf(android.R.attr.state_focused, android.R.attr.state_enabled),
          intArrayOf()
        ),
        intArrayOf(
          themeEngine.chanTheme.getControlDisabledColor(themeEngine.chanTheme.defaultColors.controlNormalColor),
          themeEngine.chanTheme.accentColor,
          themeEngine.chanTheme.defaultColors.controlNormalColor,
        )
      )
    )

    setBoxBackgroundColorStateList(ColorStateList.valueOf(Color.TRANSPARENT))
  }

}