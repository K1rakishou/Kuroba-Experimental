package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.IColorizableWidget
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

class ColorizableDivider @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  val defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), IColorizableWidget {

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

    setBackgroundColor(themeEngine.chanTheme.dividerColor)
  }

}