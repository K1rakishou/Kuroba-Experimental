package com.github.k1rakishou.core_themes

import android.graphics.Color

data class DefaultLightTheme(
  override val name: String = "Default light theme",
  override val isLightTheme: Boolean = true,
  override val lightStatusBar: Boolean = true,
  override val lightNavBar: Boolean = true,
  override val accentColor: Int = Color.parseColor("#af0a0f"),
  override val primaryColor: Int = Color.parseColor("#3c8f52"),
  override val backColor: Int = Color.parseColor("#EEF2FF"),
  override val backColorSecondary: Int = Color.parseColor("#d6daf0"),
  override val errorColor: Int = Color.parseColor("#ff4444"),
  override val textColorPrimary: Int = Color.parseColor("#000000"),
  override val textColorSecondary: Int = Color.parseColor("#6b6b6b"),
  override val textColorHint: Int = Color.parseColor("#8c8c8c"),
  override val postHighlightedColor: Int = Color.parseColor("#60a3a6b5"),
  override val postSavedReplyColor: Int = Color.parseColor("#3b59f5"),
  override val postSubjectColor: Int = Color.parseColor("#0F0C5D"),
  override val postDetailsColor: Int = Color.parseColor("#656565"),
  override val postNameColor: Int = Color.parseColor("#117743"),
  override val postInlineQuoteColor: Int = Color.parseColor("#789922"),
  override val postQuoteColor: Int = Color.parseColor("#dd0000"),
  override val postHighlightQuoteColor: Int = Color.parseColor("#780202"),
  override val postLinkColor: Int = Color.parseColor("#dd0000"),
  override val postSpoilerColor: Int = Color.parseColor("#000000"),
  override val postSpoilerRevealTextColor: Int = Color.parseColor("#ffffff"),
  override val postUnseenLabelColor: Int = errorColor,
  override val dividerColor: Int = Color.parseColor("#20000000"),
  override val bookmarkCounterNotWatchingColor: Int = Color.parseColor("#898989"),
  override val bookmarkCounterHasRepliesColor: Int = Color.parseColor("#ff5744"),
  override val bookmarkCounterNormalColor: Int = Color.parseColor("#6033e5"),
) : ChanTheme() {

  override fun fullCopy(): ChanTheme {
    return copy()
  }

}