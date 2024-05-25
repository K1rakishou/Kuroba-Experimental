package com.github.k1rakishou.chan.core.site.parser_v2

import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.parser.TextPart
import com.github.k1rakishou.chan.core.parser.TextPartMut
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.comment.HtmlNode
import com.github.k1rakishou.core_parser.comment.HtmlParserPool
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

interface PostCommentParser {
  suspend fun parsePostCommentAsText(
    postCommentUnparsed: String,
    postDescriptor: PostDescriptor
  ): String?
  suspend fun parsePostComment(
    postCommentUnparsed: String?,
    postDescriptor: PostDescriptor
  ): List<TextPart>
  fun parsePostComment(
    postParser: AbstractSitePostParser,
    postCommentUnparsed: String,
    postDescriptor: PostDescriptor
  ): List<TextPart>
}

class PostCommentParserImpl(
  private val siteManager: SiteManager,
  private val htmlParserPool: HtmlParserPool
) : PostCommentParser {

  override suspend fun parsePostCommentAsText(
    postCommentUnparsed: String,
    postDescriptor: PostDescriptor
  ): String? {
    val site = siteManager.bySiteDescriptor(postDescriptor.siteDescriptor())
    if (site == null) {
      Logger.error(TAG) {
        "parsePostCommentAsText() site for descriptor ${postDescriptor.siteDescriptor()} is not supported!"
      }

      return null
    }

    val postParser = site.chanApi().parserV2()
    if (postParser == null) {
      Logger.error(TAG) {
        "parsePostCommentAsText() site '${site.name()}' doesn't provide AbstractSitePostParser!"
      }

      return null
    }

    return ModularResult.Try {
      val textParts = parsePostComment(postParser, postCommentUnparsed, postDescriptor)
      val totalTextLength = textParts.sumOf { textPart -> textPart.text.length }
      val stringBuilder = StringBuilder(totalTextLength)

      textParts.joinTo(
        buffer = stringBuilder,
        separator = "",
        transform = { textPart -> textPart.text }
      )

      return@Try stringBuilder.toString()
    }.valueOrNull()
  }

  override suspend fun parsePostComment(
    postCommentUnparsed: String?,
    postDescriptor: PostDescriptor
  ): List<TextPart> {
    if (postCommentUnparsed.isNullOrBlank()) {
      return emptyList()
    }

    val site = siteManager.bySiteDescriptor(postDescriptor.siteDescriptor())
    if (site == null) {
      Logger.error(TAG) {
        "parsePostComment() site for descriptor ${postDescriptor.siteDescriptor()} is not supported!"
      }

      return emptyList()
    }

    val postParser = site.chanApi().parserV2()
    if (postParser == null) {
      Logger.error(TAG) {
        "parsePostCommentAsText() site '${site.name()}' doesn't provide AbstractSitePostParser!"
      }

      return emptyList()
    }

    return ModularResult
      .Try {
        parsePostComment(
          postParser = postParser,
          postCommentUnparsed = postCommentUnparsed,
          postDescriptor = postDescriptor
        )
      }
      .mapErrorToValue { error ->
        Logger.error(TAG) {
          "Failed to parse post '${postDescriptor.userReadableString()}, error: '${error.errorMessageOrClassName()}'"
        }

        val errorMessage = "Failed to parse post '${postDescriptor.userReadableString()}', " +
          "error message: ${error.errorMessageOrClassName()}"

        return@mapErrorToValue listOf(TextPart(text = errorMessage))
      }
  }

  override fun parsePostComment(
    postParser: AbstractSitePostParser,
    postCommentUnparsed: String,
    postDescriptor: PostDescriptor
  ): List<TextPart> {
    val htmlParser = htmlParserPool.get()
    val htmlNodes = htmlParser.parse(postCommentUnparsed).nodes
    val parserContext = PostCommentParserContext()

    return processNodes(
      postDescriptor = postDescriptor,
      htmlNodes = htmlNodes,
      sitePostParser = postParser,
      parserContext = parserContext
    )
      .map { textPartMut -> postParser.postProcessTextParts(textPartMut) }
      .map { textPartMut -> textPartMut.toTextPartWithSortedSpans() }
  }

  private fun processNodes(
    postDescriptor: PostDescriptor,
    htmlNodes: List<HtmlNode>,
    sitePostParser: AbstractSitePostParser,
    parserContext: PostCommentParserContext
  ): MutableList<TextPartMut> {
    if (htmlNodes.isEmpty()) {
      return mutableListOf()
    }

    val currentTextParts = mutableListWithCap<TextPartMut>(16)

    for (htmlNode in htmlNodes) {
      when (htmlNode) {
        is HtmlNode.Tag -> {
          parserContext.onTagOpened(htmlNode)

          val htmlTag = htmlNode.htmlTag
          val childTextParts = processNodes(
            postDescriptor = postDescriptor,
            htmlNodes = htmlTag.children,
            sitePostParser = sitePostParser,
            parserContext = parserContext
          )

          sitePostParser.parseHtmlNode(
            htmlTag = htmlTag,
            childTextParts = childTextParts,
            postDescriptor = postDescriptor,
            parserContext = parserContext
          )

          sitePostParser.postProcessHtmlNode(
            htmlTag = htmlTag,
            childTextParts = childTextParts,
            postDescriptor = postDescriptor,
            parserContext = parserContext
          )

          currentTextParts.addAll(childTextParts)

          parserContext.onTagClosed(htmlNode)
        }
        is HtmlNode.Text -> {
          val nodeText = if (parserContext.isInsideDivTag) {
            htmlNode.text.trim { ch -> ch.isWhitespace() }
          } else {
            htmlNode.text
          }

          currentTextParts += TextPartMut(nodeText)
        }
      }
    }

    return currentTextParts
  }

  companion object {
    private const val TAG = "PostCommentParser"
  }

}