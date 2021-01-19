package com.github.k1rakishou.core_parser.html

data class ExtractedAttributeValues(
  val extractAttributeValues: Map<Extractable, String?> = emptyMap()
) {

  fun getText(): String? = extractAttributeValues[ExtractWholeText]
  fun getHtml(): String? = extractAttributeValues[ExtractHtml]
  fun getAttrValue(attrKey: String): String? = extractAttributeValues[AttributeKey(attrKey)]

}

interface Extractable

data class AttributeKey(val key: String) : Extractable
object ExtractWholeText : Extractable
object ExtractHtml : Extractable