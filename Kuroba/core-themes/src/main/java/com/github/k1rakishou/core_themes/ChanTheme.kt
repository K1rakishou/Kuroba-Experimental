package com.github.k1rakishou.core_themes

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Typeface
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CheckboxColors
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.SliderColors
import androidx.compose.material.SliderDefaults
import androidx.compose.material.SliderDefaults.DisabledTickAlpha
import androidx.compose.material.SliderDefaults.InactiveTrackAlpha
import androidx.compose.material.SliderDefaults.TickAlpha
import androidx.compose.material.SwitchColors
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.manipulateColor

@SuppressLint("ResourceType")
open class ChanTheme(
  // Don't forget to update ThemeParser's gson when this class changes !!!
  val name: String,
  val isLightTheme: Boolean,
  val lightStatusBar: Boolean,
  val lightNavBar: Boolean,
  val accentColor: Int,
  val primaryColor: Int,
  val backColor: Int,
  val backColorSecondary: Int,
  val errorColor: Int,
  val textColorPrimary: Int,
  val textColorSecondary: Int,
  val textColorHint: Int,
  val postHighlightedColor: Int,
  val postSavedReplyColor: Int,
  val postSubjectColor: Int,
  val postDetailsColor: Int,
  val postNameColor: Int,
  val postInlineQuoteColor: Int,
  val postQuoteColor: Int,
  val postHighlightQuoteColor: Int,
  val postLinkColor: Int,
  val postSpoilerColor: Int,
  val postSpoilerRevealTextColor: Int,
  val postUnseenLabelColor: Int,
  val dividerColor: Int,
  val bookmarkCounterNotWatchingColor: Int,
  val bookmarkCounterHasRepliesColor: Int,
  val bookmarkCounterNormalColor: Int,
  val scrollbarTrackColor: Int = ChanTheme.DefaultScrollbarTrackColor,
  val scrollbarThumbColorNormal: Int = ChanTheme.DefaultScrollbarThumbColorNormal,
  val scrollbarThumbColorDragged: Int = accentColor,
) {

  open fun copyTheme(
    name: String = this.name,
    isLightTheme: Boolean = this.isLightTheme,
    lightStatusBar: Boolean = this.lightStatusBar,
    lightNavBar: Boolean = this.lightNavBar,
    accentColor: Int = this.accentColor,
    primaryColor: Int = this.primaryColor,
    backColor: Int = this.backColor,
    backColorSecondary: Int = this.backColorSecondary,
    errorColor: Int = this.errorColor,
    textColorPrimary: Int = this.textColorPrimary,
    textColorSecondary: Int = this.textColorSecondary,
    textColorHint: Int = this.textColorHint,
    postHighlightedColor: Int = this.postHighlightedColor,
    postSavedReplyColor: Int = this.postSavedReplyColor,
    postSubjectColor: Int = this.postSubjectColor,
    postDetailsColor: Int = this.postDetailsColor,
    postNameColor: Int = this.postNameColor,
    postInlineQuoteColor: Int = this.postInlineQuoteColor,
    postQuoteColor: Int = this.postQuoteColor,
    postHighlightQuoteColor: Int = this.postHighlightQuoteColor,
    postLinkColor: Int = this.postLinkColor,
    postSpoilerColor: Int = this.postSpoilerColor,
    postSpoilerRevealTextColor: Int = this.postSpoilerRevealTextColor,
    postUnseenLabelColor: Int = this.postUnseenLabelColor,
    dividerColor: Int = this.dividerColor,
    bookmarkCounterNotWatchingColor: Int = this.bookmarkCounterNotWatchingColor,
    bookmarkCounterHasRepliesColor: Int = this.bookmarkCounterHasRepliesColor,
    bookmarkCounterNormalColor: Int = this.bookmarkCounterNormalColor,
    scrollbarTrackColor: Int = this.scrollbarTrackColor,
    scrollbarThumbColorNormal: Int = this.scrollbarThumbColorNormal,
    scrollbarThumbColorDragged: Int = this.scrollbarThumbColorDragged,
  ): ChanTheme {
    return ChanTheme(
      name = name,
      isLightTheme = isLightTheme,
      lightStatusBar = lightStatusBar,
      lightNavBar = lightNavBar,
      accentColor = accentColor,
      primaryColor = primaryColor,
      backColor = backColor,
      backColorSecondary = backColorSecondary,
      errorColor = errorColor,
      textColorPrimary = textColorPrimary,
      textColorSecondary = textColorSecondary,
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
      scrollbarTrackColor = scrollbarTrackColor,
      scrollbarThumbColorNormal = scrollbarThumbColorNormal,
      scrollbarThumbColorDragged = scrollbarThumbColorDragged
    )
  }

  val isDarkTheme: Boolean
    get() = !isLightTheme

  val isBackColorDark: Boolean
    get() = ThemeEngine.isDarkColor(backColor)
  val isBackColorLight: Boolean
    get() = !isBackColorDark

  val accentColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(accentColor) }
  val primaryColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(primaryColor) }
  val backColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(backColor) }
  val backColorSecondaryCompose by lazy(LazyThreadSafetyMode.NONE) { Color(backColorSecondary) }
  val textColorPrimaryCompose by lazy(LazyThreadSafetyMode.NONE) { Color(textColorPrimary) }
  val textColorSecondaryCompose by lazy(LazyThreadSafetyMode.NONE) { Color(textColorSecondary) }
  val textColorHintCompose by lazy(LazyThreadSafetyMode.NONE) { Color(textColorHint) }
  val errorColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(errorColor) }
  val dividerColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(dividerColor) }
  val postSubjectColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(postSubjectColor) }
  val postHighlightedColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(postHighlightedColor) }
  val bookmarkCounterNotWatchingColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(bookmarkCounterNotWatchingColor) }
  val bookmarkCounterHasRepliesColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(bookmarkCounterHasRepliesColor) }
  val bookmarkCounterNormalColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(bookmarkCounterNormalColor) }
  val postLinkColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(postLinkColor) }
  val postInlineQuoteColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(postInlineQuoteColor) }
  val postQuoteColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(postQuoteColor) }
  val scrollbarTrackColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(scrollbarTrackColor) }
  val scrollbarThumbColorNormalCompose by lazy(LazyThreadSafetyMode.NONE) { Color(scrollbarThumbColorNormal) }
  val scrollbarThumbColorDraggedCompose by lazy(LazyThreadSafetyMode.NONE) { Color(scrollbarThumbColorDragged) }

  val selectedOnBackColor: Color
    get() {
      return if (ThemeEngine.isDarkColor(backColor)) {
        Color(0xff909090)
      } else {
        Color(0xff855a5a)
      }
    }

  val toolbarBackgroundComposeColor: Color
    get() = primaryColorCompose

  val onToolbarBackgroundComposeColor: Color
    get() {
      return if (ThemeEngine.isDarkColor(toolbarBackgroundComposeColor)) {
        Color.White
      } else {
        Color.Black
      }
    }

  val colorError = Color.Red
  val colorWarning = Color(0xFFFFA500L)
  val colorInfo = Color.White

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

    return DefaultColors(
      disabledControlAlpha = disabledControlAlpha,
      controlNormalColor = controlNormalColor,
      controlNormalColorCompose = Color(controlNormalColor)
    )
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
      ChanThemeColorId.BackColorSecondary -> backColorSecondary
      ChanThemeColorId.PostLinkColor -> postLinkColor
      ChanThemeColorId.TextColorPrimary -> textColorPrimary
    }
  }

  @Composable
  fun textFieldColors(): TextFieldColors {
    val disabledAlpha = ContentAlpha.disabled

    val backColorDisabled = remember(key1 = backColorCompose) { backColorCompose.copy(alpha = disabledAlpha) }
    val iconColor = remember(key1 = backColorCompose) { backColorCompose.copy(alpha = TextFieldDefaults.IconOpacity) }

    return TextFieldDefaults.outlinedTextFieldColors(
      textColor = textColorPrimaryCompose,
      disabledTextColor = textColorPrimaryCompose.copy(ContentAlpha.disabled),
      backgroundColor = Color.Transparent,
      cursorColor = accentColorCompose,
      focusedBorderColor = accentColorCompose.copy(alpha = ContentAlpha.high),
      unfocusedBorderColor = defaultColors.controlNormalColorCompose.copy(alpha = ContentAlpha.medium),
      disabledBorderColor = defaultColors.controlNormalColorCompose.copy(alpha = ContentAlpha.disabled),
      focusedLabelColor = accentColorCompose.copy(alpha = ContentAlpha.high),
      unfocusedLabelColor = defaultColors.controlNormalColorCompose.copy(alpha = ContentAlpha.medium),
      disabledLabelColor = defaultColors.controlNormalColorCompose.copy(ContentAlpha.disabled),
      leadingIconColor = iconColor,
      disabledLeadingIconColor = iconColor.copy(alpha = ContentAlpha.disabled),
      errorLeadingIconColor = iconColor,
      trailingIconColor = iconColor,
      disabledTrailingIconColor = iconColor.copy(alpha = ContentAlpha.disabled),
      placeholderColor = backColorDisabled.copy(ContentAlpha.medium),
      disabledPlaceholderColor = backColorDisabled.copy(ContentAlpha.disabled),
      errorBorderColor = errorColorCompose,
      errorTrailingIconColor = errorColorCompose,
      errorCursorColor = errorColorCompose,
      errorLabelColor = errorColorCompose,
    )
  }

  @Composable
  fun checkBoxColors(): CheckboxColors {
    return CheckboxDefaults.colors(
      checkedColor = accentColorCompose,
      uncheckedColor = accentColorCompose.copy(alpha = 0.6f),
      checkmarkColor = backColorCompose,
      disabledColor = accentColorCompose.copy(alpha = ContentAlpha.disabled),
      disabledIndeterminateColor = accentColorCompose.copy(alpha = ContentAlpha.disabled)
    )
  }

  @Composable
  fun buttonColors(): ButtonColors {
    return ButtonDefaults.buttonColors(
      backgroundColor = accentColorCompose,
      contentColor = backColorCompose,
      disabledBackgroundColor = accentColorCompose.copy(alpha = ContentAlpha.disabled),
      disabledContentColor = backColorCompose.copy(alpha = ContentAlpha.disabled)
    )
  }

  @Composable
  fun barButtonColors(): ButtonColors {
    return ButtonDefaults.buttonColors(
      backgroundColor = Color.Unspecified,
      contentColor = accentColorCompose,
      disabledBackgroundColor = Color.Unspecified,
      disabledContentColor = accentColorCompose.copy(alpha = ContentAlpha.disabled)
    )
  }

  @Composable
  fun sliderColors(): SliderColors {
    val disabledThumbColor = accentColorCompose.copy(alpha = ContentAlpha.disabled)
    val disabledActiveTrackColor = disabledThumbColor.copy(alpha = SliderDefaults.DisabledActiveTrackAlpha)
    val disabledInactiveTrackColor = disabledActiveTrackColor.copy(alpha = SliderDefaults.DisabledInactiveTrackAlpha)
    val activeTickColor = contentColorFor(accentColorCompose).copy(alpha = TickAlpha)

    return SliderDefaults.colors(
      thumbColor = accentColorCompose,
      disabledThumbColor = disabledThumbColor,
      activeTrackColor = accentColorCompose,
      inactiveTrackColor = accentColorCompose.copy(alpha = InactiveTrackAlpha),
      disabledActiveTrackColor = disabledActiveTrackColor,
      disabledInactiveTrackColor = disabledInactiveTrackColor,
      activeTickColor = activeTickColor,
      inactiveTickColor = accentColorCompose.copy(alpha = TickAlpha),
      disabledActiveTickColor = activeTickColor.copy(alpha = DisabledTickAlpha),
      disabledInactiveTickColor = disabledInactiveTrackColor.copy(alpha = DisabledTickAlpha)
    )
  }

  @Composable
  fun switchColors(): SwitchColors {
    val checkedThumbColor = accentColorCompose
    val uncheckedThumbColor = remember(key1 = defaultColors.controlNormalColorCompose) {
      manipulateColor(defaultColors.controlNormalColorCompose, 1.2f)
    }
    val uncheckedTrackColor = remember(key1 = defaultColors.controlNormalColorCompose) {
      manipulateColor(defaultColors.controlNormalColorCompose, .6f)
    }

    return SwitchDefaults.colors(
      checkedThumbColor = checkedThumbColor,
      checkedTrackColor = checkedThumbColor,
      checkedTrackAlpha = 0.54f,
      uncheckedThumbColor = uncheckedThumbColor,
      uncheckedTrackColor = uncheckedTrackColor,
      uncheckedTrackAlpha = 0.38f,
      disabledCheckedThumbColor = checkedThumbColor
        .copy(alpha = ContentAlpha.disabled)
        .compositeOver(uncheckedThumbColor),
      disabledCheckedTrackColor = checkedThumbColor
        .copy(alpha = ContentAlpha.disabled)
        .compositeOver(uncheckedThumbColor),
      disabledUncheckedThumbColor = uncheckedThumbColor
        .copy(alpha = ContentAlpha.disabled)
        .compositeOver(uncheckedThumbColor),
      disabledUncheckedTrackColor = uncheckedThumbColor
        .copy(alpha = ContentAlpha.disabled)
        .compositeOver(uncheckedThumbColor)
    )
  }

  fun calculateTextColor(
    text: String
  ): Color {
    // Stolen from the 4chan extension
    val hash: Int = text.hashCode()

    val r = hash shr 24 and 0xff
    val g = hash shr 16 and 0xff
    val b = hash shr 8 and 0xff
    val textColor = (0xff shl 24) + (r shl 16) + (g shl 8) + b

    val textColorHSL = ThemeEngine.colorToHsl(textColor)

    // Make the posterId text color darker if it's too light and the current theme's back color is
    // also light and vice versa
    if (isBackColorDark && textColorHSL.lightness < 0.5) {
      textColorHSL.lightness = .7f
    } else if (isBackColorLight && textColorHSL.lightness > 0.5) {
      textColorHSL.lightness = .3f
    }

    return Color(ThemeEngine.hslToColor(textColorHSL))
  }

  fun overrideForSearchInputOnToolbar(newAccentColor: Color, newTextColorPrimary: Color): ChanTheme {
    return copyTheme(
      accentColor = newAccentColor.toArgb(),
      textColorPrimary = newTextColorPrimary.toArgb()
    )
  }

  data class DefaultColors(
    val disabledControlAlpha: Int,
    val controlNormalColor: Int,
    val controlNormalColorCompose: Color,
  ) {

    val disabledControlAlphaFloat: Float
      get() = disabledControlAlpha.toFloat() / MAX_ALPHA_FLOAT

  }

  companion object {
    private val ROBOTO_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val ROBOTO_CONDENSED = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    private const val CONTROL_LIGHT_COLOR = 0xFFAAAAAAL.toInt()
    private const val CONTROL_DARK_COLOR = 0xFFCCCCCCL.toInt()

    private const val MAX_ALPHA_FLOAT = 255f

    @Deprecated("Automatic backColorSecondary calculation is deprecated! Use an actual color instead.")
    fun backColorSecondaryDeprecated(backColor: Int): Int {
      return manipulateColor(backColor, .7f)
    }

    val DefaultScrollbarTrackColor = android.graphics.Color.parseColor("#ffbababa")
    val DefaultScrollbarThumbColorNormal = android.graphics.Color.parseColor("#ff262626")
  }
}