package com.github.k1rakishou.chan.features.reply.left

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.github.k1rakishou.chan.core.site.parser.CommentParserHelper
import com.github.k1rakishou.common.substringSafe
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import java.util.regex.Pattern

object ReplyTextFieldHelpers {
  private val InlineQuoteRegex by lazy(LazyThreadSafetyMode.NONE) { Pattern.compile("^(>.*)", Pattern.MULTILINE) }
  private val QuoteRegex by lazy(LazyThreadSafetyMode.NONE) { Pattern.compile("(>>\\d+)") }

  fun colorizeReplyInputText(
    disabledAlpha: Float,
    text: AnnotatedString,
    replyLayoutEnabled: Boolean,
    chanTheme: ChanTheme
  ): AnnotatedString {
    return buildAnnotatedString {
      append(text)

      val textAlpha = if (replyLayoutEnabled) 1f else disabledAlpha
      val textColor = chanTheme.textColorPrimaryCompose.copy(alpha = textAlpha)
      val postInlineQuoteColor = chanTheme.postInlineQuoteColorCompose.copy(alpha = textAlpha)
      val quoteColor = chanTheme.postQuoteColorCompose.copy(alpha = textAlpha)
      val postLinkColor = chanTheme.postLinkColorCompose.copy(alpha = textAlpha)

      addStyle(
        style = SpanStyle(color = textColor),
        start = 0,
        end = text.length
      )

      val inlineQuoteMatcher = InlineQuoteRegex.matcher(text)
      while (inlineQuoteMatcher.find()) {
        val start = inlineQuoteMatcher.start(1)
        val end = inlineQuoteMatcher.end(1)

        if (start >= end) {
          continue
        }

        addStyle(
          style = SpanStyle(color = postInlineQuoteColor),
          start = start,
          end = end
        )
      }

      val quoteMatcher = QuoteRegex.matcher(text)
      while (quoteMatcher.find()) {
        val start = quoteMatcher.start(1)
        val end = quoteMatcher.end(1)

        if (start >= end) {
          continue
        }

        addStyle(
          style = SpanStyle(
            color = quoteColor,
            textDecoration = TextDecoration.Underline
          ),
          start = start,
          end = end
        )
      }

      CommentParserHelper.LINK_EXTRACTOR.extractLinks(text).forEach { linkSpan ->
        addStyle(
          style = SpanStyle(
            color = postLinkColor,
            textDecoration = TextDecoration.Underline
          ),
          start = linkSpan.beginIndex,
          end = linkSpan.endIndex
        )
      }
    }
  }

  fun findAllQuotesInText(chanDescriptor: ChanDescriptor, replyTextCopy: String): Set<PostDescriptor> {
    val foundPostDescriptors = mutableSetOf<PostDescriptor>()

    val quoteMatcher = QuoteRegex.matcher(replyTextCopy)
    while (quoteMatcher.find()) {
      val start = quoteMatcher.start(1)
      val end = quoteMatcher.end(1)

      if (start >= end) {
        continue
      }

      val postNo = replyTextCopy.substringSafe(start + 2, end)?.toLongOrNull()
      if (postNo == null || postNo < 0) {
        continue
      }

      foundPostDescriptors += PostDescriptor.create(chanDescriptor, postNo)
    }

    return foundPostDescriptors
  }

}