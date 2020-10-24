package com.github.k1rakishou.chan.core.site.parser.html

import com.github.k1rakishou.chan.core.site.parser.html.commands.KurobaParserCommand
import com.github.k1rakishou.chan.core.site.parser.html.commands.KurobaParserPopStateCommand
import com.github.k1rakishou.chan.core.site.parser.html.commands.KurobaParserPushStateCommand

class KurobaHtmlParserCommandBufferBuilder<T : KurobaHtmlParserCollector> {
  private val parserCommands = mutableListOf<KurobaParserCommand<T>>()

  fun group(
    groupName: String,
    builder: KurobaHtmlParserNestedCommandBufferBuilder<T>.() -> KurobaHtmlParserNestedCommandBufferBuilder<T>
  ): KurobaHtmlParserCommandBufferBuilder<T> {

    parserCommands += KurobaParserPushStateCommand<T>()
    parserCommands += builder(KurobaHtmlParserNestedCommandBufferBuilder(groupName)).build()
    parserCommands += KurobaParserPopStateCommand<T>()
    return this
  }

  fun build(): List<KurobaParserCommand<T>> {
    return parserCommands
  }

}