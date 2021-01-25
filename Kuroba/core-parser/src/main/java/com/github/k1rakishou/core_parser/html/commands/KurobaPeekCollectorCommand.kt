package com.github.k1rakishou.core_parser.html.commands

import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector

class KurobaPeekCollectorCommand<T : KurobaHtmlParserCollector>(
  val collectorAccessor: (T) -> Unit
) : KurobaParserCommand<T> {
  override fun toString(): String {
    return "KurobaPeekCollectorCommand"
  }
}