package com.github.k1rakishou.chan.features.reply

import android.text.Editable
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import androidx.core.text.getSpans
import com.github.k1rakishou.chan.core.site.parser.CommentParserHelper
import com.github.k1rakishou.common.setSpanSafe
import com.github.k1rakishou.core_themes.ThemeEngine
import java.util.regex.Pattern

object ReplyCommentHelper {
  private val QUOTE_MATCHER = Pattern.compile(">>\\d+")
  private val BOARD_LINK_MATCHER = Pattern.compile(">>>\\/\\w+\\/")

  fun processReplyCommentHighlight(themeEngine: ThemeEngine, commentText: Editable) {
    commentText
      .getSpans<ReplyLinkSpan>(0, commentText.length)
      .forEach { span -> commentText.removeSpan(span) }

    var offset = 0
    val newLine = "\n"
    val newLineLen = newLine.length

    val factor = if (ThemeEngine.isDarkColor(themeEngine.chanTheme.accentColor)) {
      1.2f
    } else {
      0.8f
    }

    val postInlineQuoteColor = ThemeEngine.manipulateColor(themeEngine.chanTheme.postInlineQuoteColor, factor)
    val postLinkColor = ThemeEngine.manipulateColor(themeEngine.chanTheme.postLinkColor, factor)
    val postQuoteColor = ThemeEngine.manipulateColor(themeEngine.chanTheme.postQuoteColor, factor)

    for (line in commentText.split(newLine)) {
      val lineFormatted = line.trimStart()

      val greenTextStartIndex = findGreenTextStartSymbolIndex(lineFormatted)
      if (greenTextStartIndex >= 0) {
        processGreenText(postInlineQuoteColor, greenTextStartIndex, line, offset, commentText)
      }

      processLinks(postLinkColor, line, offset, commentText)
      processQuotes(postQuoteColor, line, offset, commentText)
      processBoardLinks(postQuoteColor, line, offset, commentText)

      offset += (line.length + newLineLen)
    }
  }

  private fun processGreenText(
    postInlineQuoteColor: Int,
    greenTextStartIndex: Int,
    line: String,
    offset: Int,
    commentText: Editable
  ) {
    val span = HighlightSpan(postInlineQuoteColor)

    val start = greenTextStartIndex + offset
    val end = line.length + offset

    commentText.setSpanSafe(span, start, end, 0)
  }

  private fun processLinks(
    postLinkColor: Int,
    line: String,
    offset: Int,
    commentText: Editable,
  ) {
    CommentParserHelper.LINK_EXTRACTOR.extractLinks(line).forEach { link ->
      val start = link.beginIndex + offset
      val end = link.endIndex + offset

      val spans = commentText.getSpans<HighlightSpan>(
        start = start,
        end = end
      )

      if (spans.isNotEmpty()) {
        return@forEach
      }

      val span = HighlightSpan(postLinkColor)

      commentText.setSpanSafe(span, start, end, 0)
      commentText.setSpanSafe(LinkUnderlineSpan(), start, end, 0)
    }
  }

  private fun processBoardLinks(
    postQuoteColor: Int,
    line: String,
    offset: Int,
    commentText: Editable,
  ) {
    val matcher = BOARD_LINK_MATCHER.matcher(line)

    while (matcher.find()) {
      val start = matcher.start(0) + offset
      val end = matcher.end(0) + offset

      val spans = commentText.getSpans<HighlightSpan>(start = start, end = end)
      if (spans.isNotEmpty()) {
        break
      }

      val span = HighlightSpan(postQuoteColor)

      commentText.setSpanSafe(span, start, end, 0)
      commentText.setSpanSafe(LinkUnderlineSpan(), start, end, 0)
    }
  }

  private fun processQuotes(
    postQuoteColor: Int,
    line: String,
    offset: Int,
    commentText: Editable
  ) {
    val matcher = QUOTE_MATCHER.matcher(line)

    while (matcher.find()) {
      val start = matcher.start(0) + offset
      val end = matcher.end(0) + offset

      val spans = commentText.getSpans<HighlightSpan>(start = start, end = end)
      if (spans.isNotEmpty()) {
        break
      }

      val span = HighlightSpan(postQuoteColor)

      commentText.setSpanSafe(span, start, end, 0)
      commentText.setSpanSafe(LinkUnderlineSpan(), start, end, 0)
    }
  }

  private fun findGreenTextStartSymbolIndex(lineFormatted: String): Int {
    val curr = lineFormatted.getOrNull(0)
    val next = lineFormatted.getOrNull(1)

    if (curr == '>' && (next == null || next != '>')) {
      return 0
    }

    return -1
  }

  interface ReplyLinkSpan

  class LinkUnderlineSpan : UnderlineSpan(), ReplyLinkSpan

  class HighlightSpan(color: Int) : ForegroundColorSpan(color), ReplyLinkSpan {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false
      val that = other as HighlightSpan
      return foregroundColor == that.foregroundColor
    }

    override fun hashCode(): Int {
      return foregroundColor
    }

  }

}