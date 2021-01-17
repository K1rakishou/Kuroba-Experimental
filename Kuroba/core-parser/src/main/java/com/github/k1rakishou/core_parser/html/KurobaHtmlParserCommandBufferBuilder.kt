package com.github.k1rakishou.core_parser.html

import com.github.k1rakishou.core_parser.html.commands.KurobaParserCommand

class KurobaHtmlParserCommandBufferBuilder<T : KurobaHtmlParserCollector> {
  private val parserCommands = mutableListOf<KurobaParserCommand<T>>()

  fun start(
    builder: KurobaHtmlParserNestedCommandBufferBuilder<T>.() -> KurobaHtmlParserNestedCommandBufferBuilder<T>
  ): KurobaHtmlParserCommandBufferBuilder<T> {
    return start(null, builder)
  }

  fun start(
    groupName: String?,
    builder: KurobaHtmlParserNestedCommandBufferBuilder<T>.() -> KurobaHtmlParserNestedCommandBufferBuilder<T>
  ): KurobaHtmlParserCommandBufferBuilder<T> {
    val commandGroup = builder(KurobaHtmlParserNestedCommandBufferBuilder(groupName)).build()
    parserCommands.addAll(commandGroup.innerCommands)
    return this
  }

  fun build(): List<KurobaParserCommand<T>> {
    return parserCommands
  }

}