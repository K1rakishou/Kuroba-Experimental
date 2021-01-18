package com.github.k1rakishou.core_parser.html

import com.github.k1rakishou.core_parser.html.commands.KurobaParserCommand

class KurobaHtmlParserCommandBufferBuilder<T : KurobaHtmlParserCollector> {
  private val parserCommands = mutableListOf<KurobaParserCommand<T>>()

  fun start(
    builder: KurobaParserCommandBuilder<T>.() -> KurobaParserCommandBuilder<T>
  ): KurobaHtmlParserCommandBufferBuilder<T> {
    return start(null, builder)
  }

  fun start(
    groupName: String?,
    builder: KurobaParserCommandBuilder<T>.() -> KurobaParserCommandBuilder<T>
  ): KurobaHtmlParserCommandBufferBuilder<T> {
    val commandGroup = builder(KurobaParserCommandBuilder(groupName)).build()
    parserCommands.addAll(commandGroup.innerCommands)
    return this
  }

  fun build(): List<KurobaParserCommand<T>> {
    return parserCommands
  }

}