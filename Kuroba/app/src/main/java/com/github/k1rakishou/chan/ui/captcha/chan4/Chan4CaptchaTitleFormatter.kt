package com.github.k1rakishou.chan.ui.captcha.chan4

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.indexOfFirstOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import kotlin.io.encoding.Base64

class Chan4CaptchaTitleFormatter {
  private val styleMatchers = listOf(
    DisplayIsNone(),
    OpacityIntIsZero(),
    WidthIsTooSmall(),
  )

  fun format(rawTitle: String?): Title {
    val titleBuilder = StringBuilder(rawTitle ?: "Captcha has no title (probably json structure got changed)")

    run {
      val text = "Use the scroll bar below to "

      titleBuilder.indexOf(text)
        .takeIf { index -> index >= 0 }
        ?.let { index -> titleBuilder.deleteRange(index, index + text.length) }
    }
    run {
      titleBuilder.indexOf("find the image that")
        .takeIf { index -> index >= 0 }
        ?.let { index ->
          val titleCaseChar = titleBuilder.getOrNull(index)?.titlecaseChar()
          if (titleCaseChar != null) {
            titleBuilder.set(index, titleCaseChar)
          }
        }
    }

    run {
      val text = ", then click Next."

      titleBuilder.indexOf(text)
        .takeIf { index -> index >= 0 }
        ?.let { index -> titleBuilder.deleteRange(index, index + text.length) }
    }

    val document = Jsoup.parseBodyFragment(titleBuilder.toString())
    val nodes = document.childNodes()
    val images = mutableListOf<ImageBitmap>()

    val text = buildAnnotatedString {
      for (node in nodes) {
        val processed = processNode(node, images)
        if (processed != null) {
          append(processed)
        }
      }
    }

    return Title(
      annotated = text,
      images = images
    )
  }

  private fun processNode(node: Node, images: MutableList<ImageBitmap>): AnnotatedString? {
    if (node is TextNode) {
      return AnnotatedString(node.text())
    }

    if (node is Element) {
      val childNodes = node.childNodes()

      val allInnerText = buildAnnotatedString {
        for (childNode in childNodes) {
          val processed = processNode(childNode, images)
          if (processed != null) {
            append(processed)
          }
        }
      }

      val styleHandledText = handleStyle(node, allInnerText)
      if (styleHandledText == null) {
        return null
      }

      return handleNode(node, styleHandledText, images)
    }

    return null
  }

  private fun handleNode(
    node: Element,
    text: AnnotatedString,
    images: MutableList<ImageBitmap>
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

    if (tagName.equals("img", ignoreCase = true)) {
      try {
        val src = node.attr("src")

        val firstIdx = src.indexOfFirstOrNull { ch -> ch == ';' } ?: 0
        val secondIdx = src.indexOfFirstOrNull(firstIdx + 1) { ch -> ch == ',' } ?: 0

        val encoding = if (firstIdx > 0 && secondIdx > firstIdx) {
          src.slice(firstIdx + 1..<secondIdx)
        } else {
          null
        }
        val data = if (secondIdx > 0) {
          src.slice(secondIdx + 1..<src.length)
        } else {
          src
        }

        val decodedImageBytes = if (encoding?.equals("base64", ignoreCase = true) == true) {
          Base64.decode(data)
        } else {
          data.encodeToByteArray()
        }

        val imageBitmap = BitmapFactory.decodeByteArray(decodedImageBytes, 0, decodedImageBytes.size).asImageBitmap()
        images += imageBitmap
      } catch (error: Throwable) {
        return buildAnnotatedString {
          append("Failed to decode captcha image! Error: ${error.errorMessageOrClassName()}")
        }
      }

      return null
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

  data class Title(
    val annotated: AnnotatedString,
    val images: List<ImageBitmap>
  )

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