package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.google.android.material.R
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
      Chan.inject(this)
    }
  }

  override fun onFinishInflate() {
    super.onFinishInflate()

    applyColors()
  }

  override fun applyColors() {
    backgroundTintList = ColorStateList.valueOf(themeEngine.chanTheme.accentColor)
    drawable.setTint(themeEngine.chanTheme.drawableTintColor)
  }
}