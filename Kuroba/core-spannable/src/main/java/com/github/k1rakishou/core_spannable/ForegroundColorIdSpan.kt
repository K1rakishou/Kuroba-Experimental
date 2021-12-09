package com.github.k1rakishou.core_spannable

import android.graphics.Color
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import com.github.k1rakishou.core_themes.ChanThemeColorId

data class ForegroundColorIdSpan(
  val chanThemeColorId: ChanThemeColorId
) : ForegroundColorSpan(Color.MAGENTA) {
  private val themeEngine by lazy { SpannableModuleInjector.themeEngine }

  override fun updateDrawState(textPaint: TextPaint) {
    textPaint.color = themeEngine.chanTheme.getColorByColorId(chanThemeColorId)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ForegroundColorIdSpan

    if (chanThemeColorId != other.chanThemeColorId) return false

    return true
  }

  override fun hashCode(): Int {
    return chanThemeColorId.hashCode()
  }

}