package com.github.k1rakishou.core_parser.comment

import com.github.k1rakishou.common.mutableListWithCap
import org.jsoup.parser.Parser

/**
 * Not thread safe!
 * */
class HtmlParser {

  fun parse(html: String): HtmlDocument {
    try {
      val nodes = parseInternal(html = html, start = 0).nodes
      return HtmlDocument(nodes)
    } catch (error: Throwable) {
      throw ParsingException("Failed to parse '$html'", error)
    }
  }

  private fun parseInternal(parentNode: HtmlNode? = null, html: String, start: Int): ParseResult {
    var localOffset = start
    var tagIndex = 0

    val outNodes = mutableListWithCap<HtmlNode>(4)
    val currentBuffer = mutableListWithCap<Char>(32)

    while (localOffset < html.length) {
      val currChar = html[localOffset]

      if (currChar == '<') {
        if (currentBuffer.size > 0) {
          val text = String(currentBuffer.toCharArray())
          val textUnescaped = Parser.unescapeEntities(text, false)

          addNewTextNode(parentNode, outNodes, textUnescaped)
          currentBuffer.clear()
        }

        ++localOffset

        val nextChar = html[localOffset]
        if (nextChar == '/') {
          val offset = skipTagEnd(html, localOffset)
          localOffset = offset

          return ParseResult(outNodes, localOffset)
        }

        val parseNodeResult = parseNode(parentNode, html, localOffset, tagIndex)
        outNodes.add(parseNodeResult.htmlNode)

        localOffset = parseNodeResult.offset
        ++tagIndex

        val htmlNode = parseNodeResult.htmlNode

        // Skip any '\n' symbols after <br> tag
        if (htmlNode is HtmlNode.Tag && htmlNode.htmlTag.tagName == "br") {
          while (html.getOrNull(localOffset) == '\n') {
            ++localOffset
          }
        }

        continue
      }

      currentBuffer.add(currChar)
      ++localOffset
    }

    if (currentBuffer.size > 0) {
      val text = String(currentBuffer.toCharArray())
      val textUnescaped = Parser.unescapeEntities(text, false)

      addNewTextNode(parentNode, outNodes, textUnescaped)
      currentBuffer.clear()
    }

    return ParseResult(outNodes, localOffset)
  }

  private fun addNewTextNode(parentNode: HtmlNode?, outNodes: MutableList<HtmlNode>, textUnescaped: String) {
    val lastNode = outNodes.lastOrNull()
    val isLastNodeVoid = (lastNode as? HtmlNode.Tag)?.htmlTag?.isVoidElement == true
    val emptyOrNewLineCharacter = textUnescaped.trim().let { text -> text.isEmpty() || (text.length == 1 && text[0] == '\n') }

    if (lastNode == null || !isLastNodeVoid || !emptyOrNewLineCharacter) {
      outNodes.add(HtmlNode.Text(textUnescaped, parentNode))
    }
  }

  private fun parseNode(parentNode: HtmlNode?, html: String, start: Int, tagIndex: Int): ParseNodeResult {
    var localOffset = start
    val tagRaw = mutableListWithCap<Char>(32)

    while (localOffset < html.length) {
      val ch = html[localOffset]
      if (ch == '>') {
        break
      }

      tagRaw.add(ch)
      ++localOffset
    }

    // Skip the ">"
    ++localOffset

    val htmlNodeTag = createHtmlTag(parentNode, tagRaw.toCharArray(), tagIndex)
    if (htmlNodeTag.htmlTag.isVoidElement) {
      return ParseNodeResult(htmlNodeTag, localOffset)
    }

    val parseResult = parseInternal(htmlNodeTag, html, localOffset)

    val updatedHtmlTag = HtmlTag(
      index = tagIndex,
      parentNode = parentNode,
      tagName = htmlNodeTag.htmlTag.tagName,
      attributes = htmlNodeTag.htmlTag.attributes,
      children = parseResult.nodes,
      isVoidElement = false
    )

    return ParseNodeResult(HtmlNode.Tag(updatedHtmlTag), parseResult.offset)
  }

