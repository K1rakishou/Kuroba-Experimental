package com.github.k1rakishou.core_parser.html.commands

import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector

class KurobaBreakpointCommand<T : KurobaHtmlParserCollector> : KurobaParserCommand<T>, IMetaCommand {
  override fun toString(): String = "BreakpointCommand"
}