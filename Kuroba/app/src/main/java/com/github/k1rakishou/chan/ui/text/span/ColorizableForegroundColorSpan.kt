package com.github.k1rakishou.chan.ui.text.span

import android.graphics.Color
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.model.data.theme.ChanThemeColorId

data class ColorizableForegroundColorSpan(
  private val themeEngine: ThemeEngine,
  val chanThemeColorId: ChanThemeColorId
) : ForegroundColorSpan(Color.MAGENTA) {

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