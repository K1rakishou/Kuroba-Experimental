package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.IColorizableWidget
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.isDarkColor
import com.google.android.material.floatingactionbutton.FloatingActionButton
import javax.inject.Inject

@Suppress("LeakingThis")
open class ColorizableFloatingActionButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = com.google.android.material.R.attr.floatingActionButtonStyle
) : FloatingActionButton(context, attrs, defStyleAttr), IColorizableWidget {

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

  override fun setImageResource(resId: Int) {
    super.setImageResource(resId)
    updateDrawableTint()
  }

  override fun applyColors() {
    if (isInEditMode) {
      return
    }

    backgroundTintList = ColorStateList.valueOf(themeEngine.chanTheme.accentColor)

    updateDrawableTint()
  }

  private fun updateDrawableTint() {
    val isDarkColor = isDarkColor(themeEngine.chanTheme.accentColor)
    if (isDarkColor) {
      drawable.setTint(Color.WHITE)
    } else {
      drawable.setTint(Color.BLACK)
    }
  }
}