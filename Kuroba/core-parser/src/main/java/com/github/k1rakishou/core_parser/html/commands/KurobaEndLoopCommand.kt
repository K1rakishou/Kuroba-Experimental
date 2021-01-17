package com.github.k1rakishou.core_parser.html.commands

import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector

class KurobaEndLoopCommand<T : KurobaHtmlParserCollector>(
  val loopId: Int
) : KurobaParserCommand<T> {
  override fun toString(): String = "KurobaEndLoopCommand(loopId=$loopId)"
}