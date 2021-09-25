package com.github.k1rakishou.core_themes

import android.graphics.Color

data class HalloweenTheme(
  override val name: String = "Halloween",
  override val isLightTheme: Boolean = false,
  override val lightStatusBar: Boolean = true,
  override val lightNavBar: Boolean = true,
  override val accentColor: Int = Color.parseColor("#C37D35"),
  override val primaryColor: Int = Color.parseColor("#000000"),
  override val backColor: Int = Color.parseColor("#171526"),
  override val backColorSecondary: Int = Color.parseColor("#03001A"),
  override val errorColor: Int = Color.parseColor("#ff0000"),
  override val textColorPrimary: Int = Color.parseColor("#dbd9d9"),
  override val textColorSecondary: Int = Color.parseColor("#b8b8b8"),
  override val textColorHint: Int = Color.parseColor("#878787"),
  override val postHighlightedColor: Int = Color.parseColor("#a0212730"),
  override val postSavedReplyColor: Int = Color.parseColor("#cf8357"),
  override val postSubjectColor: Int = Color.parseColor("#d95c43"),
  override val postDetailsColor: Int = Color.parseColor("#919191"),
  override val postNameColor: Int = Color.parseColor("#919191"),
  override val postInlineQuoteColor: Int = Color.parseColor("#b76d5e"),
  override val postQuoteColor: Int = Color.parseColor("#d77841"),
  override val postHighlightQuoteColor: Int = Color.parseColor("#d77841"),
  override val postLinkColor: Int = Color.parseColor("#d77841"),
  override val postSpoilerColor: Int = Color.parseColor("#01060a"),
  override val postSpoilerRevealTextColor: Int = Color.parseColor("#b8b8b8"),
  override val postUnseenLabelColor: Int = Color.parseColor("#d38d70"),
  override val dividerColor: Int = Color.parseColor("#303438"),
  override val bookmarkCounterNotWatchingColor: Int = Color.parseColor("#919191"),
  override val bookmarkCounterHasRepliesColor: Int = Color.parseColor("#d77841"),
  override val bookmarkCounterNormalColor: Int = Color.parseColor("#b9b9b9"),
) : ChanTheme() {

  override fun fullCopy(): ChanTheme {
    return copy()
  }

}