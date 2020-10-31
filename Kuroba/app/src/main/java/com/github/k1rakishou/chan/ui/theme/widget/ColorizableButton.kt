package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import androidx.core.view.ViewCompat
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.google.android.material.button.MaterialButton
import javax.inject.Inject

class ColorizableButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  val defStyleAttr: Int = R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr), IColorizableWidget {

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

    ViewCompat.setBackgroundTintList(
      this,
      ColorStateList(
        arrayOf(
          intArrayOf(android.R.attr.state_pressed),
          intArrayOf(-android.R.attr.state_pressed),
          intArrayOf(-android.R.attr.state_enabled),
          intArrayOf()
        ),
        intArrayOf(
          AndroidUtils.manipulateColor(themeEngine.chanTheme.accentColor, 1.2f),
          themeEngine.chanTheme.accentColor,
          AndroidUtils.manipulateColor(themeEngine.chanTheme.accentColor, .5f),
          themeEngine.chanTheme.accentColor
        )
      )
    )

    val isDarkColor = AndroidUtils.isDarkColor(themeEngine.chanTheme.accentColor)
    val textColorEnabled = if (isDarkColor) {
      Color.WHITE
    } else {
      Color.BLACK
    }

    val textColorDisabled = if (isDarkColor) {
      Color.LTGRAY
    } else {
      Color.DKGRAY
    }

    setTextColor(
      ColorStateList(
        arrayOf(
          intArrayOf(-android.R.attr.state_enabled),
          intArrayOf()
        ),
        intArrayOf(
          textColorDisabled,
          textColorEnabled
        )
      )
    )
  }
}