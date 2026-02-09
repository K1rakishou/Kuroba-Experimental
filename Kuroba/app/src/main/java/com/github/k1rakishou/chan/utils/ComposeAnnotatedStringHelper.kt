package com.github.k1rakishou.chan.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

interface ComposeAnnotatedStringHelper {
  data class TextMark(
    val pattern: String,
    val backgroundColor: Color = Color.Unspecified,
    val textColor: Color = Color.Unspecified
  )
}

class ComposeAnnotatedStringHelperImpl {
  fun AnnotatedString.Builder.markText(text: String, textMarks: List<ComposeAnnotatedStringHelper.TextMark>) {
    if (text.isEmpty()) {
      return
    }

    for (textMark in textMarks) {
      val pattern = Regex(textMark.pattern)

      pattern.findAll(text)
        .forEach { matchResult ->
          val range = matchResult.range
          if (range.isEmpty()) {
            return@forEach
          }

          val span = SpanStyle(
            color = textMark.textColor,
            background = textMark.backgroundColor
          )

          addStyle(
            style = span,
            start = range.first,
            end = range.last + 1
          )
        }
    }
  }
}