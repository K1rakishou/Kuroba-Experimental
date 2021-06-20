package com.github.k1rakishou.chan.ui.compose

import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.core.text.getSpans
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.ColorizableBackgroundColorSpan
import com.github.k1rakishou.core_spannable.ColorizableForegroundColorSpan
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.core_themes.ThemeEngine

object ComposeHelpers {
  private const val TAG = "ComposeHelpers"

  @OptIn(ExperimentalUnitApi::class)
  fun Spanned.toAnnotatedString(themeEngine: ThemeEngine): AnnotatedString {
    val theme = themeEngine.chanTheme
    val annotatedStringBuilder = AnnotatedString.Builder(this.toString())

    getSpans<CharacterStyle>().forEach { characterStyle ->
      val spanStyle = when (characterStyle) {
        is AbsoluteSizeSpan -> {
          SpanStyle(fontSize = TextUnit(characterStyle.size.toFloat(), TextUnitType.Sp))
        }
        is BackgroundColorSpan -> {
          SpanStyle(background = Color(characterStyle.backgroundColor))
        }
        is ColorizableBackgroundColorSpan -> {
          val color = getColorByColorId(themeEngine, characterStyle.chanThemeColorId, characterStyle.colorModificationFactor)
          SpanStyle(background = color)
        }
        is ForegroundColorSpan -> {
          SpanStyle(color = Color(characterStyle.foregroundColor))
        }
        is ColorizableForegroundColorSpan -> {
          SpanStyle(color = getColorByColorId(themeEngine, characterStyle.chanThemeColorId, null))
        }
        is PostLinkable -> {
          mapPostLinkable(characterStyle, theme)
        }
        else -> null
      }

      if (spanStyle == null) {
        Logger.d(TAG, "Unsupported character style: ${characterStyle::class.java.simpleName}")
        return@forEach
      }

      annotatedStringBuilder.addStyle(spanStyle, getSpanStart(characterStyle), getSpanEnd(characterStyle))
    }

    return annotatedStringBuilder.toAnnotatedString()
  }

  private fun mapPostLinkable(characterStyle: PostLinkable, theme: ChanTheme): SpanStyle {
    var color = Color.Magenta
    var background = Color.Unspecified
    var fontWeight: FontWeight? = null
    var textDecoration: TextDecoration? = null

    when (characterStyle.type) {
      PostLinkable.Type.QUOTE,
      PostLinkable.Type.LINK,
      PostLinkable.Type.THREAD,
      PostLinkable.Type.BOARD,
      PostLinkable.Type.SEARCH,
      PostLinkable.Type.DEAD,
      PostLinkable.Type.ARCHIVE -> {
        if (characterStyle.type == PostLinkable.Type.QUOTE) {
          if (characterStyle.isMarkedNo()) {
            color = Color(theme.postHighlightQuoteColor)
            fontWeight = FontWeight.Bold
          } else {
            color = Color(theme.postQuoteColor)
          }
        } else if (characterStyle.type == PostLinkable.Type.LINK) {
          color = Color(theme.postLinkColor)
        } else {
          color = Color(theme.postQuoteColor)
        }

        if (characterStyle.type == PostLinkable.Type.DEAD) {
          textDecoration = TextDecoration.LineThrough
        } else {
          textDecoration = TextDecoration.Underline
        }
      }
      PostLinkable.Type.SPOILER -> {
        background = Color(theme.postSpoilerColor)
        textDecoration = TextDecoration.None

        if (!characterStyle.isSpoilerVisible) {
          color = Color(theme.postSpoilerColor)
        } else {
          color = Color(theme.postSpoilerRevealTextColor)
        }
      }
    }

    return SpanStyle(color = color, background = background, fontWeight = fontWeight, textDecoration = textDecoration)
  }

  private fun getColorByColorId(
    themeEngine: ThemeEngine,
    chanThemeColorId: ChanThemeColorId,
    factor: Float?
  ): Color {
    var color = themeEngine.chanTheme.getColorByColorId(chanThemeColorId)

    if (factor != null) {
      color = ThemeEngine.manipulateColor(color, factor)
    }

    return Color(color)
  }

}