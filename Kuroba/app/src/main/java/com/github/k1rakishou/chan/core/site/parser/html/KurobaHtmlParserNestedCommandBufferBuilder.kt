package com.github.k1rakishou.chan.core.site.parser.html

import com.github.k1rakishou.chan.core.site.parser.html.commands.*

class KurobaHtmlParserNestedCommandBufferBuilder<T : KurobaHtmlParserCollector>(
  private val groupName: String
) {
  private val parserCommands = mutableListOf<KurobaParserCommand<T>>()

  fun breakpoint(): KurobaHtmlParserNestedCommandBufferBuilder<T> {
    parserCommands += KurobaBreakpointCommand()
    return this
  }

  fun preserveCommandIndex(
    builder: KurobaHtmlParserNestedCommandBufferBuilder<T>.() -> KurobaHtmlParserNestedCommandBufferBuilder<T>
  ): KurobaHtmlParserNestedCommandBufferBuilder<T> {
    parserCommands += KurobaEnterPreserveIndexStateCommand<T>()
    parserCommands += builder(KurobaHtmlParserNestedCommandBufferBuilder(groupName)).build()
    parserCommands += KurobaExitPreserveIndexStateCommand<T>()
    return this
  }

  fun group(
    groupName: String,
    builder: KurobaHtmlParserNestedCommandBufferBuilder<T>.() -> KurobaHtmlParserNestedCommandBufferBuilder<T>
  ): KurobaHtmlParserNestedCommandBufferBuilder<T> {
    parserCommands += KurobaParserPushStateCommand<T>()
    parserCommands += builder(KurobaHtmlParserNestedCommandBufferBuilder(groupName)).build()
    parserCommands += KurobaParserPopStateCommand<T>()
    return this
  }

  fun htmlElement(
    builder: KurobaHtmlElementBuilder<T>.() -> KurobaHtmlElement
  ): KurobaHtmlParserNestedCommandBufferBuilder<T> {
    parserCommands += KurobaParserStepCommand(
      builder.invoke(KurobaHtmlElementBuilder())
    )

    return this
  }

  fun build(): KurobaParserCommandGroup<T> {
    return KurobaParserCommandGroup(groupName, parserCommands)
  }

}