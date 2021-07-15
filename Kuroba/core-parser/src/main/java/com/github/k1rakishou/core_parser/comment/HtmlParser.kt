package com.github.k1rakishou.core_parser.comment

import com.github.k1rakishou.common.mutableListWithCap

/**
 * Not thread safe!
 * */
class HtmlParser {

  fun parse(html: String): HtmlDocument {
    val nodes = parseInternal(html = html, start = 0).nodes
    return HtmlDocument(nodes)
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

          outNodes.add(HtmlNode.Text(text))
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

        continue
      }

      currentBuffer.add(currChar)
      ++localOffset
    }

    if (currentBuffer.size > 0) {
      outNodes.add(HtmlNode.Text(String(currentBuffer.toCharArray())))
      currentBuffer.clear()
    }

    return ParseResult(outNodes, localOffset)
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
        tagNameMaybe = tagPart
        continue
      }

      val attributeSplitList = splitIntoPartsBySeparator(tagPart, '=')
      val attrName = attributeSplitList[0]
      var attrValue = attributeSplitList[1]

      if (attrValue[0] == '\"') {
        attrValue = attrValue.copyOfRange(1, attrValue.lastIndex)
      }

      if (attrValue[attrValue.lastIndex] == '\"') {
        attrValue = attrValue.copyOfRange(0, attrValue.lastIndex - 1)
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
      val ch = tagRaw[offset]

      if (ch == '\"') {
        isInsideString = isInsideString.not()
      }

      if (ch == separator && !isInsideString) {
        tagParts.add(currentTagPart.toCharArray())
        currentTagPart.clear()

        ++offset
        continue
      }

      currentTagPart.add(ch)
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
        .append('=')
        .append(attribute.value)
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

  class ParsingException(message: String) : Exception(message)

  companion object {
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