package com.github.k1rakishou.chan.ui.text.span

import android.graphics.Color
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.model.data.theme.ChanThemeColorId
import javax.inject.Inject

data class ColorizableBackgroundColorSpan(
  val chanThemeColorId: ChanThemeColorId
) : BackgroundColorSpan(Color.MAGENTA) {

  @Inject
  lateinit var themeEngine: ThemeEngine

  init {
    Chan.inject(this)
  }

  override fun updateDrawState(textPaint: TextPaint) {
    textPaint.color = themeEngine.chanTheme.getColorByColorId(chanThemeColorId)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ColorizableBackgroundColorSpan

    if (chanThemeColorId != other.chanThemeColorId) return false

    return true
  }

  override fun hashCode(): Int {
    return chanThemeColorId.hashCode()
  }


}