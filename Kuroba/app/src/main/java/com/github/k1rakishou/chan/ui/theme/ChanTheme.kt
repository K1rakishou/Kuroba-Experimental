package com.github.k1rakishou.chan.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.model.data.theme.ChanThemeColorId

@SuppressLint("ResourceType")
abstract class ChanTheme {
  abstract val context: Context
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
  abstract val postHighlightedColor: Int
  abstract val postSavedReplyColor: Int
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

  val defaultColors by lazy { loadDefaultColors() }

  private fun loadDefaultColors(): DefaultColors {
    val controlNormalColor = if (isLightTheme) {
      CONTROL_LIGHT_COLOR
    } else {
      CONTROL_DARK_COLOR
    }

    val disabledControlAlpha = (255f * .4f).toInt()

    return DefaultColors(disabledControlAlpha, controlNormalColor)
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
    postHighlightedColor: Int,
    postSavedReplyColor: Int,
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
    return DefaultDarkChanTheme(
      context = context,
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
      postHighlightedColor = postHighlightedColor,
      postSavedReplyColor = postSavedReplyColor,
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

  fun getDisabledTextColor(color: Int): Int {
    return if (isLightTheme) {
      AndroidUtils.manipulateColor(color, 1.3f)
    } else {
      AndroidUtils.manipulateColor(color, .7f)
    }
  }

  fun getControlDisabledColor(color: Int): Int {
    return ColorStateList.valueOf(color)
      .withAlpha(defaultColors.disabledControlAlpha)
      .defaultColor
  }

  fun getColorByColorId(chanThemeColorId: ChanThemeColorId): Int {
    return when (chanThemeColorId) {
      ChanThemeColorId.PostSubjectColor -> postSubjectColor
      ChanThemeColorId.PostNameColor -> postNameColor
      ChanThemeColorId.AccentColor -> accentColor
      ChanThemeColorId.PostInlineQuoteColor -> postInlineQuoteColor
      ChanThemeColorId.PostQuoteColor -> postQuoteColor
      ChanThemeColorId.BackColorSecondary -> backColorSecondary
      ChanThemeColorId.PostLinkColor -> postLinkColor
    }
  }

  data class DefaultColors(
    val disabledControlAlpha: Int,
    val controlNormalColor: Int
  ) {

    val disabledControlAlphaFloat: Float
      get() = disabledControlAlpha.toFloat() / MAX_ALPHA_FLOAT

  }

  companion object {
    const val CURRENT_THEME_SCHEMA_VERSION = 1

    private val ROBOTO_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val ROBOTO_CONDENSED = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    private val CONTROL_LIGHT_COLOR = Color.parseColor("#FFAAAAAA")
    private val CONTROL_DARK_COLOR = Color.parseColor("#FFCCCCCC")

    private const val MAX_ALPHA_FLOAT = 255f
  }
}