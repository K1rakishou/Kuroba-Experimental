package com.github.k1rakishou.core_parser.html

data class ExtractedAttributeValues(
  val extractAttributeValues: Map<String?, String> = emptyMap()
) {

  fun getText(): String? = extractAttributeValues[null]
  fun getAttrValue(attrKey: String): String? = extractAttributeValues[attrKey]

}