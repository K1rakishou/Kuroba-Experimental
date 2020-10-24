package com.github.k1rakishou.chan.core.site.parser.html

data class ExtractAttributeValues(
  val extractAttributeValues: Map<String?, String> = emptyMap()
) {

  fun getText(): String? = extractAttributeValues[null]
  fun getAttrValue(attrKey: String): String? = extractAttributeValues[attrKey]

}