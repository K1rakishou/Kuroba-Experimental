package com.github.k1rakishou.core_parser.html.commands

import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.Matchable

class KurobaBeginConditionCommand<T : KurobaHtmlParserCollector>(
  val conditionId: Int,
  val conditionMatchables: List<Matchable>,
  val resetNodeIndexToStart: Boolean
) : KurobaParserCommand<T> {
  override fun toString(): String {
    return "KurobaBeginConditionCommand(conditionId=$conditionId, conditionMatchables=$conditionMatchables, " +
      "resetNodeIndexToStart=$resetNodeIndexToStart)"
  }
}