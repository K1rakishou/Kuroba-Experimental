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

  override fun onFinishInflate() {
    super.onFinishInflate()

    applyColors()
  }

  override fun applyColors() {
    setHintTextColor(themeEngine.chanTheme.textColorHint)
    highlightColor = themeEngine.chanTheme.accentColor
    setLinkTextColor(themeEngine.chanTheme.postLinkColor)

    setEditTextCursorColor(themeEngine)
    setHandlesColors(themeEngine)

    setTextColor(
      ColorStateList(
        arrayOf(
          intArrayOf(android.R.attr.state_focused),
          intArrayOf(android.R.attr.state_enabled),
          intArrayOf(-android.R.attr.state_enabled),
          intArrayOf()
        ),
        intArrayOf(
          AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, 1.2f),
          themeEngine.chanTheme.textPrimaryColor,
          AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, .8f),
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
          themeEngine.chanTheme.textPrimaryColor,
          AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, .8f),
          themeEngine.chanTheme.textColorHint
        )
      )
    )
  }

}