package com.github.k1rakishou.chan.core.site.parser_v2

import com.github.k1rakishou.chan.core.parser.TextPartBuilder
import com.github.k1rakishou.chan.core.parser.TextPartSpan
import com.github.k1rakishou.chan.core.repository.StaticHtmlColorRepository
import com.github.k1rakishou.core_parser.comment.HtmlTag
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.model.data.descriptor.PostDescriptor


class DvachPostParser(
  staticHtmlColorRepository: StaticHtmlColorRepository
) : Chan4PostParser(staticHtmlColorRepository) {

  override fun parseSpanTag(
    htmlTag: HtmlTag,
    textPartBuilders: MutableList<TextPartBuilder>,
    postDescriptor: PostDescriptor
  ) {
    super.parseSpanTag(htmlTag, textPartBuilders, postDescriptor)

    if (htmlTag.hasClass("unkfunc")) {
      TextPartBuilder.addMany(textPartBuilders, TextPartSpan.FgColorId(ChanThemeColorId.PostInlineQuoteColor))
    }

    if (htmlTag.hasClass("post__pomyanem")) {
      TextPartBuilder.addMany(textPartBuilders, TextPartSpan.FgColorId(ChanThemeColorId.AccentColor))
    }

    if (htmlTag.hasClass("spoiler")) {
      TextPartBuilder.addMany(textPartBuilders, TextPartSpan.Spoiler)
    }

    parseLinkTag(htmlTag, textPartBuilders, postDescriptor)
  }

  override fun parseLinkTag(
    htmlTag: HtmlTag,
    textPartBuilders: MutableList<TextPartBuilder>,
    postDescriptor: PostDescriptor
  ) {
    var textPartBuilder = if (textPartBuilders.size == 1) {
      textPartBuilders.first()
    } else {
      return
    }

    val className = htmlTag.classAttrOrNull()
    val isDeadLink = htmlTag.tagName == "span" && htmlTag.hasClass("deadlink")

    val href = if (isDeadLink) {
      textPartBuilder.text
    } else {
      htmlTag.attrUnescapedOrNull("href")
    }

    if (href.isNullOrEmpty()) {
      return
    }

    val linkable = parseLinkable(className, href, postDescriptor)
      ?: TextPartSpan.Linkable.Url(href)

    if (linkable is TextPartSpan.Linkable.Quote) {
      textPartBuilder = quoteTrimUnnecessaryCharacters(textPartBuilder)

      textPartBuilders.clear()
      textPartBuilders.add(textPartBuilder)
    }

    TextPartBuilder.add(textPartBuilder, linkable)
  }

  override fun parseParagraphTag(textPartBuilders: MutableList<TextPartBuilder>) {
    textPartBuilders += TextPartBuilder(text = "\n")
    textPartBuilders += TextPartBuilder(text = "\n")
  }

  override fun parseStrikethroughTag(textPartBuilders: MutableList<TextPartBuilder>) {
    TextPartBuilder.addMany(textPartBuilders, TextPartSpan.Strikethrough)
  }

  override fun parseLinkable(className: String?, href: String, postDescriptor: PostDescriptor): TextPartSpan.Linkable? {
    val hrefPreprocessed = preprocessHref(href)

    // '/a/res/7526735.html#7526735'
    // '/a/res/7526735.html'

    val fullPathSplit = hrefPreprocessed.split("/")
      .filter { part -> part.isNotBlank() }

    val resSegment = fullPathSplit.getOrNull(1)
    if (resSegment?.equals("res", ignoreCase = true) != true) {
      return null
    }

    val boardCode = fullPathSplit.getOrNull(0)
      ?: return null
    val threadNoPostNo = fullPathSplit.getOrNull(2)
      ?: return null

    var threadNo = 0L
    var postNo = 0L

    if (threadNoPostNo.contains("#")) {
      val threadNoPostNoSplit = threadNoPostNo.split("#")

      threadNo = threadNoPostNoSplit.getOrNull(0)?.replace(".html", "")?.toLongOrNull()
        ?: return null
      postNo = threadNoPostNoSplit.getOrNull(1)?.toLongOrNull()
        ?: return null
    } else if (threadNoPostNo.endsWith(".html")) {
      threadNo = threadNoPostNo.removeSuffix(".html").toLongOrNull()
        ?: return null
      postNo = threadNo
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
      crossThread = threadNo != postDescriptor.getThreadNo(),
      dead = false,
      postDescriptor = quotePostDescriptor
    )
  }

  // >>7484866 (OP) ->  >>7484866
  // >>7499275 →    ->  >>7499275
  private fun quoteTrimUnnecessaryCharacters(textPartBuilder: TextPartBuilder): TextPartBuilder {
    val newQuoteText = buildString {
      var offset = 0
      var processingPostNumber = false
      val text = textPartBuilder.text

      while (offset < text.length) {
        val ch = text.getOrNull(offset) ?: break

        if (ch.isDigit()) {
          processingPostNumber = true
        }

        if (ch.isWhitespace() && !processingPostNumber) {
          ++offset
          continue
        }

        if (ch != '>' && !ch.isDigit()) {
          break
        }

        append(text[offset])
        ++offset
      }
    }

    if (newQuoteText.isBlank()) {
      return textPartBuilder
    }

    return textPartBuilder.copy(text = newQuoteText)
  }

}