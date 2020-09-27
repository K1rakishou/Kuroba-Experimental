package com.github.k1rakishou.chan.ui.theme

import android.graphics.Typeface

class MockChanTheme(
  override val name: String = "Test theme",
  override val isLightTheme: Boolean = false,
  override val accentColor: Int = 0xff008080L.toInt(),
  override val primaryColor: Int = 0xff282a2eL.toInt(),
  override val secondaryColor: Int = 0xff1d1f21L.toInt(),
  override val textPrimaryColor: Int = 0xFFCECECEL.toInt(),
  override val textSecondaryColor: Int = 0xFF909090L.toInt(),
  override val textColorHint: Int = 0xFF494949L.toInt(),
  override val drawableTintColor: Int = 0xFFFFFFFFL.toInt(),
  override val postHighlightedColor: Int = 0xff444444L.toInt(),
  override val postSavedReplyColor: Int = 0xff5C5C5CL.toInt(),
  override val postSelectedColor: Int = 0x20000000L.toInt(),
  override val postSubjectColor: Int = 0xffb294bbL.toInt(),
  override val postDetailsColor: Int = 0xffc5c8c6L.toInt(),
  override val postNameColor: Int = 0xffc5c8c6L.toInt(),
  override val postInlineQuoteColor: Int = 0xffb5bd68L.toInt(),
  override val postQuoteColor: Int = 0xff5F89ACL.toInt(),
  override val postHighlightQuoteColor: Int = 0xff2b435aL.toInt(),
  override val postLinkColor: Int = 0xff5F89ACL.toInt(),
  override val postSpoilerColor: Int = 0xff000000L.toInt(),
  override val postSpoilerRevealTextColor: Int = 0xffffffffL.toInt(),
  override val dividerColor: Int = 0x1effffffL.toInt(),
  override val bookmarkCounterNotWatchingColor: Int = 0xff33B5E5L.toInt(),
  override val bookmarkCounterHasRepliesColor: Int = 0xffff4444L.toInt(),
  override val bookmarkCounterNormalColor: Int = 0xff898989L.toInt(),
) : ChanTheme() {

  override val mainFont: Typeface
    get() = ROBOTO_MEDIUM
  override val altFont: Typeface
    get() = ROBOTO_CONDENSED

  override fun <T : ChanTheme> copy(
    name: String,
    isLightTheme: Boolean,
    accentColor: Int,
    primaryColor: Int,
    secondaryColor: Int,
    textPrimaryColor: Int,
    textSecondaryColor: Int,
    textColorHint: Int,
    drawableTintColor: Int,
    postHighlightedColor: Int,
    postSavedReplyColor: Int,
    postSelectedColor: Int,
    postSubjectColor: Int,
    postDetailsColor: Int,
    postNameColor: Int,
    postInlineQuoteColor: Int,
    postQuoteColor: Int,
    postHighlightQuoteColor: Int,
    postLinkColor: Int,
    postSpoilerColor: Int,
    postSpoilerRevealTextColor: Int,
    dividerColor: Int,
    bookmarkCounterNotWatchingColor: Int,
    bookmarkCounterHasRepliesColor: Int,
    bookmarkCounterNormalColor: Int,
  ): T {
    return MockChanTheme(
      name = name,
      isLightTheme = isLightTheme,
      accentColor = accentColor,
      primaryColor = primaryColor,
      secondaryColor = secondaryColor,
      textPrimaryColor = textPrimaryColor,
      textSecondaryColor = textSecondaryColor,
      textColorHint = textColorHint,
      drawableTintColor = drawableTintColor,
      postHighlightedColor = postHighlightedColor,
      postSavedReplyColor = postSavedReplyColor,
      postSelectedColor = postSelectedColor,
      postSubjectColor = postSubjectColor,
      postDetailsColor = postDetailsColor,
      postNameColor = postNameColor,
      postInlineQuoteColor = postInlineQuoteColor,
      postQuoteColor = postQuoteColor,
      postHighlightQuoteColor = postHighlightQuoteColor,
      postLinkColor = postLinkColor,
      postSpoilerColor = postSpoilerColor,
      postSpoilerRevealTextColor = postSpoilerRevealTextColor,
      dividerColor = dividerColor,
      bookmarkCounterNotWatchingColor = bookmarkCounterNotWatchingColor,
      bookmarkCounterHasRepliesColor = bookmarkCounterHasRepliesColor,
      bookmarkCounterNormalColor = bookmarkCounterNormalColor,
    ) as T
  }

  companion object {
    private val ROBOTO_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val ROBOTO_CONDENSED = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
  }
}