package com.github.k1rakishou.core_parser.html

import com.github.k1rakishou.core_parser.html.commands.KurobaBreakpointCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaCommandPopState
import com.github.k1rakishou.core_parser.html.commands.KurobaCommandPushState
import com.github.k1rakishou.core_parser.html.commands.KurobaParserCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaParserCommandGroup
import com.github.k1rakishou.core_parser.html.commands.KurobaParserStepCommand

class KurobaHtmlParserNestedCommandBufferBuilder<T : KurobaHtmlParserCollector>(
  private val groupName: String?
) {
  private val parserCommands = mutableListOf<KurobaParserCommand<T>>()

  fun nest(
    builder: KurobaHtmlParserNestedCommandBufferBuilder<T>.() -> KurobaHtmlParserNestedCommandBufferBuilder<T>
  ): KurobaHtmlParserNestedCommandBufferBuilder<T> {
    return nest(null, builder)
  }

  fun nest(
    commandGroupName: String?,
    builder: KurobaHtmlParserNestedCommandBufferBuilder<T>.() -> KurobaHtmlParserNestedCommandBufferBuilder<T>
  ): KurobaHtmlParserNestedCommandBufferBuilder<T> {
    val commandGroup = builder(KurobaHtmlParserNestedCommandBufferBuilder(commandGroupName)).build()

    parserCommands += KurobaCommandPushState(commandGroup.groupName)
    parserCommands.addAll(commandGroup.innerCommands)
    parserCommands += KurobaCommandPopState()
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

  fun breakpoint(): KurobaHtmlParserNestedCommandBufferBuilder<T> {
    parserCommands += KurobaBreakpointCommand<T>()
    return this
  }

  fun build(): KurobaParserCommandGroup<T> {
    return KurobaParserCommandGroup(groupName, parserCommands)
  }

}