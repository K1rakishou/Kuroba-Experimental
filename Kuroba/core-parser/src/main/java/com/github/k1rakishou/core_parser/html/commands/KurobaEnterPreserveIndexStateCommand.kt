package com.github.k1rakishou.core_parser.html.commands

import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector

class KurobaEnterPreserveIndexStateCommand<T : KurobaHtmlParserCollector> : KurobaParserCommand<T> {
  override fun toString(): String = "KurobaEnterPreserveIndexStateCommand"
}