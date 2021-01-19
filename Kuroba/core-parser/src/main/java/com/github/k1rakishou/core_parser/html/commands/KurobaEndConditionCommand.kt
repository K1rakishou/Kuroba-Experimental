package com.github.k1rakishou.core_parser.html.commands

import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector

class KurobaEndConditionCommand<T : KurobaHtmlParserCollector>(
  val conditionId: Int
) : KurobaParserCommand<T> {
  override fun toString(): String = "KurobaEndConditionCommand(conditionId=$conditionId)"
}