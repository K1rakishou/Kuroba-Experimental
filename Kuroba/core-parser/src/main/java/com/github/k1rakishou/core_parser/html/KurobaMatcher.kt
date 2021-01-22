package com.github.k1rakishou.core_parser.html

import org.jsoup.nodes.Element
import java.util.regex.Pattern

sealed class KurobaMatcher {

  sealed class TagMatcher : KurobaMatcher() {
    abstract fun matches(element: Element): Boolean

    object KurobaEmptyTagMatcher : TagMatcher() {
      override fun matches(element: Element): Boolean {
        return element.attributes().isEmpty
      }

      override fun toString(): String = "KurobaEmptyTagMatcher"
    }

    object KurobaAnyTagMatcher : TagMatcher() {
      override fun matches(element: Element): Boolean {
        return true
      }

      override fun toString(): String = "KurobaAnyTagMatcher"
    }

    class KurobaHasAttributeMatcher(val attributeKey: String) : TagMatcher() {
      override fun matches(element: Element): Boolean {
        return element.hasAttr(attributeKey) && element.attr(attributeKey).isNotEmpty()
      }

      override fun toString(): String = "KurobaHasAttributeMatcher"
    }

    class KurobaHasAnyAttributeMatcher(val attributeKeys: List<String>) : TagMatcher() {
      override fun matches(element: Element): Boolean {
        for (attributeKey in attributeKeys) {
          if (element.hasAttr(attributeKey) && element.attr(attributeKey).isNotEmpty()) {
            return true
          }
        }

        return false
      }

      override fun toString(): String = "KurobaHasAttributeMatcher"
    }

    class KurobaTagWithAttributeMatcher(
      val tagName: String,
      val attributeClass: String,
      val attributeValue: String
    ) : TagMatcher() {

      override fun matches(element: Element): Boolean {
        if (tagName != element.tagName()) {
          return false
        }

        if (element.attr(attributeClass) != attributeValue) {
          return false
        }

        return true
      }

      override fun toString(): String = "KurobaTagWithAttributeMatcher{tagName='$tagName', " +
        "attributeClass='$attributeClass', attributeValue='$attributeValue'}"

    }

    companion object {
      fun tagNoAttributesMatcher(): TagMatcher = KurobaEmptyTagMatcher
      fun tagAnyAttributeMatcher(): TagMatcher = KurobaAnyTagMatcher
      fun tagHasAttribute(attributeKey: String): TagMatcher = KurobaHasAttributeMatcher(attributeKey)
      fun tagHasAnyOfAttributes(attributeKeys: List<String>) = KurobaHasAnyAttributeMatcher(attributeKeys)

      fun tagWithAttributeMatcher(tagName: String, attributeClass: String, attributeValue: String): TagMatcher {
        return KurobaTagWithAttributeMatcher(tagName, attributeClass, attributeValue)
      }
    }
  }

  sealed class PatternMatcher : KurobaMatcher() {
    abstract fun matches(input: String): Boolean

    object KurobaAlwaysAcceptMatcher : PatternMatcher() {
      override fun matches(input: String): Boolean = true
      override fun toString(): String = "KurobaAlwaysAcceptMatcher"
    }

    class KurobaStringEquals(val string: String) : PatternMatcher() {
      override fun matches(input: String): Boolean = input == string
      override fun toString(): String = "KurobaStringEquals{string=${string}}"
    }

    class KurobaStringEqualsIgnoreCase(val string: String) : PatternMatcher() {
      override fun matches(input: String): Boolean = input.equals(string, ignoreCase = true)
      override fun toString(): String = "KurobaStringEqualsIgnoreCase{string=${string}}"
    }

    class KurobaStringContains(val string: String) : PatternMatcher() {
      override fun matches(input: String): Boolean = input.contains(string)
      override fun toString(): String = "KurobaStringContains{string=${string}}"
    }

    class KurobaStringContainsIgnoreCase(val string: String) : PatternMatcher() {
      override fun matches(input: String): Boolean = input.contains(string, ignoreCase = true)
      override fun toString(): String = "KurobaStringContainsIgnoreCase{string=${string}}"
    }

    class KurobaPatternMatch(val pattern: Pattern) : PatternMatcher() {
      override fun matches(input: String): Boolean = pattern.matcher(input).matches()
      override fun toString(): String = "KurobaPatternMatch{pattern=${pattern.pattern()}}"
    }

    class KurobaPatternFind(val pattern: Pattern) : PatternMatcher() {
      override fun matches(input: String): Boolean = pattern.matcher(input).find()
      override fun toString(): String = "KurobaPatternFind{pattern=${pattern.pattern()}}"
    }

    companion object {
      fun alwaysAccept(): PatternMatcher = KurobaAlwaysAcceptMatcher

      fun stringEquals(string: String): PatternMatcher = KurobaStringEquals(string)
      fun stringEqualsIgnoreCase(string: String): PatternMatcher = KurobaStringEqualsIgnoreCase(string)

      fun stringContains(string: String): PatternMatcher = KurobaStringContains(string)
      fun stringContainsIgnoreCase(string: String): PatternMatcher = KurobaStringContainsIgnoreCase(string)

      fun patternMatch(pattern: Pattern): PatternMatcher = KurobaPatternMatch(pattern)
      fun patternFind(pattern: Pattern): PatternMatcher = KurobaPatternFind(pattern)
    }
  }

}