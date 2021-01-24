package com.github.k1rakishou.core_parser.html.commands

import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.Matchable

class KurobaBeginLoopCommand<T : KurobaHtmlParserCollector>(
  val loopId: Int,
  val matchables: List<Matchable>
) : KurobaParserCommand<T> {
  override fun toString(): String = "KurobaBeginLoopCommand(loopId=$loopId, matchables.size=${matchables.size})"
}