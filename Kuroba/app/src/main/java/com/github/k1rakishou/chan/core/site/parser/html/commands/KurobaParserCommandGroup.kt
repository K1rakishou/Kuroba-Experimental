package com.github.k1rakishou.chan.core.site.parser.html.commands

import com.github.k1rakishou.chan.core.site.parser.html.KurobaHtmlParserCollector

class KurobaParserCommandGroup<T : KurobaHtmlParserCollector>(
  val groupName: String,
  val commands: List<KurobaParserCommand<T>>
) : KurobaParserCommand<T> {
  override fun toString(): String = "CommandGroup{groupName=$groupName, commandsCount=${commands.size}}"
}