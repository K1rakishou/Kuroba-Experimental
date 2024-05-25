package com.github.k1rakishou.chan.core.site.parser_v2

import androidx.annotation.VisibleForTesting
import com.github.k1rakishou.chan.core.parser.TextPartMut
import com.github.k1rakishou.chan.core.parser.TextPartSpan
import com.github.k1rakishou.chan.core.repository.StaticHtmlColorRepository
import com.github.k1rakishou.chan.utils.decodeUrlOrNull
import com.github.k1rakishou.chan.utils.removeAllBefore
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_parser.comment.HtmlTag
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import java.nio.charset.StandardCharsets

open class Chan4PostParser(
  staticHtmlColorRepository: StaticHtmlColorRepository
) : AbstractSitePostParser(staticHtmlColorRepository) {

  override fun parsePreTag(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPartMut>,
    postDescriptor: PostDescriptor,
    parserContext: PostCommentParserContext
  ) {
    super.parsePreTag(htmlTag, childTextParts, postDescriptor, parserContext)

    val tagClass = htmlTag.classAttrOrNull()
    if (tagClass == "prettyprint") {
      // TODO: compose post cells.
    }
  }

  override fun parseNewLineTag(childTextParts: MutableList<TextPartMut>) {
    childTextParts += TextPartMut(text = "\n")
  }

  override fun parseParagraphTag(childTextParts: MutableList<TextPartMut>) {

  }

  override fun parseStrikethroughTag(childTextParts: MutableList<TextPartMut>) {
    for (childTextPart in childTextParts) {
      childTextPart.spans.add(TextPartSpan.Spoiler)
    }
  }

  override fun parseSpanTag(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPartMut>,
    postDescriptor: PostDescriptor
  ) {
    super.parseSpanTag(htmlTag, childTextParts, postDescriptor)

    if (htmlTag.hasClass("quote")) {
      for (childTextPart in childTextParts) {
        childTextPart.spans.add(TextPartSpan.FgColorId(ChanThemeColorId.PostInlineQuoteColor))
      }
    }

    if (htmlTag.hasClass("s")) {
      for (childTextPart in childTextParts) {
        childTextPart.spans.add(TextPartSpan.Linethrough)
      }
    }

    parseLinkTag(htmlTag, childTextParts, postDescriptor)
  }

  override fun parseLinkTag(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPartMut>,
    postDescriptor: PostDescriptor
  ) {
    val childTextPart = if (childTextParts.size == 1) {
      childTextParts.first()
    } else {
      val mergedTextPart = mergeChildTextPartsIntoOne(childTextParts)

      childTextParts.clear()
      childTextParts.add(mergedTextPart)

      mergedTextPart
    }

    val isDeadLink = htmlTag.tagName == "span" && htmlTag.hasClass("deadlink")

    val href = if (isDeadLink) {
      childTextPart.text
    } else {
      htmlTag.attrUnescapedOrNull("href")
    }

    if (href.isNullOrEmpty()) {
      return
    }

    var linkable: TextPartSpan.Linkable? = null

    if (href.isNotNullNorEmpty()) {
      val className = htmlTag.classAttrOrNull()

      linkable = parseLinkable(className, href, postDescriptor)
    }

    if (linkable == null) {
      linkable = TextPartSpan.Linkable.Url(href)
    }

    childTextPart.spans += linkable
  }

  @VisibleForTesting
  override fun parseLinkable(
    className: String?,
    href: String,
    postDescriptor: PostDescriptor
  ): TextPartSpan.Linkable? {
    val isDeadLink = className.equals(other = "deadlink", ignoreCase = true)
    val isQuoteLink = className.equals(other = "quotelink", ignoreCase = true)

    // TODO: check that this quote points to a real post that exists and if the post it points to does not exist then
    //  mark then quote a 'dead'

    if (!isDeadLink && !isQuoteLink) {
      return null
    }

    if (href.startsWith(">>>/")) {
      // Cross-board quote >>>/g/1234567890
      val crossBoardLinkSplit = href.drop(4).split("/")

      val boardCode = crossBoardLinkSplit.getOrNull(0)
        ?: return null
      val threadNo = crossBoardLinkSplit.getOrNull(1)?.toLongOrNull()
        ?: return null

      val quotedThread = PostDescriptor.create(
        siteName = postDescriptor.siteDescriptor().siteName,
        boardCode = boardCode,
        threadNo = threadNo,
        postNo = threadNo
      )

      return TextPartSpan.Linkable.Quote(
        crossThread = true,
        dead = isDeadLink,
        postDescriptor = quotedThread
      )
    }

    if (href.startsWith("#") || href.startsWith(">>")) {
      // Internal quote, e.g. '#p370525473'
      // or
      // Internal dead quote, e.g. '>>370525473'
      val postNo = href.drop(2).toLongOrNull()
        ?: return null

      if (postNo <= 0) {
        return null
      }

      val quotePostDescriptor = PostDescriptor.create(
        threadDescriptor = postDescriptor.threadDescriptor(),
        postNo = postNo
      )

      return TextPartSpan.Linkable.Quote(
        crossThread = false,
        dead = isDeadLink,
        postDescriptor = quotePostDescriptor
      )
    }

    if (href.startsWith("/")) {
      // External quote
      val hrefPreprocessed = preprocessHref(href)

      when {
        href.contains("/thread/") -> {
          // Thread quotes:
          // '/qst/thread/5126311#p5126311'
          // '/aco/thread/6149612#p6149612'

          // Cross-thread quotes:
          // '/vg/thread/369649921#p369650787'
          // '/vg/thread/369649921'

          val fullPathSplit = hrefPreprocessed.split("/")
            .filter { part -> part.isNotBlank() }

          val threadSegment = fullPathSplit.getOrNull(1)
          if (threadSegment?.equals("thread", ignoreCase = true) != true) {
            return null
          }

          val boardCode = fullPathSplit.getOrNull(0)
            ?: return null
          val threadNoPostNo = fullPathSplit.getOrNull(2)
            ?: return null

          var threadNo = 0L
          var postNo = 0L

          if (threadNoPostNo.contains("#p")) {
            val threadNoPostNoSplit = threadNoPostNo.split("#p")

            threadNo = threadNoPostNoSplit.getOrNull(0)?.toLongOrNull()
              ?: return null
            postNo = threadNoPostNoSplit.getOrNull(1)?.toLongOrNull()
              ?: return null
          } else {
            threadNo = threadNoPostNo.toLongOrNull()
              ?: return null
            postNo = threadNo
          }

          if (threadNo <= 0 || postNo <= 0) {
            return null
          }

          val quotePostDescriptor = PostDescriptor.create(
            siteName = postDescriptor.siteDescriptor().siteName,
            boardCode = boardCode,
            threadNo = threadNo,
            postNo = postNo
          )

          return TextPartSpan.Linkable.Quote(
            crossThread = true,
            dead = isDeadLink,
            postDescriptor = quotePostDescriptor
          )
        }
        hrefPreprocessed.contains("#s") -> {
          // Search quotes:
          // '/vg/catalog#s=tesog%2F'
          // '/aco/catalog#s=weg%2F'

          val fullPathSplit = hrefPreprocessed.split("/")
            .filter { part -> part.isNotBlank() }

          val boardCode = fullPathSplit.getOrNull(0)
            ?: return null
          val theRest = fullPathSplit.getOrNull(1)?.removePrefix("catalog#s=")
            ?: return null

          val decodedQuery = decodeUrlOrNull(theRest, StandardCharsets.UTF_8.name())
            ?.removeSuffix("/")
            ?: return null

          return TextPartSpan.Linkable.Search(
            boardCode = boardCode,
            searchQuery = decodedQuery
          )
        }
        else -> {
          val fullPathSplit = hrefPreprocessed.split("/")
            .filter { part -> part.isNotBlank() }

          if (fullPathSplit.size == 1) {
            // Board quotes:
            // '/jp/'
            // '/jp/'
            val boardCode = fullPathSplit.get(0).removePrefix("/").removeSuffix("/").trim()
            if (boardCode.isBlank()) {
              return null
            }

            return TextPartSpan.Linkable.Board(
              boardCode = boardCode
            )
          }
        }
      }
    }

    return null
  }

  override fun postProcessTextParts(textPartMut: TextPartMut): TextPartMut {
    val text = textPartMut.text
    val links = LINK_EXTRACTOR.extractLinks(text)

    for (link in links) {
      val urlSpan = TextPartSpan.Linkable.Url(text.substring(link.beginIndex, link.endIndex))

      textPartMut.spans += TextPartSpan.PartialSpan(
        start = link.beginIndex,
        end = link.endIndex,
        linkSpan = urlSpan
      )
    }

    return textPartMut
  }

  protected fun preprocessHref(href: String): String {
    // //boards.4channel.org/qst/thread/5126311#p5126311  -> qst/thread/5126311#p5126311
    // /qst/thread/5126311#p5126311                       -> qst/thread/5126311#p5126311
    // https://2ch.hk/bo/res/489664.html                  -> bo/res/489664.html

    var resultHref = href

    if (resultHref.contains("://")) {
      resultHref = resultHref.removeAllBefore("://").dropWhile { it != '/' }
    }

    if (resultHref.startsWith("//")) {
      resultHref = resultHref.removePrefix("//").dropWhile { it != '/' }
    }

    return resultHref.removePrefix("/")
  }

  companion object {
    private const val TAG = "Chan4PostParser"
  }

}