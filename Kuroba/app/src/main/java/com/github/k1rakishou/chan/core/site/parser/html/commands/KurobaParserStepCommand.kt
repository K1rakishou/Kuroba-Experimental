package com.github.k1rakishou.chan.core.site.parser.html.commands

import com.github.k1rakishou.chan.core.site.parser.html.ExtractAttributeValues
import com.github.k1rakishou.chan.core.site.parser.html.KurobaHtmlElement
import com.github.k1rakishou.chan.core.site.parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.chan.core.site.parser.html.KurobaHtmlParserCommandExecutor
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

class KurobaParserStepCommand<T : KurobaHtmlParserCollector>(
  private val kurobaHtmlElement: KurobaHtmlElement
) : KurobaParserCommand<T> {

  @Suppress("UNCHECKED_CAST")
  fun executeStep(childNode: Node, collector: T): Boolean {
    when (val element = kurobaHtmlElement) {
      is KurobaHtmlElement.Html<*> -> {
        element as KurobaHtmlElement.Html<T>

        if (childNode is Element && childNode.tagName() == KurobaHtmlParserCommandExecutor.HTML_TAG) {
          element.extractor?.invoke(childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Head<*> -> {
        element as KurobaHtmlElement.Head<T>

        if (childNode is Element && childNode.tagName() == KurobaHtmlParserCommandExecutor.HEAD_TAG) {
          element.extractor?.invoke(childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Body<*> -> {
        element as KurobaHtmlElement.Body<T>

        if (childNode is Element && childNode.tagName() == KurobaHtmlParserCommandExecutor.BODY_TAG) {
          element.extractor?.invoke(childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Noscript<*> -> {
        element as KurobaHtmlElement.Noscript<T>

        if (matchNoscriptInternal(element, childNode)) {
          element.extractor?.invoke(childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Article<*> -> {
        element as KurobaHtmlElement.Article<T>

        if (childNode is Element && childNode.tagName() == KurobaHtmlParserCommandExecutor.ARTICLE_TAG) {
          element.extractor?.invoke(childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Header<*> -> {
        element as KurobaHtmlElement.Header<T>

        if (childNode is Element && childNode.tagName() == KurobaHtmlParserCommandExecutor.HEADER_TAG) {
          element.extractor?.invoke(childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Div<*> -> {
        element as KurobaHtmlElement.Div<T>

        if (matchDivInternal(element, childNode)) {
          element.extractor?.invoke(childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Meta<*> -> {
        element as KurobaHtmlElement.Meta<T>

        if (matchMetaInternal(element, childNode)) {
          val extractedAttrValues = element.attr?.extractAttributeValues(childNode as Element)
            ?: ExtractAttributeValues()

          element.extractor?.invoke(childNode, extractedAttrValues, collector)
          return true
        }
      }
      is KurobaHtmlElement.Heading<*> -> {
        element as KurobaHtmlElement.Heading<T>

        if (matchHeadingInternal(element, childNode)) {
          val extractedAttrValues = element.attr?.extractAttributeValues(childNode as Element)
            ?: ExtractAttributeValues()

          element.extractor?.invoke(childNode, extractedAttrValues, collector)
          return true
        }
      }
      is KurobaHtmlElement.A<*> -> {
        element as KurobaHtmlElement.A<T>

        if (matchATagInternal(element, childNode)) {
          val extractedAttrValues = element.attr.extractAttributeValues(childNode)

          element.extractor?.invoke(childNode, extractedAttrValues, collector)
          return true
        }
      }
      else -> throw IllegalAccessException("Unknown kurobaHtmlElement: ${element.javaClass.simpleName}")
    }

    return false
  }

  private fun matchATagInternal(
    aTag: KurobaHtmlElement.A<*>,
    childNode: Node
  ): Boolean {
    if (childNode is TextNode && !aTag.attr.hasAttributesToCheck() && aTag.attr.hasExtractText()) {
      return true
    }

    if (childNode !is Element || childNode.tagName() != KurobaHtmlParserCommandExecutor.A_TAG) {
      return false
    }

    val matches = aTag.attr.matches(childNode)
    if (!matches) {
      return false
    }

    return matches
  }

  private fun matchHeadingInternal(
    heading: KurobaHtmlElement.Heading<*>,
    childNode: Node
  ): Boolean {
    val fullHeadingTagName = "${KurobaHtmlParserCommandExecutor.HEADING_TAG}${heading.headingNum}"

    if (childNode !is Element || childNode.tagName() != fullHeadingTagName) {
      return false
    }

    var matches = false

    if (heading.attr != null) {
      matches = heading.attr.matches(childNode)
      if (!matches) {
        return false
      }
    }

    return matches
  }

  private fun matchMetaInternal(
    meta: KurobaHtmlElement.Meta<T>,
    childNode: Node
  ): Boolean {
    if (childNode !is Element || childNode.tagName() != KurobaHtmlParserCommandExecutor.META_TAG) {
      return false
    }

    var matches = false

    if (meta.attr != null) {
      matches = meta.attr!!.matches(childNode)
      if (!matches) {
        return false
      }
    }

    return matches
  }

  private fun matchNoscriptInternal(
    noscript: KurobaHtmlElement.Noscript<T>,
    childNode: Node
  ): Boolean {
    if (childNode !is Element || childNode.tagName() != KurobaHtmlParserCommandExecutor.NOSCRIPT_TAG) {
      return false
    }

    if (noscript.isEmpty() && childNode.attributes().isEmpty) {
      return true
    }

    var matches = false

    if (noscript.className != null) {
      matches = noscript.className!!
        .matches(childNode.attr(KurobaHtmlParserCommandExecutor.CLASS_ATTR))

      if (!matches) {
        return false
      }
    }

    return matches
  }

  private fun matchDivInternal(
    div: KurobaHtmlElement.Div<T>,
    childNode: Node
  ): Boolean {
    if (childNode !is Element || childNode.tagName() != KurobaHtmlParserCommandExecutor.DIV_TAG) {
      return false
    }

    var matches = false

    if (div.className != null) {
      matches = div.className!!
        .matches(childNode.attr(KurobaHtmlParserCommandExecutor.CLASS_ATTR))

      if (!matches) {
        return false
      }
    }

    if (div.style != null) {
      matches = div.style!!
        .matches(childNode.attr(KurobaHtmlParserCommandExecutor.STYLE_ATTR))

      if (!matches) {
        return false
      }
    }

    if (div.id != null) {
      matches = div.id!!
        .matches(childNode.attr(KurobaHtmlParserCommandExecutor.ID_ATTR))

      if (!matches) {
        return false
      }
    }

    return matches
  }

  override fun toString(): String = "StepCommand{htmlElement=${kurobaHtmlElement.javaClass.simpleName}}"

}