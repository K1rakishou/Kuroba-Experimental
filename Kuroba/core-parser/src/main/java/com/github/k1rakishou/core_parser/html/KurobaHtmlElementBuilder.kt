package com.github.k1rakishou.core_parser.html

import org.jsoup.nodes.Node

class KurobaHtmlElementBuilder<T : KurobaHtmlParserCollector> {
  fun html(extractor: ((Node, T) -> Unit)? = null): KurobaHtmlElement.Html<T> {
    return KurobaHtmlElement.Html(extractor)
  }

  fun head(extractor: ((Node, T) -> Unit)? = null): KurobaHtmlElement.Head<T> {
    return KurobaHtmlElement.Head(extractor)
  }

  fun body(extractor: ((Node, T) -> Unit)? = null): KurobaHtmlElement.Body<T> {
    return KurobaHtmlElement.Body(extractor)
  }

  fun article(extractor: ((Node, T) -> Unit)? = null): KurobaHtmlElement.Article<T> {
    return KurobaHtmlElement.Article(extractor)
  }

  fun header(extractor: ((Node, T) -> Unit)? = null): KurobaHtmlElement.Header<T> {
    return KurobaHtmlElement.Header(extractor)
  }

  fun heading(
    headingNum: Int,
    attr: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder)? = null,
    extractor: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaHtmlElement.Heading<T> {
    return KurobaHtmlElement.Heading(
      headingNum,
      attr?.invoke(KurobaAttributeBuilder())?.build(),
      extractor
    )
  }

  fun a(
    attr: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder),
    extractor: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaHtmlElement.A<T> {
    return KurobaHtmlElement.A(
      attr.invoke(KurobaAttributeBuilder()).build(),
      extractor
    )
  }

  fun meta(
    attr: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder)? = null,
    extractor: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaHtmlElement.Meta<T> {
    return KurobaHtmlElement.Meta<T>().apply {
      if (attr != null) {
        withAttr(attr(KurobaAttributeBuilder()).build())
      }

      if (extractor != null) {
        withExtractor(extractor)
      }
    }
  }

  fun noscript(
    className: KurobaMatcher? = null,
    extractor: ((Node, T) -> Unit)? = null
  ): KurobaHtmlElement.Noscript<T> {
    return KurobaHtmlElement.Noscript(extractor).apply {
      if (className != null) {
        withClass(className)
      }
    }
  }

  fun div(
    className: KurobaMatcher? = null,
    style: KurobaMatcher? = null,
    id: KurobaMatcher? = null,
    extractor: ((Node, T) -> Unit)? = null
  ): KurobaHtmlElement.Div<T> {
    if (className == null && style == null && id == null) {
      throw IllegalAccessException("div() all parameters are null")
    }

    return KurobaHtmlElement.Div(extractor).apply {
      if (className != null) {
        withClass(className)
      }

      if (style != null) {
        withStyle(style)
      }

      if (id != null) {
        withId(id)
      }
    }
  }

  fun span(
    attr: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder),
    extractor: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaHtmlElement.Span<T> {
    return KurobaHtmlElement.Span<T>(
      attr(KurobaAttributeBuilder()).build(),
      extractor
    )
  }

  fun script(
    attr: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder),
    extractor: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaHtmlElement.Script<T> {
    return KurobaHtmlElement.Script<T>(
      attr(KurobaAttributeBuilder()).build(),
      extractor
    )
  }

  fun title(
    attr: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder),
    extractor: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaHtmlElement.Title<T> {
    return KurobaHtmlElement.Title<T>(
      attr(KurobaAttributeBuilder()).build(),
      extractor
    )
  }

}