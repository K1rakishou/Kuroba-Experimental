package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.core.view.ViewCompat
import com.github.k1rakishou.chan.Chan
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
      Chan.inject(this)
    }
  }

  override fun onFinishInflate() {
    super.onFinishInflate()

    applyColors()
  }

  override fun applyColors() {
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
          AndroidUtils.manipulateColor(themeEngine.chanTheme.accentColor, .5f),
          themeEngine.chanTheme.accentColor
        )
      )
    )

    setTextColor(
      ColorStateList(
        arrayOf(
          intArrayOf(android.R.attr.state_pressed),
          intArrayOf(-android.R.attr.state_enabled),
          intArrayOf()
        ),
        intArrayOf(
          AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, 1.2f),
          AndroidUtils.manipulateColor(themeEngine.chanTheme.textPrimaryColor, .5f),
          themeEngine.chanTheme.textPrimaryColor
        )
      )
    )
  }
}