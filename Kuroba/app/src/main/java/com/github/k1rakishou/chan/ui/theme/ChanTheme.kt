package com.github.k1rakishou.chan.ui.theme

import android.graphics.Color
import android.graphics.Typeface
import com.github.k1rakishou.chan.R

abstract class ChanTheme {
  abstract val version: Int
  abstract val name: String
  abstract val isLightTheme: Boolean
  abstract val lightStatusBar: Boolean
  abstract val lightNavBar: Boolean
  abstract val accentColor: Int
  abstract val primaryColor: Int
  abstract val backColor: Int
  abstract val backColorSecondary: Int
  abstract val errorColor: Int
  abstract val textColorPrimary: Int
  abstract val textColorSecondary: Int
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
  abstract val postUnseenLabelColor: Int
  abstract val dividerColor: Int
  abstract val bookmarkCounterNotWatchingColor: Int
  abstract val bookmarkCounterHasRepliesColor: Int
  abstract val bookmarkCounterNormalColor: Int

  open val mainFont: Typeface = ROBOTO_MEDIUM
  open val altFont: Typeface = ROBOTO_CONDENSED

  @JvmField
  val settingsDrawable = SETTINGS_DRAWABLE
  @JvmField
  val imageDrawable = IMAGE_DRAWABLE
  @JvmField
  val sendDrawable = SEND_DRAWABLE
  @JvmField
  val clearDrawable = CLEAR_DRAWABLE
  @JvmField
  val backDrawable = BACK_DRAWABLE
  @JvmField
  val doneDrawable = DONE_DRAWABLE
  @JvmField
  val historyDrawable = HISTORY_DRAWABLE
  @JvmField
  val helpDrawable = HELP_DRAWABLE
  @JvmField
  val refreshDrawable = REFRESH_DRAWABLE

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

  fun <T : ChanTheme> copy(
    version: Int,
    name: String,
    isLightTheme: Boolean,
    lightStatusBar: Boolean,
    lightNavBar: Boolean,
    accentColor: Int,
    primaryColor: Int,
    backColor: Int,
    backColorSecondary: Int,
    errorColor: Int,
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
    postUnseenLabelColor: Int,
    dividerColor: Int,
    bookmarkCounterNotWatchingColor: Int,
    bookmarkCounterHasRepliesColor: Int,
    bookmarkCounterNormalColor: Int,
  ): T {
    return MockDarkChanTheme(
      version = version,
      name = name,
      isLightTheme = isLightTheme,
      lightStatusBar = lightStatusBar,
      lightNavBar = lightNavBar,
      accentColor = accentColor,
      primaryColor = primaryColor,
      backColor = backColor,
      backColorSecondary = backColorSecondary,
      errorColor = errorColor,
      textColorPrimary = textPrimaryColor,
      textColorSecondary = textSecondaryColor,
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
      postUnseenLabelColor = postUnseenLabelColor,
      dividerColor = dividerColor,
      bookmarkCounterNotWatchingColor = bookmarkCounterNotWatchingColor,
      bookmarkCounterHasRepliesColor = bookmarkCounterHasRepliesColor,
      bookmarkCounterNormalColor = bookmarkCounterNormalColor,
    ) as T
  }

  companion object {
    const val CURRENT_THEME_SCHEMA_VERSION = 1

    @JvmField
    val SETTINGS_DRAWABLE = ThemeDrawable(R.drawable.ic_settings_white_24dp, 0.54f)
    @JvmField
    val IMAGE_DRAWABLE = ThemeDrawable(R.drawable.ic_image_white_24dp, 0.54f)
    @JvmField
    val SEND_DRAWABLE = ThemeDrawable(R.drawable.ic_send_white_24dp, 0.54f)
    @JvmField
    val CLEAR_DRAWABLE = ThemeDrawable(R.drawable.ic_clear_white_24dp, 0.54f)
    @JvmField
    val BACK_DRAWABLE = ThemeDrawable(R.drawable.ic_arrow_back_white_24dp, 0.54f)
    @JvmField
    val DONE_DRAWABLE = ThemeDrawable(R.drawable.ic_done_white_24dp, 0.54f)
    @JvmField
    val HISTORY_DRAWABLE = ThemeDrawable(R.drawable.ic_history_white_24dp, 0.54f)
    @JvmField
    val HELP_DRAWABLE = ThemeDrawable(R.drawable.ic_help_outline_white_24dp, 0.54f)
    @JvmField
    val REFRESH_DRAWABLE = ThemeDrawable(R.drawable.ic_refresh_white_24dp, 0.54f)

    private val ROBOTO_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val ROBOTO_CONDENSED = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
  }
}