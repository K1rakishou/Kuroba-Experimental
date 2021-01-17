package com.github.k1rakishou.core_parser.html.commands

import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import org.jsoup.nodes.Node

class KurobaBeginLoopCommand<T : KurobaHtmlParserCollector>(
  val loopId: Int,
  val predicate: (Node) -> Boolean
) : KurobaParserCommand<T> {
  override fun toString(): String = "KurobaBeginLoopCommand(loopId=$loopId)"
}