package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
import javax.inject.Inject

class ColorizableScrollView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0
) : ScrollView(context, attributeSet, defStyle), IColorizableWidget {

  @Inject
  lateinit var themeEngine: ThemeEngine

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

    setBackgroundColor(themeEngine.chanTheme.backColor)
  }

}