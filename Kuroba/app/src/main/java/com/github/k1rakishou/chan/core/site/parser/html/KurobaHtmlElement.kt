package com.github.k1rakishou.chan.core.site.parser.html

import org.jsoup.nodes.Node

sealed class KurobaHtmlElement {

  abstract class AbstractElementWithExtractor<T : KurobaHtmlParserCollector>(
    val extractor: ((Node, T) -> Unit)?
  ) : KurobaHtmlElement()

  class Html<T : KurobaHtmlParserCollector>(
    extractor: ((Node, T) -> Unit)?
  ) : AbstractElementWithExtractor<T>(extractor) {
    override fun toString(): String = "html"
  }

  class Head<T : KurobaHtmlParserCollector>(
    extractor: ((Node, T) -> Unit)?
  ) : AbstractElementWithExtractor<T>(extractor) {
    override fun toString(): String = "head"
  }

  class Body<T : KurobaHtmlParserCollector>(
    extractor: ((Node, T) -> Unit)?
  ) : AbstractElementWithExtractor<T>(extractor) {
    override fun toString(): String = "body"
  }

  class Noscript<T : KurobaHtmlParserCollector>(
    extractor: ((Node, T) -> Unit)?
  ) : AbstractElementWithExtractor<T>(extractor) {
    var className: KurobaMatcher? = null
      private set

    fun isEmpty(): Boolean {
      return className == null
    }

    fun withClass(className: KurobaMatcher): Noscript<T> {
      this.className = className
      return this
    }

    override fun toString(): String = "noscript{empty=${className?.toString()}}"
  }

  class Meta<T : KurobaHtmlParserCollector> : KurobaHtmlElement() {
    var attr: KurobaAttribute? = null
    var extractor: ((Node, ExtractAttributeValues, T) -> Unit)? = null

    fun withAttr(attr: KurobaAttribute): Meta<T> {
      this.attr = attr
      return this
    }

    fun withExtractor(extractor: ((Node, ExtractAttributeValues, T) -> Unit)? = null): Meta<T> {
      this.extractor = extractor
      return this
    }

    override fun toString(): String {
      return "meta{attr=${attr}}"
    }
  }

  class Article<T : KurobaHtmlParserCollector>(
    extractor: ((Node, T) -> Unit)?
  ) : AbstractElementWithExtractor<T>(extractor) {
    override fun toString(): String = "article"
  }

  class Header<T : KurobaHtmlParserCollector>(
    extractor: ((Node, T) -> Unit)?
  ) : AbstractElementWithExtractor<T>(extractor) {
    override fun toString(): String = "header"
  }

  class Heading<T : KurobaHtmlParserCollector>(
    val headingNum: Int,
    val attr: KurobaAttribute?,
    val extractor: ((Node, ExtractAttributeValues, T) -> Unit)? = null
  ) : KurobaHtmlElement() {
    override fun toString(): String = "h${headingNum}"
  }

  class Div<T : KurobaHtmlParserCollector>(
    extractor: ((Node, T) -> Unit)?
  ) : AbstractElementWithExtractor<T>(extractor) {
    var className: KurobaMatcher? = null
      private set
    var style: KurobaMatcher? = null
      private set
    var id: KurobaMatcher? = null
      private set

    fun withClass(className: KurobaMatcher): Div<T> {
      this.className = className
      return this
    }

    fun withStyle(style: KurobaMatcher): Div<T> {
      this.style = style
      return this
    }

    fun withId(id: KurobaMatcher): Div<T> {
      this.id = id
      return this
    }

    override fun toString(): String {
      return "div{className=${className?.toString()}, style=${style?.toString()}, id=${id?.toString()}}"
    }
  }

  class Span<T : KurobaHtmlParserCollector>(
    val attr: KurobaAttribute,
    val extractor: ((Node, ExtractAttributeValues, T) -> Unit)? = null
  ) : KurobaHtmlElement() {
    override fun toString(): String = "span"
  }

  class Script<T : KurobaHtmlParserCollector>(
    val attr: KurobaAttribute,
    val extractor: ((Node, ExtractAttributeValues, T) -> Unit)? = null
  ) : KurobaHtmlElement() {
    override fun toString(): String = "script"
  }

  class A<T : KurobaHtmlParserCollector>(
    val attr: KurobaAttribute,
    val extractor: ((Node, ExtractAttributeValues, T) -> Unit)?
  ) : KurobaHtmlElement() {
    override fun toString(): String = "a"
  }

}
