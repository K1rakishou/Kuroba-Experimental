package com.github.k1rakishou.core_themes

import android.graphics.Color

data class DefaultDarkTheme(
  override val name: String = "Kuroneko",
  override val isLightTheme: Boolean = false,
  override val lightStatusBar: Boolean = true,
  override val lightNavBar: Boolean = true,
  override val accentColor: Int = Color.parseColor("#e0224e"),
  override val primaryColor: Int = Color.parseColor("#090909"),
  override val backColor: Int = Color.parseColor("#212121"),
  override val backColorSecondary: Int = Color.parseColor("#171717"),
  override val errorColor: Int = Color.parseColor("#ff4444"),
  override val textColorPrimary: Int = Color.parseColor("#aeaed6"),
  override val textColorSecondary: Int = Color.parseColor("#8c8ca1"),
  override val textColorHint: Int = Color.parseColor("#7b7b85"),
  override val postHighlightedColor: Int = Color.parseColor("#60947383"),
  override val postSavedReplyColor: Int = Color.parseColor("#6078616c"),
  override val postSubjectColor: Int = Color.parseColor("#d5a6bd"),
  override val postDetailsColor: Int = textColorHint,
  override val postNameColor: Int = Color.parseColor("#996878"),
  override val postInlineQuoteColor: Int = Color.parseColor("#794e94"),
  override val postQuoteColor: Int = Color.parseColor("#ab4d63"),
  override val postHighlightQuoteColor: Int = Color.parseColor("#612c38"),
  override val postLinkColor: Int = Color.parseColor("#ab4d7e"),
  override val postSpoilerColor: Int = Color.parseColor("#000000"),
  override val postSpoilerRevealTextColor: Int = Color.parseColor("#ffffff"),
  override val postUnseenLabelColor: Int = Color.parseColor("#bf3232"),
  override val dividerColor: Int = Color.parseColor("#1effffff"),
  override val bookmarkCounterNotWatchingColor: Int = Color.parseColor("#898989"),
  override val bookmarkCounterHasRepliesColor: Int = Color.parseColor("#ff4444"),
  override val bookmarkCounterNormalColor: Int = Color.parseColor("#33B5E5"),
) : ChanTheme() {

  override fun fullCopy(): ChanTheme {
    return copy()
  }

}