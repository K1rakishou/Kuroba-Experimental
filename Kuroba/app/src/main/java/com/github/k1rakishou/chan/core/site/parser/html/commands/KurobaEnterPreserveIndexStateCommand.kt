package com.github.k1rakishou.chan.core.site.parser.html.commands

import com.github.k1rakishou.chan.core.site.parser.html.KurobaHtmlParserCollector

class KurobaEnterPreserveIndexStateCommand<T : KurobaHtmlParserCollector> : KurobaParserCommand<T> {
  override fun toString(): String = "KurobaEnterPreserveIndexStateCommand"
}