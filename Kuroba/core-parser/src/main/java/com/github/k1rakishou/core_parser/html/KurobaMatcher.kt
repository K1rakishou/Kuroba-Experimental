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

    companion object {
      fun tagNoAttributesMatcher(): KurobaMatcher.TagMatcher = KurobaEmptyTagMatcher
      fun tagAnyAttributeMatcher(): KurobaMatcher.TagMatcher = KurobaAnyTagMatcher
    }
  }

  sealed class PatternMatcher : KurobaMatcher() {
    abstract fun matches(input: String): Boolean

    object KurobaAlwaysAcceptMatcher : PatternMatcher() {
      override fun matches(input: String): Boolean = true
      override fun toString(): String = "DummyMatcher"
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