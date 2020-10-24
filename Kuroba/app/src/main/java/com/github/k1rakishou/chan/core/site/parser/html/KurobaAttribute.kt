package com.github.k1rakishou.chan.core.site.parser.html

import com.github.k1rakishou.chan.utils.Logger
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

class KurobaAttribute(
  private val checkAttributeKeysMap: Map<String, KurobaMatcher>,
  private val extractAttributeValues: Set<IExtractable>
) {

  fun hasAttributesToCheck(): Boolean = checkAttributeKeysMap.isNotEmpty()
  fun hasExtractText(): Boolean = extractAttributeValues.any { extractable -> extractable is ExtractText }

  fun matches(element: Element): Boolean {
    return checkAttributeKeysMap.all { (attributeKey, expectedValueMatcher) ->
      expectedValueMatcher.matches(element.attr(attributeKey))
    }
  }

  fun extractAttributeValues(childNode: Node): ExtractAttributeValues {
    if (extractAttributeValues.isEmpty()) {
      return ExtractAttributeValues()
    }

    val resultMap = mutableMapOf<String?, String>()

    extractAttributeValues.forEach { extractable ->
      when (extractable) {
        is ExtractAttribute -> {
          resultMap[extractable.attrKey] = (childNode as Element).attr(extractable.attrKey)
        }
        ExtractText -> {
          if (childNode is TextNode) {
            resultMap[null] = childNode.text()
            return@forEach
          }

          val childNodes = (childNode as Element).childNodes()
          if (childNodes.size != 1) {
            Logger.e(TAG, "Failed to parse Text from node ${childNode.javaClass.simpleName} " +
              "because children count != 1, childrenCount = ${childNode.childrenSize()} " +
              "nodeText = ${childNode.text()}")
            return@forEach
          }

          val textNode = childNodes.firstOrNull() as? TextNode
          if (textNode == null) {
            Logger.e(TAG, "Failed to parse Text from node ${childNode.javaClass.simpleName} " +
              "because it is not a TextNode, nodeText = ${childNode.text()}")
            return@forEach
          }

          resultMap[null] = textNode.text()
        }
      }
    }

    return ExtractAttributeValues(resultMap)
  }

  override fun toString(): String {
    return "KurobaAttribute{checkAttributeKeysMap=${checkAttributeKeysMap}, " +
      "extractAttributeValues=${extractAttributeValues}}"
  }

  companion object {
    private const val TAG = "KurobaAttribute"
  }
}