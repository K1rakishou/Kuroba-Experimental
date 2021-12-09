package com.github.k1rakishou.core_spannable

import android.graphics.Color
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.manipulateColor

data class BackgroundColorIdSpan @JvmOverloads constructor(
  val chanThemeColorId: ChanThemeColorId,
  val colorModificationFactor: Float? = null
) : BackgroundColorSpan(Color.MAGENTA) {
  private val themeEngine by lazy { SpannableModuleInjector.themeEngine }

  override fun updateDrawState(textPaint: TextPaint) {
    var color = themeEngine.chanTheme.getColorByColorId(chanThemeColorId)

    if (colorModificationFactor != null) {
      color = manipulateColor(color, colorModificationFactor)
    }

    textPaint.bgColor = color
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as BackgroundColorIdSpan

    if (chanThemeColorId != other.chanThemeColorId) return false
    if (colorModificationFactor != other.colorModificationFactor) return false

    return true
  }

  override fun hashCode(): Int {
    var result = chanThemeColorId.hashCode()
    result = 31 * result + (colorModificationFactor?.hashCode() ?: 0)
    return result
  }


}