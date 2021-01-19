package com.github.k1rakishou.core_parser.html

interface Matchable

data class PatternMatchable(val attrName: String, val matcher: KurobaMatcher.PatternMatcher) : Matchable

data class TagMatchable(val matcher: KurobaMatcher.TagMatcher) : Matchable

class MatchableBuilder {
  private val matchables = mutableListOf<Matchable>()

  @KurobaHtmlParserDsl
  fun className(patternMatcher: KurobaMatcher.PatternMatcher): MatchableBuilder {
    matchables += PatternMatchable(KurobaHtmlParserCommandExecutor.CLASS_ATTR, patternMatcher)
    return this
  }

  @KurobaHtmlParserDsl
  fun style(patternMatcher: KurobaMatcher.PatternMatcher): MatchableBuilder {
    matchables += PatternMatchable(KurobaHtmlParserCommandExecutor.STYLE_ATTR, patternMatcher)
    return this
  }

  @KurobaHtmlParserDsl
  fun id(patternMatcher: KurobaMatcher.PatternMatcher): MatchableBuilder {
    matchables += PatternMatchable(KurobaHtmlParserCommandExecutor.ID_ATTR, patternMatcher)
    return this
  }

  @KurobaHtmlParserDsl
  fun attr(attrName: String, matcher: KurobaMatcher.PatternMatcher): MatchableBuilder {
    matchables += PatternMatchable(attrName, matcher)
    return this
  }

  @KurobaHtmlParserDsl
  fun tag(tagMatcher: KurobaMatcher.TagMatcher): MatchableBuilder {
    matchables += TagMatchable(tagMatcher)
    return this
  }

  @KurobaHtmlParserDsl
  fun emptyTag(): MatchableBuilder {
    return tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher())
  }

  @KurobaHtmlParserDsl
  fun anyTag(): MatchableBuilder {
    return tag(KurobaMatcher.TagMatcher.tagAnyAttributeMatcher())
  }

  fun build(): List<Matchable> {
    check(matchables.isNotEmpty()) { "Matchables are empty" }
    return matchables
  }
}
