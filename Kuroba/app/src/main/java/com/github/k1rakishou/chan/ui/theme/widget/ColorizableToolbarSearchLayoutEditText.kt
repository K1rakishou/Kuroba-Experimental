package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import androidx.core.view.ViewCompat
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.ViewUtils.setEditTextCursorColor
import com.github.k1rakishou.chan.utils.ViewUtils.setHandlesColors
import com.github.k1rakishou.core_themes.IColorizableWidget
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.android.material.textfield.TextInputEditText
import javax.inject.Inject

class ColorizableToolbarSearchLayoutEditText @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : TextInputEditText(context, attrs, defStyleAttr), IColorizableWidget {

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

    highlightColor = themeEngine.chanTheme.accentColor
    setLinkTextColor(themeEngine.chanTheme.postLinkColor)

    setEditTextCursorColor(themeEngine.chanTheme)
    setHandlesColors(themeEngine.chanTheme)

    setHintTextColor(
      ColorStateList(
        arrayOf(
          intArrayOf(-android.R.attr.state_focused),
          intArrayOf()
        ),
        intArrayOf(
          ThemeEngine.updateAlphaForColor(Color.LTGRAY, 0.7f),
          Color.LTGRAY,
        )
      )
    )

    setTextColor(
      ColorStateList(
        arrayOf(
          intArrayOf(android.R.attr.state_focused),
          intArrayOf(android.R.attr.state_enabled),
          intArrayOf(-android.R.attr.state_enabled),
          intArrayOf()
        ),
        intArrayOf(
          Color.WHITE,
          ThemeEngine.manipulateColor(Color.WHITE, .9f),
          ThemeEngine.manipulateColor(Color.WHITE, .8f),
          Color.LTGRAY
        )
      )
    )

    ViewCompat.setBackgroundTintList(
      this,
      ColorStateList(
        arrayOf(
          intArrayOf(android.R.attr.state_focused),
          intArrayOf(android.R.attr.state_enabled),
          intArrayOf(-android.R.attr.state_enabled),
          intArrayOf()
        ),
        intArrayOf(
          themeEngine.chanTheme.accentColor,
          themeEngine.chanTheme.defaultColors.controlNormalColor,
          themeEngine.chanTheme.getDisabledTextColor(themeEngine.chanTheme.defaultColors.controlNormalColor),
          themeEngine.chanTheme.textColorHint
        )
      )
    )
  }

}