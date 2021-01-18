package com.github.k1rakishou.core_parser.html.commands

import com.github.k1rakishou.core_parser.html.KurobaHtmlElement
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import com.github.k1rakishou.core_parser.html.PatternMatchable
import com.github.k1rakishou.core_parser.html.TagMatchable
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

class KurobaParserStepCommand<T : KurobaHtmlParserCollector>(
  private val kurobaHtmlElement: KurobaHtmlElement
) : KurobaParserCommand<T> {

  @Suppress("UNCHECKED_CAST")
  fun executeStep(childNode: Node, collector: T): Boolean {
    when (val element = kurobaHtmlElement) {
      is KurobaHtmlElement.Html<*> -> {
        element as KurobaHtmlElement.Html<T>

        if (childNode is Element && childNode.tagName() == KurobaHtmlParserCommandExecutor.HTML_TAG) {
          runExtractorIfPresent(element, childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Head<*> -> {
        element as KurobaHtmlElement.Head<T>

        if (childNode is Element && childNode.tagName() == KurobaHtmlParserCommandExecutor.HEAD_TAG) {
          runExtractorIfPresent(element, childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Body<*> -> {
        element as KurobaHtmlElement.Body<T>

        if (childNode is Element && childNode.tagName() == KurobaHtmlParserCommandExecutor.BODY_TAG) {
          runExtractorIfPresent(element, childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Noscript<*> -> {
        element as KurobaHtmlElement.Noscript<T>

        if (matchElementInternal(element, childNode)) {
          runExtractorIfPresent(element, childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Article<*> -> {
        element as KurobaHtmlElement.Article<T>

        if (childNode is Element && childNode.tagName() == KurobaHtmlParserCommandExecutor.ARTICLE_TAG) {
          runExtractorIfPresent(element, childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Header<*> -> {
        element as KurobaHtmlElement.Header<T>

        if (childNode is Element && childNode.tagName() == KurobaHtmlParserCommandExecutor.HEADER_TAG) {
          runExtractorIfPresent(element, childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Div<*> -> {
        element as KurobaHtmlElement.Div<T>

        if (matchElementInternal(element, childNode)) {
          runExtractorIfPresent(element, childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Meta<*> -> {
        element as KurobaHtmlElement.Meta<T>

        if (matchElementInternal(element, childNode)) {
          runExtractorIfPresent(element, childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Span<*> -> {
        element as KurobaHtmlElement.Span<T>

        if (matchElementInternal(element, childNode)) {
          runExtractorIfPresent(element, childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Script<*> -> {
        element as KurobaHtmlElement.Script<T>

        if (matchElementInternal(element, childNode)) {
          runExtractorIfPresent(element, childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Heading<*> -> {
        element as KurobaHtmlElement.Heading<T>

        if (matchElementInternal(element, childNode)) {
          runExtractorIfPresent(element, childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.A<*> -> {
        element as KurobaHtmlElement.A<T>

        if (matchElementInternal(element, childNode)) {
          runExtractorIfPresent(element, childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Title<*> -> {
        element as KurobaHtmlElement.Title<T>

        if (matchElementInternal(element, childNode)) {
          runExtractorIfPresent(element, childNode, collector)
          return true
        }
      }
      is KurobaHtmlElement.Tag<*> -> {
        element as KurobaHtmlElement.Tag<T>

        if (matchElementInternal(element, childNode)) {
          runExtractorIfPresent(element, childNode, collector)
          return true
        }
      }
      else -> throw IllegalAccessException("Unknown kurobaHtmlElement: ${element.javaClass.simpleName}")
    }

    return false
  }

  private fun runExtractorIfPresent(
    element: KurobaHtmlElement.Tag<T>,
    childNode: Node,
    collector: T
  ) {
    element.extractor?.let { extractor ->
      val extractedAttrValues = extractor.extractorParams.extractAttributeValues(childNode)

      extractor.extractionFunc?.invoke(
        childNode,
        extractedAttrValues,
        collector
      )
    }
  }

  private fun matchElementInternal(
    tag: KurobaHtmlElement.Tag<T>,
    childNode: Node
  ): Boolean {
    if (tag.isEmpty() && !childNode.attributes().isEmpty) {
      return false
    }

    if (childNode !is Element || childNode.tagName() != tag.tagName) {
      return false
    }

    for (matchable in tag.matchables) {
      when (matchable) {
        is PatternMatchable -> {
          if (!matchable.matcher.matches(childNode.attr(matchable.attrName))) {
            return false
          }
        }
        is TagMatchable -> {
          if (!matchable.matcher.matches(childNode)) {
            return false
          }
        }
        else -> throw IllegalStateException("Unexpected matcher: ${matchable.javaClass.simpleName}")
      }
    }

    if (tag.extractor != null && tag.extractor.extractorParams.hasAttributesToCheck()) {
      if (!tag.extractor.extractorParams.matches(childNode)) {
        return false
      }

      // fallthrough
    }

    return true
  }

  override fun toString(): String = "StepCommand{htmlElement=${kurobaHtmlElement}}"

}