package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.github.k1rakishou.chan.ui.compose.KurobaTextUnit
import com.github.k1rakishou.chan.ui.compose.collectTextFontSize
import com.github.k1rakishou.chan.ui.compose.ktu

@Composable
internal fun ComposeText(
  text: String,
  modifier: Modifier = Modifier,
  color: Color = Color.Unspecified,
  fontSize: KurobaTextUnit = 16.ktu,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  textAlign: TextAlign? = null,
  fontWeight: FontWeight? = null,
  onTextLayout: (TextLayoutResult) -> Unit = {},
  style: TextStyle = LocalTextStyle.current
) {
  val actualFontSize = collectTextFontSize(defaultFontSize = fontSize)

  Text(
    modifier = modifier,
    color = color,
    text = text,
    fontSize = actualFontSize,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = softWrap,
    textAlign = textAlign,
    fontWeight = fontWeight,
    onTextLayout = onTextLayout,
    style = remember(style) { kurobaTextStyle(style) }
  )
}

@Composable
internal fun ComposeText(
  text: AnnotatedString,
  modifier: Modifier = Modifier,
  color: Color = Color.Unspecified,
  fontSize: KurobaTextUnit = 16.ktu,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  textAlign: TextAlign? = null,
  fontWeight: FontWeight? = null,
  inlineContent: Map<String, InlineTextContent> = mapOf(),
  onTextLayout: (TextLayoutResult) -> Unit = {},
  style: TextStyle = LocalTextStyle.current
) {
  val actualFontSize = collectTextFontSize(defaultFontSize = fontSize)

  Text(
    modifier = modifier,
    color = color,
    text = text,
    fontSize = actualFontSize,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = softWrap,
    textAlign = textAlign,
    fontWeight = fontWeight,
    inlineContent = inlineContent,
    onTextLayout = onTextLayout,
    style = remember(style) { kurobaTextStyle(style) }
  )
}

private fun kurobaTextStyle(style: TextStyle): TextStyle {
  return style.copy(
    lineHeight = style.lineHeight * 0.8f
  )
}