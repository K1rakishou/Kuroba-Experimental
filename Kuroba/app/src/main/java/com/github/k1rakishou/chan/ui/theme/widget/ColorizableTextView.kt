package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.ViewUtils.setHandlesColors
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_themes.IColorizableWidget
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.android.material.textview.MaterialTextView
import javax.inject.Inject

class ColorizableTextView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  val defStyleAttr: Int = android.R.attr.textViewStyle
) : MaterialTextView(context, attrs, defStyleAttr), IColorizableWidget {

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

    highlightColor = themeEngine.chanTheme.accentColor
    setLinkTextColor(themeEngine.chanTheme.postLinkColor)
    setHandlesColors(themeEngine.chanTheme)

    setTextColor(
      ColorStateList(
        arrayOf(
          intArrayOf(-android.R.attr.state_enabled),
          intArrayOf()
        ),
        intArrayOf(
          themeEngine.chanTheme.getDisabledTextColor(themeEngine.chanTheme.textColorPrimary),
          themeEngine.chanTheme.textColorPrimary,
        )
      )
    )
  }

  override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
    try {
      super.onFocusChanged(focused, direction, previouslyFocusedRect)
    } catch (ignored: IndexOutOfBoundsException) {
      // java.lang.IndexOutOfBoundsException: setSpan (-1 ... -1) starts before 0
    }
  }

  override fun setText(text: CharSequence?, type: BufferType?) {
    try {
      super.setText(text, type)
    } catch (error: IllegalArgumentException) {
      throw IllegalAccessException("Exception=${error.errorMessageOrClassName()}, viewInfo=${this}, text=${this.text}")
    }
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    try {
      return super.onKeyUp(keyCode, event)
    } catch (error: IllegalStateException) {
      throw IllegalAccessException("Exception=${error.errorMessageOrClassName()}, viewInfo=${this}")
    }
  }
}