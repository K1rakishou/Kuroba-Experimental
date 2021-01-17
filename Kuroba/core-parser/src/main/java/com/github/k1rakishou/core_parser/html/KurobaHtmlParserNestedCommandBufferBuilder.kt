package com.github.k1rakishou.core_parser.html

import com.github.k1rakishou.core_parser.html.commands.KurobaBeginLoopCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaBreakpointCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaCommandPopState
import com.github.k1rakishou.core_parser.html.commands.KurobaCommandPushState
import com.github.k1rakishou.core_parser.html.commands.KurobaEndLoopCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaParserCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaParserCommandGroup
import com.github.k1rakishou.core_parser.html.commands.KurobaParserStepCommand
import org.jsoup.nodes.Node

class KurobaHtmlParserNestedCommandBufferBuilder<T : KurobaHtmlParserCollector>(
  private val groupName: String?
) {
  private val parserCommands = mutableListOf<KurobaParserCommand<T>>()
  private var loopIds = 0

  fun nest(
    builder: KurobaHtmlParserNestedCommandBufferBuilder<T>.() -> KurobaHtmlParserNestedCommandBufferBuilder<T>
  ): KurobaHtmlParserNestedCommandBufferBuilder<T> {
    return nest(null, builder)
  }

  /**
   * nest() command basically means to take child nodes of the current node and start iterating them.
   * By default we iterate everything sequentially without going deep into the node tree.
   * */
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

  fun loop(
    builder: KurobaHtmlParserNestedCommandBufferBuilder<T>.() -> KurobaHtmlParserNestedCommandBufferBuilder<T>
  ): KurobaHtmlParserNestedCommandBufferBuilder<T> {
    return loopWhile(predicate = { true }, builder)
  }

  /**
   * loopWhile() command will iterate commands produced by [builder] while [predicate] returns true.
   * If you want to iterate while we have child nodes that we not iterated yet left then use [loop].
   * [loopWhile] does not go deeper into the nodes tree. Use [nest] to go 1 layer deeper into the
   * nodes tree.
   * */
  fun loopWhile(
    predicate: (Node) -> Boolean,
    builder: KurobaHtmlParserNestedCommandBufferBuilder<T>.() -> KurobaHtmlParserNestedCommandBufferBuilder<T>
  ): KurobaHtmlParserNestedCommandBufferBuilder<T> {
    val commandGroup = builder(KurobaHtmlParserNestedCommandBufferBuilder(null)).build()
    val loopId = loopIds++

    parserCommands += KurobaBeginLoopCommand(loopId, predicate)
    parserCommands.addAll(commandGroup.innerCommands)
    parserCommands += KurobaEndLoopCommand(loopId)

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