package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.core.view.ViewCompat
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
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

  override fun onFinishInflate() {
    super.onFinishInflate()

    applyColors()
  }

  override fun applyColors() {
    boxStrokeErrorColor = ColorStateList(
      arrayOf(
        intArrayOf(-android.R.attr.state_enabled),
        intArrayOf(android.R.attr.state_enabled),
      ),
      intArrayOf(
        AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, .8f),
        themeEngine.chanTheme.errorColor
      )
    )

    counterOverflowTextColor = ColorStateList(
      arrayOf(
        intArrayOf(-android.R.attr.state_enabled),
        intArrayOf(android.R.attr.state_enabled),
      ),
      intArrayOf(
        AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, .8f),
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
        AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, .6f),
        themeEngine.chanTheme.accentColor,
        themeEngine.chanTheme.textPrimaryColor
      )
    )

    hintTextColor = colorStateList
    defaultHintTextColor = colorStateList
    counterTextColor = colorStateList
    setBoxBackgroundColorStateList(colorStateList)
    setBoxStrokeColorStateList(colorStateList)

    editText?.let { et ->
      (et as? IColorizableWidget)?.applyColors()

      ViewCompat.setBackgroundTintList(
        et,
        ColorStateList(arrayOf(intArrayOf()), intArrayOf(themeEngine.chanTheme.primaryColor))
      )
    }
  }

}