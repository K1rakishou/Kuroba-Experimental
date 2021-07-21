package com.github.k1rakishou.core_parser.comment

import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import org.jsoup.parser.Parser

data class HtmlDocument(
  val nodes: List<HtmlNode>
) {

  override fun toString(): String {
    return "HtmlDocument{nodesCount=${nodes.size}}"
  }

}

sealed class HtmlNode {
  fun asTagOrNull(): Tag? = this as? Tag

  fun traverse(iterator: (HtmlNode) -> Unit) {
    when (this) {
      is Text -> iterator(this)
      is Tag -> {
        iterator(this)

        for (child in htmlTag.children) {
          child.traverse(iterator)
        }
      }
    }
  }

  data class Text(val text: String) : HtmlNode()
  data class Tag(val htmlTag: HtmlTag) : HtmlNode()
}

data class HtmlTag(
  val index: Int,
  val parentNode: HtmlNode?,
  val tagName: String,
  val attributes: List<HtmlAttribute>,
  val children: List<HtmlNode>,
  val isVoidElement: Boolean
) {
  private val attributesAsMap: Map<String, String> by lazy(LazyThreadSafetyMode.NONE) {
    if (attributes.isEmpty()) {
      return@lazy emptyMap<String, String>()
    }

    val map = mutableMapWithCap<String, String>(attributes.size)

    for (attribute in attributes) {
      map[attribute.nameAsString()] = attribute.valueAsString()
    }

    return@lazy map
  }

  override fun toString(): String {
    return "HtmlTag{tagName='$tagName', attributesCount=${attributes.size}, " +
      "childrenCount=${children.size}, isVoidElement=$isVoidElement}"
  }

  fun attrOrNull(name: String): String? {
    return attributesAsMap[name]
  }

  fun attrUnescapedOrNull(name: String): String? {
    return attributesAsMap[name]?.let { attrValue -> Parser.unescapeEntities(attrValue, false) }
  }

  fun classAttrOrNull(): String? = attrOrNull(CLASS_ATTR)

  fun hasClass(classAttrValue: String): Boolean {
    return classAttrOrNull() == classAttrValue
  }

  /**
   * Takes parent node's children (if the current node is not root node) and checks whether there
   * is a next tag node after this one.
   * */
  fun hasNextSibling(): Boolean {
    val children = (parentNode as? HtmlNode.Tag)?.htmlTag?.children
      ?: return false

    val requiredIndex = index + 1
    var currentIndex = 0

    for (child in children) {
      if (child !is HtmlNode.Tag) {
        continue
      }

      if (currentIndex == requiredIndex) {
        return true
      }

      ++currentIndex
    }

    return false
  }

  fun text(): String {
    return textOrNull() ?: ""
  }

  fun textOrNull(): String? {
    if (children.isEmpty()) {
      return null
    }

    val resultString = StringBuilder(64)

    for (child in children) {
      child.traverse { htmlNode ->
        if (htmlNode is HtmlNode.Text) {
          resultString.append(htmlNode.text)
        }
      }
    }

    return resultString.toString()
  }

  fun getTagsByName(name: String): List<HtmlTag> {
    if (children.isEmpty()) {
      return emptyList()
    }

    val resultList = mutableListWithCap<HtmlTag>(4)

    for (child in children) {
      val tag = child.asTagOrNull()?.htmlTag
        ?: continue

      if (tag.tagName == name) {
        resultList += tag
      }
    }

    return resultList
  }

  companion object {
    private const val CLASS_ATTR = "class"
  }

}

/**
 * Not thread safe!
 * */
class HtmlAttribute(
  val name: CharArray,
  val value: CharArray
) {
  private var nameAsString: String? = null
  private var valueAsString: String? = null

  fun nameAsString(): String {
    if (nameAsString == null) {
      nameAsString = String(name)
    }

    return nameAsString!!
  }

  fun valueAsString(): String {
    if (valueAsString == null) {
      valueAsString = String(value)
    }

    return valueAsString!!
  }

}