  private fun createHtmlTag(parentNode: HtmlNode?, tagRaw: CharArray, tagIndex: Int): HtmlNode.Tag {
    val tagParts = splitIntoPartsBySeparator(tagRaw, separator = ' ')
    if (tagParts.isEmpty()) {
      throw ParsingException("tagParts is empty! tagRaw=${tagRaw.joinToString()}")
    }

    var tagNameMaybe: CharArray? = null
    val attributes = mutableListWithCap<HtmlAttribute>(4)

    for (tagPart in tagParts) {
      if (!tagPart.contains('=')) {
        if (tagNameMaybe == null) {
          tagNameMaybe = tagPart
        } else {
          attributes.add(HtmlAttribute(tagPart, charArrayOf()))
        }

        continue
      }

      val attributeSplitList = splitIntoPartsBySeparator(tagPart, '=')
      val attrName = attributeSplitList.getOrNull(0) ?: CharArray(0)
      var attrValue = attributeSplitList?.getOrNull(1) ?: CharArray(0)

      if (attrName.isEmpty() || attrValue.isEmpty()) {
        continue
      }

      val firstCh = attrValue.getOrNull(0)
        ?: continue
      val secondCh = attrValue.getOrNull(1)

      if (firstCh == '\\' && secondCh == '\"') {
        attrValue = attrValue.copyOfRange(2, attrValue.size)
      } else if (firstCh == '\"') {
        attrValue = attrValue.copyOfRange(1, attrValue.size)
      }

      val lastCh = attrValue.getOrNull(attrValue.lastIndex)
        ?: continue
      val secondToLastCh = attrValue.getOrNull(attrValue.lastIndex - 1)

      if (secondToLastCh == '\\' && lastCh == '\"') {
        attrValue = attrValue.copyOfRange(0, attrValue.size - 2)
      } else if (lastCh == '\"') {
        attrValue = attrValue.copyOfRange(0, attrValue.size - 1)
      }

      attributes.add(HtmlAttribute(attrName, attrValue))
    }

    if (tagNameMaybe == null || tagNameMaybe.isEmpty()) {
      throw ParsingException("Tag has no name!")
    }

    val tagName = String(tagNameMaybe)
    val isVoidElement = VOID_TAGS.contains(tagName)

    return HtmlNode.Tag(
      HtmlTag(
        index = tagIndex,
        parentNode = parentNode,
        tagName = tagName,
        attributes = attributes,
        children = mutableListWithCap(4),
        isVoidElement = isVoidElement
      )
    )
  }

  private fun splitIntoPartsBySeparator(tagRaw: CharArray, separator: Char): List<CharArray> {
    var isInsideString = false
    var offset = 0
    val tagParts = mutableListWithCap<CharArray>(4)
    val currentTagPart = mutableListWithCap<Char>(32)

    while (offset < tagRaw.size) {
      val prevCh = tagRaw.getOrNull(offset - 1)
      val currentCh = tagRaw[offset]
      val nextCh = tagRaw.getOrNull(offset + 1)

      if (currentCh == '\"' && prevCh != '\\') {
        isInsideString = isInsideString.not()
      } else if (currentCh == '\\' && nextCh == '\"') {
        isInsideString = isInsideString.not()
      }

      if (currentCh == separator && !isInsideString && nextCh != '/') {
        tagParts.add(currentTagPart.toCharArray())
        currentTagPart.clear()

        ++offset
        continue
      }

      if (!isInsideString && (currentCh == '/' || currentCh.isWhitespace())) {
        ++offset
        continue
      }

      currentTagPart.add(currentCh)
      ++offset
    }

    if (currentTagPart.size > 0) {
      tagParts.add(currentTagPart.toCharArray())
      currentTagPart.clear()
    }

    return tagParts
  }

  private fun skipTagEnd(html: String, start: Int): Int {
    var localOffset = start

    while (localOffset < html.length) {
      val ch = html[localOffset]
      if (ch == '>') {
        return localOffset + 1
      }

      ++localOffset
    }

    throw ParsingException("Failed to find tag end")
  }

  fun debugConcatIntoString(nodes: List<HtmlNode>): String {
    val resultString = StringBuilder(64)

    debugConcatIntoStringInternal(
      nodes = nodes,
      iterator = { nodeString -> resultString.append("${nodeString}\n") }
    )

    return resultString.toString()
  }

  fun debugConcatIntoStringInternal(nodes: List<HtmlNode>, iterator: (String) -> Unit) {
    for (node in nodes) {
      when (node) {
        is HtmlNode.Text -> iterator(node.text)
        is HtmlNode.Tag -> {
          iterator("<${node.htmlTag.tagName}${debugFormatAttributes(node.htmlTag.attributes)}>")
          debugConcatIntoStringInternal(node.htmlTag.children, iterator)
        }
      }
    }
  }

  private fun debugFormatAttributes(attributes: List<HtmlAttribute>): String {
    if (attributes.isEmpty()) {
      return ""
    }

    val resultString = StringBuilder(64)

    for (attribute in attributes) {
      resultString
        .append(", ")
        .append(attribute.name)

      if (attribute.value.isNotEmpty()) {
        resultString
          .append('=')
          .append(attribute.value)
      }
    }

    return resultString.toString()
  }

  class ParseResult(
    val nodes: List<HtmlNode>,
    val offset: Int
  )

  class ParseNodeResult(
    val htmlNode: HtmlNode,
    val offset: Int
  )

  class ParsingException(message: String, cause: Throwable? = null) : Exception(message, cause)

  companion object {
    private const val TAG = "HtmlParser"

    private val VOID_TAGS = mutableSetOf(
      "area",
      "base",
      "br",
      "wbr",
      "col",
      "hr",
      "img",
      "input",
      "link",
      "meta",
      "param",
    )
  }
}