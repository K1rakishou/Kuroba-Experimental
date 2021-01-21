package com.github.k1rakishou.core_parser.html

data class ExtractedAttributeValues(
  val extractAttributeValues: Map<Extractable, String?> = emptyMap()
) {

  fun getText(): String? = extractAttributeValues[ExtractWholeText]
  fun getHtml(): String? = extractAttributeValues[ExtractHtml]
  fun getAttrValue(attrKey: String): String? = extractAttributeValues[AttributeKey(attrKey)]

  fun getAnyAttrValue(attrKeys: List<String>): String? {
    for (attrKey in attrKeys) {
      val attrValue = getAttrValue(attrKey)
      if (attrValue != null) {
        return attrValue
      }
    }

    return null
  }

}

interface Extractable

data class AttributeKey(val key: String) : Extractable
data class AnyOfAttributeKeys(val keys: List<String>) : Extractable
object ExtractWholeText : Extractable
object ExtractHtml : Extractable