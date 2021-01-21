package com.github.k1rakishou.core_parser.html

class KurobaAttributeBuilder {
  private val checkAttributeKeysMap = mutableMapOf<String, KurobaMatcher>()
  private val extractAttributeValues = mutableSetOf<IExtractable>()

  @KurobaHtmlParserDsl
  fun expectAttrWithValue(attrKey: String, attrValue: KurobaMatcher): KurobaAttributeBuilder {
    check(!checkAttributeKeysMap.containsKey(attrKey)) {
      "checkAttributeKeysMap already contains attrKey: ${attrKey}"
    }

    checkAttributeKeysMap[attrKey] = attrValue
    return this
  }

  @KurobaHtmlParserDsl
  fun expectAttr(attrKey: String): KurobaAttributeBuilder {
    check(!checkAttributeKeysMap.containsKey(attrKey)) {
      "checkAttributeKeysMap already contains attrKey: ${attrKey}"
    }

    checkAttributeKeysMap[attrKey] = KurobaMatcher.PatternMatcher.alwaysAccept()
    return this
  }

  @KurobaHtmlParserDsl
  fun extractAttrValueByKey(attrKey: String): KurobaAttributeBuilder {
    val extractable = ExtractAttribute(AttributeKey(attrKey))

    check(!extractAttributeValues.contains(extractable)) {
      "extractAttributeValues already contains extractable: ${extractable}"
    }

    extractAttributeValues += extractable
    return this
  }

  @KurobaHtmlParserDsl
  fun extractAttrValueByAnyKey(attrKeys: List<String>): KurobaAttributeBuilder {
    val extractable = ExtractAnyAttributeOf(attrKeys.map { attrKey -> AttributeKey(attrKey) })

    check(!extractAttributeValues.contains(extractable)) {
      "extractAttributeValues already contains extractable: ${extractable}"
    }

    extractAttributeValues += extractable
    return this
  }

  @KurobaHtmlParserDsl
  fun extractText(): KurobaAttributeBuilder {
    extractAttributeValues += ExtractText
    return this
  }

  @KurobaHtmlParserDsl
  fun extractHtml(): KurobaAttributeBuilder {
    extractAttributeValues += ExtractHtmlAsText
    return this
  }

  fun build(): KurobaAttributeExtractorParams {
    return KurobaAttributeExtractorParams(
      checkAttributeKeysMap = checkAttributeKeysMap,
      extractAttributeValues = extractAttributeValues
    )
  }
}

interface IExtractable

data class ExtractAttribute(val attrKey: AttributeKey) : IExtractable

class ExtractAnyAttributeOf(val attrKeys: List<AttributeKey>) : IExtractable {
  override fun toString(): String {
    return "ExtractAnyAttributeOf{$attrKeys}"
  }
}

object ExtractText : IExtractable {
  override fun toString(): String = "ExtractText"
}

object ExtractHtmlAsText : IExtractable {
  override fun toString(): String = "ExtractHtmlAsText"
}