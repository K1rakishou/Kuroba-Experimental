package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import androidx.core.view.ViewCompat
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.IColorizableWidget
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.isDarkColor
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.manipulateColor
import com.google.android.material.button.MaterialButton
import javax.inject.Inject

class ColorizableButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  val defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr), IColorizableWidget {

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
          manipulateColor(themeEngine.chanTheme.accentColor, 1.2f),
          themeEngine.chanTheme.accentColor,
          manipulateColor(themeEngine.chanTheme.accentColor, .5f),
          themeEngine.chanTheme.accentColor
        )
      )
    )

    val isDarkColor = isDarkColor(themeEngine.chanTheme.accentColor)
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