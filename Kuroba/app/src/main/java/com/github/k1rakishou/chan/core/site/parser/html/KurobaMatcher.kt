package com.github.k1rakishou.chan.core.site.parser.html

import java.util.regex.Pattern

sealed class KurobaMatcher {
  abstract fun matches(input: String): Boolean

  object KurobaAlwaysAcceptMatcher : KurobaMatcher() {
    override fun matches(input: String): Boolean = true
    override fun toString(): String = "DummyMatcher"
  }

  class KurobaStringEquals(val string: String) : KurobaMatcher() {
    override fun matches(input: String): Boolean = string == input
    override fun toString(): String = "KurobaStringEquals{string=${string}}"
  }

  class KurobaStringEqualsIgnoreCase(val string: String) : KurobaMatcher() {
    override fun matches(input: String): Boolean = string.equals(input, ignoreCase = true)
    override fun toString(): String = "KurobaStringEqualsIgnoreCase{string=${string}}"
  }

  class KurobaStringContains(val string: String) : KurobaMatcher() {
    override fun matches(input: String): Boolean = string.contains(input)
    override fun toString(): String = "KurobaStringContains{string=${string}}"
  }

  class KurobaStringContainsIgnoreCase(val string: String) : KurobaMatcher() {
    override fun matches(input: String): Boolean = string.contains(input, ignoreCase = true)
    override fun toString(): String = "KurobaStringContainsIgnoreCase{string=${string}}"
  }

  class KurobaPatternMatch(val pattern: Pattern) : KurobaMatcher() {
    override fun matches(input: String): Boolean = pattern.matcher(input).matches()
    override fun toString(): String = "KurobaPatternMatch{pattern=${pattern.pattern()}}"
  }

  class KurobaPatternFind(val pattern: Pattern) : KurobaMatcher() {
    override fun matches(input: String): Boolean = pattern.matcher(input).find()
    override fun toString(): String = "KurobaPatternFind{pattern=${pattern.pattern()}}"
  }

  companion object {
    fun alwaysAccept(): KurobaMatcher = KurobaAlwaysAcceptMatcher

    fun stringEquals(string: String): KurobaMatcher = KurobaStringEquals(string)
    fun stringEqualsIgnoreCase(string: String): KurobaMatcher =
      KurobaStringEqualsIgnoreCase(string)

    fun stringContains(string: String): KurobaMatcher = KurobaStringContains(string)
    fun stringContainsIgnoreCase(string: String): KurobaMatcher =
      KurobaStringContainsIgnoreCase(string)

    fun patternMatch(pattern: Pattern): KurobaMatcher = KurobaPatternMatch(pattern)
    fun patternFind(pattern: Pattern): KurobaMatcher = KurobaPatternFind(pattern)
  }

}