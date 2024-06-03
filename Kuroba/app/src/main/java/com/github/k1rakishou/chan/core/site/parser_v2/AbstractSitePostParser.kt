package com.github.k1rakishou.chan.core.site.parser_v2

import androidx.annotation.CallSuper
import com.github.k1rakishou.chan.core.parser.TextPartBuilder
import com.github.k1rakishou.chan.core.parser.TextPartSpan
import com.github.k1rakishou.chan.core.repository.StaticHtmlColorRepository
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.comment.HtmlTag
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import org.nibor.autolink.LinkExtractor
import org.nibor.autolink.LinkType
import java.util.EnumSet

abstract class AbstractSitePostParser(
  private val staticHtmlColorRepository: StaticHtmlColorRepository
) {

  fun parseHtmlNode(
    htmlTag: HtmlTag,
    textPartBuilders: MutableList<TextPartBuilder>,
    postDescriptor: PostDescriptor,
    parserContext: PostCommentParserContext
  ) {
    when (val tagName = htmlTag.tagName) {
      "br" -> parseNewLineTag(textPartBuilders)
      "p" -> parseParagraphTag(textPartBuilders)
      "s" -> parseStrikethroughTag(textPartBuilders)
      "b",
      "strong" -> parseStrongTag(textPartBuilders)
      "em" -> parseEmphasizedTag(textPartBuilders)
      "sup" -> parseSuperscriptTag(textPartBuilders)
      "sub" -> parseSubscriptTag(textPartBuilders)
      "span" -> parseSpanTag(htmlTag, textPartBuilders, postDescriptor)
      "ins" -> parseInsTag(htmlTag, textPartBuilders, postDescriptor)
      "a" -> parseLinkTag(htmlTag, textPartBuilders, postDescriptor)
      "ul" -> { /**no-op*/ }
      "tr" -> parseTrTag(htmlTag, textPartBuilders, postDescriptor, parserContext)
      "li" -> parseLiTag(htmlTag, textPartBuilders, postDescriptor, parserContext)
      "pre" -> parsePreTag(htmlTag, textPartBuilders, postDescriptor, parserContext)
      "wbr" -> {
        error("<wbr> tags should all be removed during the HTML parsing stage. This is most likely a HTML parser bug.")
      }
      else -> {
        if (tagName.startsWith("h", ignoreCase = true) && tagName.length == 2) {
          parseHeadingTag(
            htmlTag = htmlTag,
            textPartBuilders = textPartBuilders,
            postDescriptor = postDescriptor,
            parserContext = parserContext
          )

          return
        }

        Logger.error(TAG) {
          "Unsupported tag with name '${htmlTag.tagName}' found. " +
            "(postDescriptor: $postDescriptor, htmlTag: $htmlTag)"
        }
      }
    }
  }

  fun postProcessHtmlNode(
    htmlTag: HtmlTag,
    textPartBuilders: MutableList<TextPartBuilder>,
    postDescriptor: PostDescriptor,
    parserContext: PostCommentParserContext
  ) {
    val allChildTextIsBlank = textPartBuilders.all { textPartMut -> textPartMut.text.isBlank() }
    if (allChildTextIsBlank) {
      return
    }

    parseAnyTagStyleAttribute(htmlTag, textPartBuilders)
    parseAnyTagSizeAttribute(htmlTag, textPartBuilders)
    parseAnyTagColorAttribute(htmlTag, textPartBuilders)
  }

  @CallSuper
  open fun parsePreTag(
    htmlTag: HtmlTag,
    textPartBuilders: MutableList<TextPartBuilder>,
    postDescriptor: PostDescriptor,
    parserContext: PostCommentParserContext
  ) {
    TextPartBuilder.addMany(textPartBuilders, TextPartSpan.Monospace)
  }

  @CallSuper
  open fun parseInsTag(
    htmlTag: HtmlTag,
    textPartBuilders: MutableList<TextPartBuilder>,
    postDescriptor: PostDescriptor
  ) {
    TextPartBuilder.addMany(textPartBuilders, TextPartSpan.Underline)
  }

  @CallSuper
  open fun parseHeadingTag(
    htmlTag: HtmlTag,
    textPartBuilders: MutableList<TextPartBuilder>,
    postDescriptor: PostDescriptor,
    parserContext: PostCommentParserContext
  ) {
    val headingSize = htmlTag.tagName.getOrNull(1)
      ?.digitToIntOrNull()
      ?.takeIf { size -> size in 0..5 }
      ?: return

    TextPartBuilder.addMany(textPartBuilders, TextPartSpan.Heading(headingSize))
  }

  @CallSuper
  open fun parseTrTag(
    htmlTag: HtmlTag,
    textPartBuilders: MutableList<TextPartBuilder>,
    postDescriptor: PostDescriptor,
    parserContext: PostCommentParserContext
  ) {
    if (parserContext.isInsideTableTag) {
      textPartBuilders += TextPartBuilder("\n")
    }
  }

  @CallSuper
  open fun parseLiTag(
    htmlTag: HtmlTag,
    textPartBuilders: MutableList<TextPartBuilder>,
    postDescriptor: PostDescriptor,
    parserContext: PostCommentParserContext
  ) {
    if (parserContext.isInsideUlTag) {
      val text = buildString {
        repeat(parserContext.ulTagsCounter.coerceAtMost(5)) {
          append("  ")
        }

        append("•")
        append(" ")
      }

      textPartBuilders.add(0, TextPartBuilder(text))
    }

    textPartBuilders += TextPartBuilder("\n")
  }

  @CallSuper
  open fun parseSubscriptTag(textPartBuilders: MutableList<TextPartBuilder>) {
    TextPartBuilder.addMany(textPartBuilders, TextPartSpan.Subscript)
  }

  @CallSuper
  open fun parseSuperscriptTag(textPartBuilders: MutableList<TextPartBuilder>) {
    TextPartBuilder.addMany(textPartBuilders, TextPartSpan.Superscript)
  }

  @CallSuper
  open fun parseEmphasizedTag(textPartBuilders: MutableList<TextPartBuilder>) {
    TextPartBuilder.addMany(textPartBuilders, TextPartSpan.Italic)
  }

  @CallSuper
  open fun parseStrongTag(textPartBuilders: MutableList<TextPartBuilder>) {
    TextPartBuilder.addMany(textPartBuilders, TextPartSpan.Bold)
  }

  @CallSuper
  open fun parseSpanTag(
    htmlTag: HtmlTag,
    textPartBuilders: MutableList<TextPartBuilder>,
    postDescriptor: PostDescriptor
  ) {
    if (htmlTag.hasClass("u")) {
      TextPartBuilder.addMany(textPartBuilders, TextPartSpan.Underline)
    }
  }

  abstract fun parseLinkTag(
    htmlTag: HtmlTag,
    textPartBuilders: MutableList<TextPartBuilder>,
    postDescriptor: PostDescriptor
  )

  abstract fun parseNewLineTag(textPartBuilders: MutableList<TextPartBuilder>)
  abstract fun parseParagraphTag(textPartBuilders: MutableList<TextPartBuilder>)
  abstract fun parseStrikethroughTag(textPartBuilders: MutableList<TextPartBuilder>)
  abstract fun parseLinkable(className: String?, href: String, postDescriptor: PostDescriptor): TextPartSpan.Linkable?
  abstract fun postProcessTextParts(textPartBuilder: TextPartBuilder): TextPartBuilder

  private fun parseAnyTagColorAttribute(htmlTag: HtmlTag, textPartBuilders: MutableList<TextPartBuilder>) {
    val colorName = htmlTag.attrUnescapedOrNull("color")
      ?: return

    val color = staticHtmlColorRepository.colorByName(colorName)
      ?: return

    TextPartBuilder.addMany(textPartBuilders, TextPartSpan.FgColor(color))
  }

  private fun parseAnyTagSizeAttribute(htmlTag: HtmlTag, textPartBuilders: MutableList<TextPartBuilder>) {
    val sizeAttribute = htmlTag.attrUnescapedOrNull("size")
      ?: return

    val minSize = TextPartSpan.FontSize.MIN_FONT_SIZE_INCREMENT
    val maxSize = TextPartSpan.FontSize.MAX_FONT_SIZE_INCREMENT

    val fontSize = sizeAttribute.toIntOrNull()
      .takeIf { size -> size in minSize..maxSize }
      ?: return

    TextPartBuilder.addMany(textPartBuilders, TextPartSpan.FontSize(fontSize))
  }

  private fun parseAnyTagStyleAttribute(
    htmlTag: HtmlTag,
    textPartBuilders: MutableList<TextPartBuilder>
  ) {
    val styleAttribute = htmlTag.attrUnescapedOrNull("style")
      ?: return

    val parameters = styleAttribute.split(';')

    for (parameter in parameters) {
      if (parameter.startsWith("color:")) {
        val colorName = parameter.split(":").getOrNull(1)?.trim() ?: continue
        val colorRgb = staticHtmlColorRepository.colorByName(colorName) ?: continue

        TextPartBuilder.addMany(textPartBuilders, TextPartSpan.FgColor(colorRgb))
      } else if (parameter.startsWith("background-color:")) {
        val colorName = parameter.split(":").getOrNull(1)?.trim() ?: continue
        val colorRgb = staticHtmlColorRepository.colorByName(colorName) ?: continue

        TextPartBuilder.addMany(textPartBuilders, TextPartSpan.BgColor(colorRgb))
      }
    }
  }

  companion object {
    private const val TAG = "AbstractSitePostParser"

    val LINK_EXTRACTOR = LinkExtractor.builder()
      .linkTypes(EnumSet.of(LinkType.URL))
      .build()
  }

}