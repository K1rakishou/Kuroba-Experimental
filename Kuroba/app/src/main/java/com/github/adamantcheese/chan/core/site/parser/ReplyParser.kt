package com.github.adamantcheese.chan.core.site.parser

import com.github.adamantcheese.chan.core.repository.SiteRepository
import com.github.adamantcheese.chan.core.site.ParserRepository
import com.github.adamantcheese.chan.utils.groupOrNull
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import java.util.regex.Matcher
import java.util.regex.Pattern

class ReplyParser(
  private val siteRepository: SiteRepository,
  private val parserRepository: ParserRepository
) {

  fun extractCommentReplies(siteDescriptor: SiteDescriptor, comment: CharSequence): List<ExtractedQuote> {
    val quotePatterns = getQuotePatterns(siteDescriptor)
      ?: return emptyList()

    var matcher = quotePatterns.fullQuotePattern.matcher(comment)
    if (matcher.find()) {
      return matchFullQuotes(matcher)
    }

    matcher = quotePatterns.quotePattern.matcher(comment)
    if (matcher.find()) {
      return matchQuotes(matcher)
    }

    return emptyList()
  }

  private fun matchQuotes(matcher: Matcher): MutableList<ExtractedQuote> {
    matcher.reset()
    val extractedReplies = mutableListOf<ExtractedQuote>()

    while (matcher.find()) {
      val postId = matcher.groupOrNull(1)?.toLongOrNull()
        ?: continue

      extractedReplies += ExtractedQuote.Quote(postId)
    }

    return extractedReplies
  }

  private fun matchFullQuotes(matcher: Matcher): MutableList<ExtractedQuote> {
    matcher.reset()
    val extractedReplies = mutableListOf<ExtractedQuote>()

    while (matcher.find()) {
      val boardCode = matcher.groupOrNull(1)
        ?: continue
      val threadId = matcher.groupOrNull(2)?.toLongOrNull()
        ?: continue
      val postId = matcher.groupOrNull(3)?.toLongOrNull()
        ?: continue

      extractedReplies += ExtractedQuote.FullQuote(boardCode, threadId, postId)
    }

    return extractedReplies
  }

  private fun getQuotePatterns(siteDescriptor: SiteDescriptor): QuotePatterns? {
    val site = siteRepository.bySiteDescriptor(siteDescriptor)
      ?: return null

    val commentParser = parserRepository.getCommentParser(site.commentParserType())
    if (commentParser !is HasQuotePatterns) {
      return null
    }

    return QuotePatterns(
      commentParser.getQuotePattern(),
      commentParser.getFullQuotePattern()
    )
  }

  sealed class ExtractedQuote {
    class FullQuote(
      val boardCode: String,
      val threadId: Long,
      val postId: Long
    ) : ExtractedQuote()

    class Quote(val postId: Long) : ExtractedQuote()
  }

  private class QuotePatterns(
    val quotePattern: Pattern,
    val fullQuotePattern: Pattern
  )
}