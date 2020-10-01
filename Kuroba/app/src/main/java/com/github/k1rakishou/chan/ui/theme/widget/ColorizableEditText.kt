package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.core.view.ViewCompat
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.ViewUtils.setEditTextCursorColor
import com.github.k1rakishou.chan.utils.ViewUtils.setHandlesColors
import com.google.android.material.textfield.TextInputEditText
import javax.inject.Inject


open class ColorizableEditText @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = R.attr.editTextStyle
) : TextInputEditText(context, attrs, defStyleAttr), IColorizableWidget {

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

    highlightColor = themeEngine.chanTheme.accentColor
    setLinkTextColor(themeEngine.chanTheme.postLinkColor)

    setEditTextCursorColor(themeEngine)
    setHandlesColors(themeEngine)

    setHintTextColor(
      ColorStateList(
        arrayOf(
          intArrayOf(-android.R.attr.state_focused),
          intArrayOf()
        ),
        intArrayOf(
          themeEngine.chanTheme.textColorHint,
          AndroidUtils.manipulateColor(themeEngine.chanTheme.textColorHint, 1.2f),
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
          AndroidUtils.manipulateColor(themeEngine.chanTheme.textColorPrimary, 1.2f),
          themeEngine.chanTheme.textColorPrimary,
          AndroidUtils.manipulateColor(themeEngine.chanTheme.textColorPrimary, .8f),
          themeEngine.chanTheme.textColorHint
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
          AndroidUtils.manipulateColor(themeEngine.chanTheme.accentColor, 1.2f),
          themeEngine.chanTheme.defaultColors.controlNormalColor,
          themeEngine.chanTheme.getDisabledTextColor(themeEngine.chanTheme.defaultColors.controlNormalColor),
          themeEngine.chanTheme.textColorHint
        )
      )
    )
  }

}