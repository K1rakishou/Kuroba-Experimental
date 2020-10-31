package com.github.k1rakishou.chan.ui.text.span

import android.graphics.Color
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.model.data.theme.ChanThemeColorId

data class ColorizableBackgroundColorSpan @JvmOverloads constructor(
  private val themeEngine: ThemeEngine,
  val chanThemeColorId: ChanThemeColorId,
  val colorModificationFactor: Float? = null
) : BackgroundColorSpan(Color.MAGENTA) {

  override fun updateDrawState(textPaint: TextPaint) {
    var color = themeEngine.chanTheme.getColorByColorId(chanThemeColorId)

    if (colorModificationFactor != null) {
      color = AndroidUtils.manipulateColor(color, colorModificationFactor)
    }

    textPaint.color = color
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ColorizableBackgroundColorSpan

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