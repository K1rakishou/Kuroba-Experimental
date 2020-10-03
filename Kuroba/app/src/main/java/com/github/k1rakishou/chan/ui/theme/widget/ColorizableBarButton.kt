package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import androidx.core.view.ViewCompat
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.google.android.material.button.MaterialButton
import javax.inject.Inject

/**
 * Button with transparent background and accent color as text color
 * */
class ColorizableBarButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  val defStyleAttr: Int = R.attr.buttonBarButtonStyle
) : MaterialButton(context, attrs, defStyleAttr), IColorizableWidget {

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

    ViewCompat.setBackgroundTintList(
      this,
      ColorStateList(
        arrayOf(
          intArrayOf(android.R.attr.state_pressed),
          intArrayOf(-android.R.attr.state_enabled),
          intArrayOf()
        ),
        intArrayOf(
          AndroidUtils.manipulateColor(themeEngine.chanTheme.accentColor, 1.2f),
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
          AndroidUtils.manipulateColor(themeEngine.chanTheme.textColorPrimary, 1.2f),
          themeEngine.chanTheme.getDisabledTextColor(themeEngine.chanTheme.textColorPrimary),
          themeEngine.chanTheme.textColorPrimary
        )
      )
    )
  }

}