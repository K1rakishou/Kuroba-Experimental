package com.github.k1rakishou.core_parser.html.commands

import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.Matchable

class KurobaConditionElseBranchCommand<T : KurobaHtmlParserCollector>(
  val conditionId: Int,
  val conditionMatchables: List<Matchable>
) : KurobaParserCommand<T> {
  override fun toString(): String {
    return "KurobaConditionElseBranchCommand(conditionId=$conditionId, conditionMatchables=$conditionMatchables)"
  }
}