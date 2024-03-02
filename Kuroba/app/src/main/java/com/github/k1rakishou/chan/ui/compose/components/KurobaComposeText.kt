package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

@Composable
fun KurobaComposeText(
  text: String,
  modifier: Modifier = Modifier,
  color: Color? = null,
  fontSize: TextUnit = TextUnit.Unspecified,
  fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  enabled: Boolean = true,
  textAlign: TextAlign? = null,
  inlineContent: Map<String, InlineTextContent> = mapOf(),
  onTextLayout: (TextLayoutResult) -> Unit = {},
) {
  KurobaComposeText(
    text = AnnotatedString(text),
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    fontWeight = fontWeight,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = softWrap,
    enabled = enabled,
    textAlign = textAlign,
    inlineContent = inlineContent,
    onTextLayout = onTextLayout
  )
}

@Composable
fun KurobaComposeText(
  text: AnnotatedString,
  modifier: Modifier = Modifier,
  color: Color? = null,
  fontSize: TextUnit = TextUnit.Unspecified,
  fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  enabled: Boolean = true,
  textAlign: TextAlign? = null,
  inlineContent: Map<String, InlineTextContent> = mapOf(),
  onTextLayout: (TextLayoutResult) -> Unit = {},
) {
  val chanTheme = LocalChanTheme.current

  val textColorPrimary = if (color == null) {
    remember(key1 = chanTheme.textColorPrimary) {
      Color(chanTheme.textColorPrimary)
    }
  } else {
    color
  }

  val actualTextColorPrimary = if (enabled) {
    textColorPrimary
  } else {
    textColorPrimary.copy(alpha = ContentAlpha.disabled)
  }

  ComposeText(
    modifier = modifier,
    color = actualTextColorPrimary,
    text = text,
    fontSize = fontSize,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = softWrap,
    textAlign = textAlign,
    fontWeight = fontWeight,
    inlineContent = inlineContent,
    onTextLayout = onTextLayout
  )
}