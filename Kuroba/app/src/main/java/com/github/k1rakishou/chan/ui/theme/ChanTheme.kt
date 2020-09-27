package com.github.k1rakishou.chan.ui.theme

import android.graphics.Color
import android.graphics.Typeface
import com.github.k1rakishou.chan.R

abstract class ChanTheme {
  abstract val name: String
  abstract val isLightTheme: Boolean
  abstract val accentColor: Int
  abstract val primaryColor: Int
  abstract val secondaryColor: Int
  abstract val textPrimaryColor: Int
  abstract val textSecondaryColor: Int
  abstract val textColorHint: Int
  abstract val drawableTintColor: Int

  abstract val postHighlightedColor: Int
  abstract val postSavedReplyColor: Int
  abstract val postSelectedColor: Int
  abstract val postSubjectColor: Int
  abstract val postDetailsColor: Int
  abstract val postNameColor: Int
  abstract val postInlineQuoteColor: Int
  abstract val postQuoteColor: Int
  abstract val postHighlightQuoteColor: Int
  abstract val postLinkColor: Int
  abstract val postSpoilerColor: Int
  abstract val postSpoilerRevealTextColor: Int

  abstract val dividerColor: Int

  abstract val bookmarkCounterNotWatchingColor: Int
  abstract val bookmarkCounterHasRepliesColor: Int
  abstract val bookmarkCounterNormalColor: Int

  abstract val mainFont: Typeface
  abstract val altFont: Typeface

  @JvmField
  val settingsDrawable = ThemeDrawable(R.drawable.ic_settings_white_24dp, 0.54f)
  @JvmField
  val imageDrawable = ThemeDrawable(R.drawable.ic_image_white_24dp, 0.54f)
  @JvmField
  val sendDrawable = ThemeDrawable(R.drawable.ic_send_white_24dp, 0.54f)
  @JvmField
  val clearDrawable = ThemeDrawable(R.drawable.ic_clear_white_24dp, 0.54f)
  @JvmField
  val backDrawable = ThemeDrawable(R.drawable.ic_arrow_back_white_24dp, 0.54f)
  @JvmField
  val doneDrawable = ThemeDrawable(R.drawable.ic_done_white_24dp, 0.54f)
  @JvmField
  val historyDrawable = ThemeDrawable(R.drawable.ic_history_white_24dp, 0.54f)
  @JvmField
  val helpDrawable = ThemeDrawable(R.drawable.ic_help_outline_white_24dp, 0.54f)
  @JvmField
  val refreshDrawable = ThemeDrawable(R.drawable.ic_refresh_white_24dp, 0.54f)

  init {
    if (isLightTheme) {
      settingsDrawable.tint = Color.BLACK
      imageDrawable.tint = Color.BLACK
      sendDrawable.tint = Color.BLACK
      clearDrawable.tint = Color.BLACK
      backDrawable.tint = Color.BLACK
      doneDrawable.tint = Color.BLACK
      historyDrawable.tint = Color.BLACK
      helpDrawable.tint = Color.BLACK
      refreshDrawable.tint = Color.BLACK
    } else {
      settingsDrawable.setAlpha(1f)
      imageDrawable.setAlpha(1f)
      sendDrawable.setAlpha(1f)
      clearDrawable.setAlpha(1f)
      backDrawable.setAlpha(1f)
      doneDrawable.setAlpha(1f)
      historyDrawable.setAlpha(1f)
      helpDrawable.setAlpha(1f)
      refreshDrawable.setAlpha(1f)
    }
  }

  abstract fun <T : ChanTheme> copy(
    name: String = this.name,
    isLightTheme: Boolean = this.isLightTheme,
    accentColor: Int = this.accentColor,
    primaryColor: Int = this.primaryColor,
    secondaryColor: Int = this.secondaryColor,
    textPrimaryColor: Int = this.textPrimaryColor,
    textSecondaryColor: Int = this.textSecondaryColor,
    textColorHint: Int = this.textColorHint,
    drawableTintColor: Int = this.drawableTintColor,
    postHighlightedColor: Int = this.postHighlightedColor,
    postSavedReplyColor: Int = this.postSavedReplyColor,
    postSelectedColor: Int = this.postSelectedColor,
    postSubjectColor: Int = this.postSubjectColor,
    postDetailsColor: Int = this.postDetailsColor,
    postNameColor: Int = this.postNameColor,
    postInlineQuoteColor: Int = this.postInlineQuoteColor,
    postQuoteColor: Int = this.postQuoteColor,
    postHighlightQuoteColor: Int = this.postHighlightQuoteColor,
    postLinkColor: Int = this.postLinkColor,
    postSpoilerColor: Int = this.postSpoilerColor,
    postSpoilerRevealTextColor: Int = this.postSpoilerRevealTextColor,
    dividerColor: Int = this.dividerColor,
    bookmarkCounterNotWatchingColor: Int = this.bookmarkCounterNotWatchingColor,
    bookmarkCounterHasRepliesColor: Int = this.bookmarkCounterHasRepliesColor,
    bookmarkCounterNormalColor: Int = this.bookmarkCounterNormalColor,
  ): T
}