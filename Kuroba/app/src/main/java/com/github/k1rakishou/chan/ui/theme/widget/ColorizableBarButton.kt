package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import androidx.core.view.ViewCompat
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.IColorizableWidget
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.manipulateColor
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.updateAlphaForColor
import com.google.android.material.button.MaterialButton
import javax.inject.Inject

/**
 * Button with transparent background and accent color as text color
 * */
class ColorizableBarButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  val defStyleAttr: Int = androidx.appcompat.R.attr.buttonBarButtonStyle
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
          intArrayOf(-android.R.attr.state_enabled),
          intArrayOf()
        ),
        intArrayOf(
          updateAlphaForColor(manipulateColor(themeEngine.chanTheme.accentColor, 1.2f), 0.3f),
          Color.TRANSPARENT,
          Color.TRANSPARENT
        )
      )
    )

    rippleColor = ColorStateList.valueOf(themeEngine.chanTheme.accentColor)

    setTextColor(
      ColorStateList(
        arrayOf(
          intArrayOf(android.R.attr.state_pressed),
          intArrayOf(-android.R.attr.state_enabled),
          intArrayOf()
        ),
        intArrayOf(
          manipulateColor(themeEngine.chanTheme.textColorPrimary, 1.2f),
          themeEngine.chanTheme.getDisabledTextColor(themeEngine.chanTheme.textColorPrimary),
          themeEngine.chanTheme.textColorPrimary
        )
      )
    )
  }

}