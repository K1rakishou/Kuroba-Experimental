package com.github.k1rakishou.core_spannable

import android.graphics.Color
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.core_themes.ThemeEngine

data class ColorizableForegroundColorSpan(
  val chanThemeColorId: ChanThemeColorId
) : ForegroundColorSpan(Color.MAGENTA), SpannableDependsOnThemeEngine {
  lateinit var themeEngine: ThemeEngine

  override fun updateDrawState(textPaint: TextPaint) {
    textPaint.color = themeEngine.chanTheme.getColorByColorId(chanThemeColorId)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ColorizableForegroundColorSpan

    if (chanThemeColorId != other.chanThemeColorId) return false

    return true
  }

  override fun hashCode(): Int {
    return chanThemeColorId.hashCode()
  }

}