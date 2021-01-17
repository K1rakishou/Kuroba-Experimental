package com.github.k1rakishou.core_parser.html.commands

import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector

class KurobaParserCommandGroup<T : KurobaHtmlParserCollector>(
  val groupName: String?,
  val innerCommands: List<KurobaParserCommand<T>>
)