package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.ProgressBar
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.IColorizableWidget
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

class ColorizableProgressBar @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  val defStyleAttr: Int = android.R.attr.progressBarStyle
) : ProgressBar(context, attrs, defStyleAttr), IColorizableWidget {

  @Inject
  lateinit var themeEngine: ThemeEngine

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

    progressTintList = ColorStateList.valueOf(themeEngine.chanTheme.accentColor)
    indeterminateTintList = ColorStateList.valueOf(themeEngine.chanTheme.accentColor)
  }

}