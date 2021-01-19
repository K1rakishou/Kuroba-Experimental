package com.github.k1rakishou.core_parser.html

import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object KurobaHtmlParserUtils {
  fun filterEmptyNodes(childNodes: List<Node>): List<Node> {
    return childNodes.filter { node -> node !is TextNode || !node.isBlank }
  }
}