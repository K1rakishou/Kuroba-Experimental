package com.github.k1rakishou.chan.ui.theme

import android.content.Context
import android.graphics.Color

data class DefaultDarkChanTheme(
  override val context: Context,
  override val version: Int = CURRENT_THEME_SCHEMA_VERSION,
  override val name: String = "Default dark theme",
  override val isLightTheme: Boolean = false,
  override val lightStatusBar: Boolean = true,
  override val lightNavBar: Boolean = true,
  override val accentColor: Int = Color.parseColor("#a00038"),
  override val primaryColor: Int = Color.parseColor("#232326"),
  override val backColor: Int = Color.parseColor("#282a2e"),
  override val backColorSecondary: Int = Color.parseColor("#1d1f21"),
  override val errorColor: Int = Color.parseColor("#ff4444"),
  override val textColorPrimary: Int = Color.parseColor("#DEDEDE"),
  override val textColorSecondary: Int = Color.parseColor("#A0A0A0"),
  override val textColorHint: Int = Color.parseColor("#707070"),
  override val postHighlightedColor: Int = Color.parseColor("#545454"),
  override val postSavedReplyColor: Int = Color.parseColor("#4c4152"),
  override val postSubjectColor: Int = Color.parseColor("#b294bb"),
  override val postDetailsColor: Int = Color.parseColor("#c5c8c6"),
  override val postNameColor: Int = Color.parseColor("#93179f"),
  override val postInlineQuoteColor: Int = Color.parseColor("#b5bd68"),
  override val postQuoteColor: Int = Color.parseColor("#5F89AC"),
  override val postHighlightQuoteColor: Int = Color.parseColor("#2b435a"),
  override val postLinkColor: Int = Color.parseColor("#5F89AC"),
  override val postSpoilerColor: Int = Color.parseColor("#000000"),
  override val postSpoilerRevealTextColor: Int = Color.parseColor("#ffffff"),
  override val postUnseenLabelColor: Int = errorColor,
  override val dividerColor: Int = Color.parseColor("#1effffff"),
  override val bookmarkCounterNotWatchingColor: Int = Color.parseColor("#898989"),
  override val bookmarkCounterHasRepliesColor: Int = Color.parseColor("#ff4444"),
  override val bookmarkCounterNormalColor: Int = Color.parseColor("#33B5E5"),
) : ChanTheme()