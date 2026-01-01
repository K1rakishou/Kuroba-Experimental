package com.github.k1rakishou.chan.ui.captcha.chan4

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.jsoup.Jsoup
import java.util.Locale

class Chan4CaptchaTitleFormatter {
  fun format(rawTitle: String?): AnnotatedString {
    var title = rawTitle ?: "Captcha has no title (probably json structure got changed)"

    title = title.removePrefix("Use the scroll bar below to ")
    title = title.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(Locale.ENGLISH) else ch.toString() }
    title = title.removeSuffix(", then click Next.")
    title += "."

    val processed = processHtmlTags(title)
    val annotated = addAnnotations(processed)

    return annotated
  }

  private fun processHtmlTags(title: String): String {
    val document = Jsoup.parseBodyFragment(title)
    for (element in document.select("*")) {
      var style = element.attr("style")
      if (style.contains(";")) {
        style = style.split(";").last().trim()
      }

      if (style.contains(":")) {
        val parts = style.split(":")
        if (parts.size == 2) {
          val key = parts[0]
          val value = parts[1]

          if (!key.equals("display", ignoreCase = true)) {
            continue
          }

          if (value.equals("none", ignoreCase = true)) {
            element.remove()
            continue
          } else if (value.equals("inline", ignoreCase = true)) {
            element.unwrap()
            continue
          }
        }
      }
    }

    val parsed = document.body().html()
    return parsed
  }

  private fun addAnnotations(input: String): AnnotatedString {
    val openTag = "<b>"
    val closeTag = "</b>"

    val span = SpanStyle(
      fontWeight = FontWeight.Bold,
      textDecoration = TextDecoration.Underline
    )

    return buildAnnotatedString {
      val boldStack = ArrayDeque<Int>()
      var offset = 0
      var removedChars = 0
      var firstTagSkipped = false

      while (offset < input.length) {
        when {
          input.startsWith(openTag, offset) -> {
            boldStack.add(offset)
            offset += openTag.length
          }

          input.startsWith(closeTag, offset) -> {
            var start = boldStack.removeLastOrNull()
            if (start != null) {
              if (!firstTagSkipped) {
                firstTagSkipped = true
              } else {
                start -= removedChars
              }

              val end = offset - removedChars - openTag.length
              if (end < offset && end <= length && start < end) {
                addStyle(span, start, end)
              }
            }

            offset += closeTag.length
            removedChars += (openTag.length + closeTag.length)
          }

          else -> {
            append(input[offset])
            offset += 1
          }
        }
      }

      while (boldStack.isNotEmpty()) {
        val start = boldStack.removeLast()
        if (start <= length) {
          addStyle(span, start, length)
        }
      }
    }
  }
}