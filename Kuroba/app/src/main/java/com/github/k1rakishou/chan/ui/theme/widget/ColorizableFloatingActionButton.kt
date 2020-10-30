package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.google.android.material.floatingactionbutton.FloatingActionButton
import javax.inject.Inject

@Suppress("LeakingThis")
open class ColorizableFloatingActionButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = R.attr.floatingActionButtonStyle
) : FloatingActionButton(context, attrs, defStyleAttr), IColorizableWidget {

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

    backgroundTintList = ColorStateList.valueOf(themeEngine.chanTheme.accentColor)

    val isDarkColor = AndroidUtils.isDarkColor(themeEngine.chanTheme.accentColor)
    if (isDarkColor) {
      drawable.setTint(Color.WHITE)
    } else {
      drawable.setTint(Color.BLACK)
    }
  }
}