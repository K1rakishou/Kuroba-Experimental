package com.github.k1rakishou.core_parser.html

sealed class KurobaHtmlElement {

  @Suppress("UNCHECKED_CAST")
  open class Tag<T : KurobaHtmlParserCollector>(
    val tagName: String,
    val matchables: MutableList<Matchable> = mutableListOf(),
    val extractor: Extractor<T>? = null
  ) : KurobaHtmlElement() {

    fun <ChildTag : Tag<T>> withMatchable(matchable: Matchable): ChildTag {
      matchables += matchable
      return this as ChildTag
    }

    fun <ChildTag : Tag<T>> withAttr(attrName: String, matcher: KurobaMatcher.PatternMatcher): ChildTag {
      return withMatchable(PatternMatchable(attrName, matcher))
    }

    fun <ChildTag : Tag<T>> withClass(matcher: KurobaMatcher.PatternMatcher): ChildTag {
      return withAttr(KurobaHtmlParserCommandExecutor.CLASS_ATTR, matcher)
    }

    fun <ChildTag : Tag<T>> withStyle(matcher: KurobaMatcher.PatternMatcher): ChildTag {
      return withAttr(KurobaHtmlParserCommandExecutor.STYLE_ATTR, matcher)
    }

    fun <ChildTag : Tag<T>> withId(matcher: KurobaMatcher.PatternMatcher): ChildTag {
      return withAttr(KurobaHtmlParserCommandExecutor.ID_ATTR, matcher)
    }

    fun isEmpty(): Boolean {
      return matchables.isEmpty() && extractor?.extractorParams?.hasAttributesToCheck() == false
    }

    override fun toString(): String = "$tagName{matchables=${matchables}, extractor=$extractor}"
  }

  class Html<T : KurobaHtmlParserCollector>(
    extractor: Extractor<T>?
  ) : Tag<T>(
    tagName = KurobaHtmlParserCommandExecutor.HTML_TAG,
    extractor = extractor
  )

  class Head<T : KurobaHtmlParserCollector>(
    extractor: Extractor<T>?
  ) : Tag<T>(
    tagName = KurobaHtmlParserCommandExecutor.HEAD_TAG,
    extractor = extractor
  )

  class Body<T : KurobaHtmlParserCollector>(
    extractor: Extractor<T>?
  ) : Tag<T>(
    tagName = KurobaHtmlParserCommandExecutor.BODY_TAG,
    extractor = extractor
  )

  class Noscript<T : KurobaHtmlParserCollector>(
    extractor: Extractor<T>?
  ) : Tag<T>(
    tagName = KurobaHtmlParserCommandExecutor.NOSCRIPT_TAG,
    extractor = extractor
  )

  class Meta<T : KurobaHtmlParserCollector>(
    extractor: Extractor<T>?
  ) : Tag<T>(
    tagName = KurobaHtmlParserCommandExecutor.META_TAG,
    extractor = extractor
  )

  class Article<T : KurobaHtmlParserCollector>(
    extractor: Extractor<T>?
  ) : Tag<T>(
    tagName = KurobaHtmlParserCommandExecutor.ARTICLE_TAG,
    extractor = extractor
  )

  class Header<T : KurobaHtmlParserCollector>(
    extractor: Extractor<T>?
  ) : Tag<T>(
    tagName = KurobaHtmlParserCommandExecutor.HEADER_TAG,
    extractor = extractor
  )

  class Heading<T : KurobaHtmlParserCollector>(
    headingNum: Int,
    extractor: Extractor<T>?
  ) : Tag<T>(
    tagName = headingTagName(headingNum),
    extractor = extractor
  ) {
    companion object {
      private fun headingTagName(headingNum: Int) =
        KurobaHtmlParserCommandExecutor.HEADING_TAG + headingNum
    }
  }

  class Div<T : KurobaHtmlParserCollector>(
    extractor: Extractor<T>?
  ) : Tag<T>(
    tagName = KurobaHtmlParserCommandExecutor.DIV_TAG,
    extractor = extractor
  )

  class Span<T : KurobaHtmlParserCollector>(
    extractor: Extractor<T>?
  ) : Tag<T>(
    tagName = KurobaHtmlParserCommandExecutor.SPAN_TAG,
    extractor = extractor
  )

  class Script<T : KurobaHtmlParserCollector>(
    extractor: Extractor<T>?
  ) : Tag<T>(
    tagName = KurobaHtmlParserCommandExecutor.SCRIPT_TAG,
    extractor = extractor
  )

  class Title<T : KurobaHtmlParserCollector>(
    extractor: Extractor<T>?
  ) : Tag<T>(
    tagName = KurobaHtmlParserCommandExecutor.TITLE_TAG,
    extractor = extractor
  )

  class A<T : KurobaHtmlParserCollector>(
    extractor: Extractor<T>?
  ) : Tag<T>(
    tagName = KurobaHtmlParserCommandExecutor.A_TAG,
    extractor = extractor
  )

}
