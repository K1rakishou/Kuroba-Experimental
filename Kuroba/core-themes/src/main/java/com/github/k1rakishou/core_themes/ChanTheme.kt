package com.github.k1rakishou.core_themes

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.manipulateColor

@SuppressLint("ResourceType")
abstract class ChanTheme {
  // Don't forget to update ThemeParser's gson when this class changes !!!
  abstract val name: String
  abstract val isLightTheme: Boolean
  abstract val lightStatusBar: Boolean
  abstract val lightNavBar: Boolean
  abstract val accentColor: Int
  abstract val primaryColor: Int
  abstract val backColor: Int
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

  val isDarkTheme: Boolean
    get() = !isLightTheme

  val isBackColorDark: Boolean
    get() = ThemeEngine.isDarkColor(backColor)

  open val mainFont: Typeface = ROBOTO_MEDIUM

  val defaultColors by lazy { loadDefaultColors() }
  val defaultBoldTypeface by lazy { Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

  private fun loadDefaultColors(): DefaultColors {
    val controlNormalColor = if (isLightTheme) {
      CONTROL_LIGHT_COLOR
    } else {
      CONTROL_DARK_COLOR
    }

    val disabledControlAlpha = (255f * .4f).toInt()

    return DefaultColors(disabledControlAlpha, controlNormalColor)
  }

  fun getDisabledTextColor(color: Int): Int {
    return if (isLightTheme) {
      manipulateColor(color, 1.3f)
    } else {
      manipulateColor(color, .7f)
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
      ChanThemeColorId.BackColorSecondary -> backColorSecondary()
      ChanThemeColorId.PostLinkColor -> postLinkColor
      ChanThemeColorId.TextColorPrimary -> textColorPrimary
    }
  }

  fun backColorSecondary(): Int {
    return manipulateColor(backColor, .7f)
  }

  data class DefaultColors(
    val disabledControlAlpha: Int,
    val controlNormalColor: Int
  ) {

    val disabledControlAlphaFloat: Float
      get() = disabledControlAlpha.toFloat() / MAX_ALPHA_FLOAT

  }

  companion object {
    private val ROBOTO_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val ROBOTO_CONDENSED = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    private val CONTROL_LIGHT_COLOR = Color.parseColor("#FFAAAAAA")
    private val CONTROL_DARK_COLOR = Color.parseColor("#FFCCCCCC")

    private const val MAX_ALPHA_FLOAT = 255f
  }
}