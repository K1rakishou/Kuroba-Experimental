package com.github.k1rakishou.core_parser.html

import org.jsoup.nodes.Node

class Extractor<T : KurobaHtmlParserCollector>(
  val extractorParams: KurobaAttributeExtractorParams,
  val extractionFunc: ((Node, ExtractedAttributeValues, T) -> Unit)?
) {

  override fun toString(): String {
    return "Extractor{extractorParams=$extractorParams}"
  }
}