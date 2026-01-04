package com.github.k1rakishou.chan.ui.captcha.chan4

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.Locale

class Chan4CaptchaTitleFormatter {
  private val styleMatchers = listOf(
    DisplayIsNone(),
    OpacityIntIsZero(),
    WidthIsTooSmall(),
  )

  fun format(rawTitle: String?): AnnotatedString {
    var title = rawTitle ?: "Captcha has no title (probably json structure got changed)"

    title = title.removePrefix("Use the scroll bar below to ")
    title = title.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(Locale.ENGLISH) else ch.toString() }
    title = title.removeSuffix(", then click Next.")

    val document = Jsoup.parseBodyFragment(title)
    val nodes = document.childNodes()

    return buildAnnotatedString {
      for (node in nodes) {
        val processed = processNode(node)
        if (processed != null) {
          append(processed)
        }
      }
    }
  }

  private fun processNode(node: Node): AnnotatedString? {
    if (node is TextNode) {
      return AnnotatedString(node.text())
    }

    if (node is Element) {
      val childNodes = node.childNodes()

      val allInnerText = buildAnnotatedString {
        for (childNode in childNodes) {
          val processed = processNode(childNode)
          if (processed != null) {
            append(processed)
          }
        }
      }

      val styleHandledText = handleStyle(node, allInnerText)
      if (styleHandledText == null) {
        return null
      }

      return handleNode(node, styleHandledText)
    }

    return null
  }

  private fun handleNode(
    node: Element,
    text: AnnotatedString
  ): AnnotatedString? {
    val tagName = node.tagName()

    if (tagName.equals("b", ignoreCase = true)) {
      return buildAnnotatedString {
        pushStyle(
          SpanStyle(
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline
          )
        )

        append(text)
      }
    }

    return text
  }

  private fun handleStyle(
    node: Element,
    allInnerText: AnnotatedString
  ): AnnotatedString? {
    val styles = node.attr("style")
      .split(";")
      .map { part -> part.trim() }

    for (style in styles) {
      if (!style.contains(":")) {
        continue
      }

      val parts = style.split(":")
      if (parts.size != 2) {
        continue
      }

      val key = parts[0]
      val value = parts[1]

      val anyMatches = styleMatchers.any { styleProcessor ->
        styleProcessor.matches(key, value)
      }

      if (anyMatches) {
        return null
      }
    }

    return allInnerText
  }

  interface StyleProcessor {
    fun matches(name: String, value: String): Boolean
  }

  class DisplayIsNone : StyleProcessor {
    override fun matches(name: String, value: String): Boolean {
      if (!name.equals("display", ignoreCase = true)) {
        return false
      }

      return value.equals("none", ignoreCase = true)
    }
  }

  class OpacityIntIsZero : StyleProcessor {
    override fun matches(name: String, value: String): Boolean {
      if (!name.equals("opacity", ignoreCase = true)) {
        return false
      }

      return value.toIntOrNull() == 0
    }
  }

  class WidthIsTooSmall : StyleProcessor {
    override fun matches(name: String, value: String): Boolean {
      if (!name.equals("width", ignoreCase = true)) {
        return false
      }

      if (value.contains("px")) {
        val width = value.removeSuffix("px")
          .toIntOrNull()
          ?: return false

        if (width < 3) {
          return true
        }
      }

      return false
    }
  }
}