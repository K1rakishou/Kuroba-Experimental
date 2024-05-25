package com.github.k1rakishou.core_parser.comment

class HtmlParserPool {
  private val htmlParserThreadLocal = ThreadLocal<HtmlParser>()

  fun get(): HtmlParser {
    var htmlParser = htmlParserThreadLocal.get()
    if (htmlParser == null) {
      htmlParserThreadLocal.set(HtmlParser())
      htmlParser = htmlParserThreadLocal.get()
    }

    return htmlParser!!
  }

}