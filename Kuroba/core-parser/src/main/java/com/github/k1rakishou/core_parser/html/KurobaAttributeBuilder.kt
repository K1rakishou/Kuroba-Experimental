package com.github.k1rakishou.core_parser.html

class KurobaAttributeBuilder {
  private val checkAttributeKeysMap = mutableMapOf<String, KurobaMatcher>()
  private val extractAttributeValues = mutableSetOf<IExtractable>()

  fun expectAttrWithValue(attrKey: String, attrValue: KurobaMatcher): KurobaAttributeBuilder {
    check(!checkAttributeKeysMap.containsKey(attrKey)) {
      "checkAttributeKeysMap already contains attrKey: ${attrKey}"
    }

    checkAttributeKeysMap[attrKey] = attrValue
    return this
  }

  fun expectAttr(attrKey: String): KurobaAttributeBuilder {
    check(!checkAttributeKeysMap.containsKey(attrKey)) {
      "checkAttributeKeysMap already contains attrKey: ${attrKey}"
    }

    checkAttributeKeysMap[attrKey] = KurobaMatcher.alwaysAccept()
    return this
  }

  fun extractAttrValueByKey(attrKey: String): KurobaAttributeBuilder {
    val extractable = ExtractAttribute(attrKey)

    check(!extractAttributeValues.contains(extractable)) {
      "extractAttributeValues already contains attrKey: ${attrKey}"
    }

    extractAttributeValues += extractable
    return this
  }

  fun extractText(): KurobaAttributeBuilder {
    extractAttributeValues += ExtractText
    return this
  }

  fun build(): KurobaAttribute {
    return KurobaAttribute(
      checkAttributeKeysMap = checkAttributeKeysMap,
      extractAttributeValues = extractAttributeValues
    )
  }
}

interface IExtractable

data class ExtractAttribute(val attrKey: String) : IExtractable
object ExtractText : IExtractable