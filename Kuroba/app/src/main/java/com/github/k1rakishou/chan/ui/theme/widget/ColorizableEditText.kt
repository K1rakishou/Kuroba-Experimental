package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.ViewUtils.setEditTextCursorColor
import com.github.k1rakishou.chan.utils.ViewUtils.setHandlesColors
import com.github.k1rakishou.core_themes.IColorizableWidget
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.manipulateColor
import com.google.android.material.textfield.TextInputEditText
import javax.inject.Inject


open class ColorizableEditText @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = R.attr.editTextStyle
) : TextInputEditText(context, attrs, defStyleAttr), IColorizableWidget {

  @Inject
  protected lateinit var themeEngine: ThemeEngine

  init {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // Hopefully this will fix these crashes:
      // java.lang.RuntimeException:
      //    android.os.TransactionTooLargeException: data parcel size 296380 bytes
      importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

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

    highlightColor = ColorUtils.setAlphaComponent(themeEngine.chanTheme.accentColor, 0x66)
    setLinkTextColor(themeEngine.chanTheme.postLinkColor)

    setEditTextCursorColor(themeEngine.chanTheme)
    setHandlesColors(themeEngine.chanTheme)

    setHintTextColor(
      ColorStateList(
        arrayOf(
          intArrayOf(-android.R.attr.state_focused),
          intArrayOf()
        ),
        intArrayOf(
          ThemeEngine.updateAlphaForColor(themeEngine.chanTheme.textColorHint, 0.7f),
          themeEngine.chanTheme.textColorHint,
        )
      )
    )

    setTextColor(
      ColorStateList(
        arrayOf(
          intArrayOf(android.R.attr.state_focused),
          intArrayOf(android.R.attr.state_enabled),
          intArrayOf(-android.R.attr.state_enabled),
          intArrayOf()
        ),
        intArrayOf(
          manipulateColor(themeEngine.chanTheme.textColorPrimary, 1.2f),
          themeEngine.chanTheme.textColorPrimary,
          manipulateColor(themeEngine.chanTheme.textColorPrimary, .8f),
          themeEngine.chanTheme.textColorHint
        )
      )
    )

    ViewCompat.setBackgroundTintList(
      this,
      ColorStateList(
        arrayOf(
          intArrayOf(android.R.attr.state_focused),
          intArrayOf(android.R.attr.state_enabled),
          intArrayOf(-android.R.attr.state_enabled),
          intArrayOf()
        ),
        intArrayOf(
          themeEngine.chanTheme.accentColor,
          themeEngine.chanTheme.defaultColors.controlNormalColor,
          themeEngine.chanTheme.getDisabledTextColor(themeEngine.chanTheme.defaultColors.controlNormalColor),
          themeEngine.chanTheme.textColorHint
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

}