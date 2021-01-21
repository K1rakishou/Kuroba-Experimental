package com.github.k1rakishou.core_parser.html

import android.util.Log
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

class KurobaAttributeExtractorParams(
  private val checkAttributeKeysMap: Map<String, KurobaMatcher>,
  private val extractAttributeValues: Set<IExtractable>
) {

  fun hasAttributesToCheck(): Boolean = checkAttributeKeysMap.isNotEmpty()
  fun hasExtractText(): Boolean = extractAttributeValues.any { extractable -> extractable is ExtractText }

  fun matches(element: Element): Boolean {
    return checkAttributeKeysMap.all { (attributeKey, expectedValueMatcher) ->
      when (expectedValueMatcher) {
        is KurobaMatcher.TagMatcher -> {
          return@all expectedValueMatcher.matches(element)
        }
        is KurobaMatcher.PatternMatcher -> {
          return@all expectedValueMatcher.matches(element.attr(attributeKey))
        }
      }
    }
  }

  fun extractAttributeValues(childNode: Node): ExtractedAttributeValues {
    if (extractAttributeValues.isEmpty()) {
      return ExtractedAttributeValues()
    }

    val resultMap = mutableMapOf<Extractable, String?>()

    extractAttributeValues.forEach { extractable ->
      when (extractable) {
        is ExtractAttribute -> {
          resultMap[extractable.attrKey] = childNode.attrOrNull(extractable.attrKey.key)
        }
        is ExtractAnyAttributeOf -> {
          for (attrKey in extractable.attrKeys) {
            val attrValue = childNode.attrOrNull(attrKey.key)

            if (attrValue != null) {
              resultMap[attrKey] = attrValue
              break
            }
          }
        }
        ExtractText -> {
          if (childNode is TextNode) {
            resultMap[ExtractWholeText] = childNode.text()
            return@forEach
          }

          val childNodes = (childNode as Element).childNodes()
          if (childNodes.isEmpty()) {
            resultMap[ExtractWholeText] = null
            return@forEach
          }

          if (childNodes.size > 1) {
            Log.e(TAG, "Failed to parse Text from node ${childNode.javaClass.simpleName} " +
              "because children count > 1, childrenCount = ${childNode.childrenSize()} " +
              "nodeText = ${childNode.text()}")
            return@forEach
          }

          val textNode = childNodes.firstOrNull() as? TextNode
          if (textNode == null) {
            Log.e(TAG, "Failed to parse Text from node ${childNode.javaClass.simpleName} " +
              "because it is not a TextNode, nodeText = ${childNode.text()}")
            return@forEach
          }

          resultMap[ExtractWholeText] = textNode.text()
        }
        ExtractHtmlAsText -> {
          if (childNode is TextNode) {
            resultMap[ExtractHtml] = childNode.outerHtml()
            return@forEach
          }

          val childNodes = KurobaHtmlParserUtils.filterEmptyNodes((childNode as Element).childNodes())
          if (childNodes.isEmpty()) {
            resultMap[ExtractHtml] = null
            return@forEach
          }

          resultMap[ExtractHtml] = childNodes.joinToString(
            separator = "\n",
            transform = { node -> node.outerHtml() }
          )
        }
      }
    }

    return ExtractedAttributeValues(resultMap)
  }

  private fun Node.attrOrNull(attrKey: String): String? {
    if (!hasAttr(attrKey)) {
      return null
    }

    return attr(attrKey)
  }

  override fun toString(): String {
    return "KurobaAttribute{checkAttributeKeysMap=${checkAttributeKeysMap}, " +
      "extractAttributeValues=${extractAttributeValues}}"
  }

  companion object {
    private const val TAG = "KurobaAttribute"

    fun empty() = KurobaAttributeExtractorParams(emptyMap(), emptySet())
  }
}