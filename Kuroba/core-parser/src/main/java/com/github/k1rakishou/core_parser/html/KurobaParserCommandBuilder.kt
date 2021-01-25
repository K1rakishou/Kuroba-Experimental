package com.github.k1rakishou.core_parser.html

import com.github.k1rakishou.core_parser.html.commands.KurobaBeginConditionCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaBeginLoopCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaBreakpointCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaCommandPopState
import com.github.k1rakishou.core_parser.html.commands.KurobaCommandPushState
import com.github.k1rakishou.core_parser.html.commands.KurobaConditionElseBranchCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaEndConditionCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaEndLoopCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaParserCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaParserCommandGroup
import com.github.k1rakishou.core_parser.html.commands.KurobaParserStepCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaPeekCollectorCommand
import org.jsoup.nodes.Node
import java.util.concurrent.atomic.AtomicInteger

class KurobaParserCommandBuilder<T : KurobaHtmlParserCollector>(
  private val groupName: String?
) {
  private val parserCommands = mutableListOf<KurobaParserCommand<T>>()

  @KurobaHtmlParserDsl
  fun peekCollector(collectorAccessor: (T) -> Unit): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaPeekCollectorCommand(collectorAccessor)
    return this
  }

  @KurobaHtmlParserDsl
  fun nest(
    builder: KurobaParserCommandBuilder<T>.() -> KurobaParserCommandBuilder<T>
  ): KurobaParserCommandBuilder<T> {
    return nest(null, builder)
  }

  /**
   * nest() command basically means to take child nodes of the current node and start iterating them.
   * By default we iterate everything sequentially without going deep into the node tree.
   * */
  @KurobaHtmlParserDsl
  fun nest(
    commandGroupName: String?,
    builder: KurobaParserCommandBuilder<T>.() -> KurobaParserCommandBuilder<T>
  ): KurobaParserCommandBuilder<T> {
    val commandGroup = builder(KurobaParserCommandBuilder(commandGroupName)).build()

    parserCommands += KurobaCommandPushState(commandGroup.groupName)
    parserCommands.addAll(commandGroup.innerCommands)
    parserCommands += KurobaCommandPopState()

    return this
  }

  /**
   * A regular if condition. If [predicate] matches any of the nodes among nodes on the current
   * tree node level then all commands produced by [builder] will be executed.
   * Otherwise they will be skipped and the next command after the [executeIf] will be executed.
   *
   * [resetNodeIndex] When true, after finding a node that matches [predicate] the node index
   * will be reset to the beginning of the current node list. The default behavior will continue with
   * the node that comes the next after the matched node.
   * */
  @KurobaHtmlParserDsl
  fun executeIf(
    predicate: MatchableBuilder.() -> MatchableBuilder,
    resetNodeIndex: Boolean = false,
    builder: KurobaParserCommandBuilder<T>.() -> KurobaParserCommandBuilder<T>
  ): KurobaParserCommandBuilder<T> {
    val commandGroup = builder(KurobaParserCommandBuilder(null)).build()
    val conditionId = nextCommandId()
    val conditionMatchables = predicate.invoke(MatchableBuilder()).build()

    parserCommands += KurobaBeginConditionCommand(conditionId, conditionMatchables, resetNodeIndex)
    parserCommands.addAll(commandGroup.innerCommands)
    parserCommands += KurobaEndConditionCommand(conditionId)

    return this
  }

  @KurobaHtmlParserDsl
  fun executeIfElse(
    predicate: MatchableBuilder.() -> MatchableBuilder,
    resetNodeIndex: Boolean = false,
    ifBranchBuilder: KurobaParserCommandBuilder<T>.() -> KurobaParserCommandBuilder<T>,
    elseBranchBuilder: KurobaParserCommandBuilder<T>.() -> KurobaParserCommandBuilder<T>
  ): KurobaParserCommandBuilder<T> {
    val ifBranchCommandGroup = ifBranchBuilder(KurobaParserCommandBuilder(null)).build()
    val elseBranchCommandGroup = elseBranchBuilder(KurobaParserCommandBuilder(null)).build()

    val conditionId = nextCommandId()
    val conditionMatchables = predicate.invoke(MatchableBuilder()).build()

    parserCommands += KurobaBeginConditionCommand(conditionId, conditionMatchables, resetNodeIndex)
    parserCommands.addAll(ifBranchCommandGroup.innerCommands)
    parserCommands += KurobaConditionElseBranchCommand(conditionId, conditionMatchables)
    parserCommands.addAll(elseBranchCommandGroup.innerCommands)
    parserCommands += KurobaEndConditionCommand(conditionId)

    return this
  }

  @KurobaHtmlParserDsl
  fun loop(
    builder: KurobaParserCommandBuilder<T>.() -> KurobaParserCommandBuilder<T>
  ): KurobaParserCommandBuilder<T> {
    return loopWhile(predicate = { anyTag() }, builder)
  }

  /**
   * loopWhile() command will iterate commands produced by [builder] while [predicate] returns true.
   * If you want to iterate while we have child nodes that we not iterated yet left then use [loop].
   * [loopWhile] does not go deeper into the nodes tree. Use [nest] to go 1 layer deeper into the
   * nodes tree.
   * */
  @KurobaHtmlParserDsl
  fun loopWhile(
    predicate: MatchableBuilder.() -> MatchableBuilder,
    builder: KurobaParserCommandBuilder<T>.() -> KurobaParserCommandBuilder<T>
  ): KurobaParserCommandBuilder<T> {
    val commandGroup = builder(KurobaParserCommandBuilder(null)).build()
    val loopId = nextCommandId()
    val predicateMatchables = predicate.invoke(MatchableBuilder()).build()

    parserCommands += KurobaBeginLoopCommand(loopId, predicateMatchables)
    parserCommands.addAll(commandGroup.innerCommands)
    parserCommands += KurobaEndLoopCommand(loopId)

    return this
  }

  @KurobaHtmlParserDsl
  fun breakpoint(): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaBreakpointCommand<T>()
    return this
  }

  @KurobaHtmlParserDsl
  fun tag(
    tagName: String,
    matchableBuilderFunc: MatchableBuilder.() -> MatchableBuilder,
    attrExtractorBuilderFunc: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder)? = null,
    extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaParserCommandBuilder<T> {
    val matchables = matchableBuilderFunc.invoke(MatchableBuilder()).build()
    check(matchables.isNotEmpty()) { "builder returned empty list" }

    parserCommands += KurobaHtmlElement.Tag(
      tagName = tagName,
      extractor = createExtractorWithAttr(attrExtractorBuilderFunc, extractorFunc)
    ).apply {
      matchables.forEach { matchable ->
        withMatchable<KurobaHtmlElement.Tag<T>>(matchable)
      }
    }

    return this
  }

  @KurobaHtmlParserDsl
  fun html(extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaHtmlElement.Html(createSimpleExtractor(extractorFunc))
    return this
  }

  @KurobaHtmlParserDsl
  fun head(extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaHtmlElement.Head(createSimpleExtractor(extractorFunc))
    return this
  }

  @KurobaHtmlParserDsl
  fun body(extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaHtmlElement.Body(createSimpleExtractor(extractorFunc))
    return this
  }

  @KurobaHtmlParserDsl
  fun article(
    matchableBuilderFunc: MatchableBuilder.() -> MatchableBuilder = { MatchableBuilder().anyTag() },
    attrExtractorBuilderFunc: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder)? = null,
    extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaHtmlElement.Article(createExtractorWithAttr(attrExtractorBuilderFunc, extractorFunc)).apply {
      val matchables = matchableBuilderFunc.invoke(MatchableBuilder()).build()

      matchables.forEach { matchable ->
        withMatchable<KurobaHtmlElement.Article<T>>(matchable)
      }
    }

    return this
  }

  @KurobaHtmlParserDsl
  fun header(
    matchableBuilderFunc: MatchableBuilder.() -> MatchableBuilder = { MatchableBuilder().anyTag() },
    extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaHtmlElement.Header(createSimpleExtractor(extractorFunc)).apply {
      val matchables = matchableBuilderFunc.invoke(MatchableBuilder()).build()

      matchables.forEach { matchable ->
        withMatchable<KurobaHtmlElement.Article<T>>(matchable)
      }
    }

    return this
  }

  @KurobaHtmlParserDsl
  fun heading(
    headingNum: Int,
    matchableBuilderFunc: MatchableBuilder.() -> MatchableBuilder = { MatchableBuilder().anyTag() },
    attrExtractorBuilderFunc: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder)? = null,
    extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaHtmlElement.Heading(
      headingNum,
      createExtractorWithAttr(attrExtractorBuilderFunc, extractorFunc)
    ).apply {
      val matchables = matchableBuilderFunc.invoke(MatchableBuilder()).build()

      matchables.forEach { matchable ->
        withMatchable<KurobaHtmlElement.Article<T>>(matchable)
      }
    }

    return this
  }

  @KurobaHtmlParserDsl
  fun a(
    matchableBuilderFunc: MatchableBuilder.() -> MatchableBuilder = { MatchableBuilder().anyTag() },
    attrExtractorBuilderFunc: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder)? = null,
    extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaHtmlElement.A(
      createExtractorWithAttr(attrExtractorBuilderFunc, extractorFunc)
    ).apply {
      val matchables = matchableBuilderFunc.invoke(MatchableBuilder()).build()

      matchables.forEach { matchable ->
        withMatchable<KurobaHtmlElement.Div<T>>(matchable)
      }
    }

    return this
  }

  @KurobaHtmlParserDsl
  fun meta(
    attrExtractorBuilderFunc: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder)? = null,
    extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaHtmlElement.Meta<T>(createExtractorWithAttr(attrExtractorBuilderFunc, extractorFunc))
    return this
  }

  @KurobaHtmlParserDsl
  fun noscript(
    matchableBuilderFunc: MatchableBuilder.() -> MatchableBuilder = { MatchableBuilder().anyTag() },
    extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaHtmlElement.Noscript(createSimpleExtractor(extractorFunc)).apply {
      val matchables = matchableBuilderFunc.invoke(MatchableBuilder()).build()

      matchables.forEach { matchable ->
        withMatchable<KurobaHtmlElement.Div<T>>(matchable)
      }
    }

    return this
  }

  @Deprecated(message = "This is an old version of the div() method which is now only used in tests. Use div() with builder {} parameter instead")
  @KurobaHtmlParserDsl
  fun div(
    className: KurobaMatcher.PatternMatcher? = null,
    style: KurobaMatcher.PatternMatcher? = null,
    id: KurobaMatcher.PatternMatcher? = null,
    extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaParserCommandBuilder<T> {
    if (className == null && style == null && id == null) {
      throw IllegalAccessException("div() all parameters are null")
    }

    parserCommands += KurobaHtmlElement.Div(createSimpleExtractor(extractorFunc)).apply {
      if (className != null) {
        withClass<KurobaHtmlElement.Div<T>>(className)
      }

      if (style != null) {
        withStyle<KurobaHtmlElement.Div<T>>(style)
      }

      if (id != null) {
        withId<KurobaHtmlElement.Div<T>>(id)
      }
    }

    return this
  }

  @KurobaHtmlParserDsl
  fun div(
    matchableBuilderFunc: MatchableBuilder.() -> MatchableBuilder = { MatchableBuilder().anyTag() },
    attrExtractorBuilderFunc: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder)? = null,
    extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaHtmlElement.Div(
      createExtractorWithAttr(attrExtractorBuilderFunc, extractorFunc)
    ).apply {
      val matchables = matchableBuilderFunc.invoke(MatchableBuilder()).build()

      matchables.forEach { matchable ->
        withMatchable<KurobaHtmlElement.Div<T>>(matchable)
      }
    }

    return this
  }

  @KurobaHtmlParserDsl
  fun span(
    matchableBuilderFunc: MatchableBuilder.() -> MatchableBuilder = { MatchableBuilder().anyTag() },
    attrExtractorBuilderFunc: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder)? = null,
    extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaHtmlElement.Span<T>(
      createExtractorWithAttr(attrExtractorBuilderFunc, extractorFunc)
    ).apply {
      val matchables = matchableBuilderFunc.invoke(MatchableBuilder()).build()

      matchables.forEach { matchable ->
        withMatchable<KurobaHtmlElement.Div<T>>(matchable)
      }
    }

    return this
  }

  @KurobaHtmlParserDsl
  fun script(
    matchableBuilderFunc: MatchableBuilder.() -> MatchableBuilder = { MatchableBuilder().anyTag() },
    attrExtractorBuilderFunc: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder),
    extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaHtmlElement.Script<T>(
      createExtractorWithAttr(attrExtractorBuilderFunc, extractorFunc)
    ).apply {
      val matchables = matchableBuilderFunc.invoke(MatchableBuilder()).build()

      matchables.forEach { matchable ->
        withMatchable<KurobaHtmlElement.Div<T>>(matchable)
      }
    }
    return this
  }

  @KurobaHtmlParserDsl
  fun title(
    attrExtractorBuilderFunc: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder),
    extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)? = null
  ): KurobaParserCommandBuilder<T> {
    parserCommands += KurobaHtmlElement.Title<T>(createExtractorWithAttr(attrExtractorBuilderFunc, extractorFunc))
    return this
  }

  fun build(): KurobaParserCommandGroup<T> {
    return KurobaParserCommandGroup(groupName, parserCommands)
  }

  private fun nextCommandId() = commandIDs.getAndIncrement()

  private operator fun List<KurobaParserCommand<T>>.plusAssign(kurobaHtmlElement: KurobaHtmlElement) {
    parserCommands += KurobaParserStepCommand<T>(kurobaHtmlElement)
  }

  private fun createExtractorWithAttr(
    attrExtractorBuilderFunc: (KurobaAttributeBuilder.() -> KurobaAttributeBuilder)?,
    extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)?
  ): Extractor<T>? {
    if (attrExtractorBuilderFunc == null && extractorFunc == null) {
      return null
    }

    return Extractor(
      extractorParams = attrExtractorBuilderFunc?.invoke(KurobaAttributeBuilder())?.build()
        ?: KurobaAttributeExtractorParams.empty(),
      extractionFunc = extractorFunc
    )
  }

  private fun createSimpleExtractor(
    extractorFunc: ((Node, ExtractedAttributeValues, T) -> Unit)?
  ): Extractor<T>? {
    return extractorFunc?.let { ef ->
      return@let Extractor(
        extractorParams = KurobaAttributeExtractorParams.empty(),
        extractionFunc = ef
      )
    }
  }

  companion object {
    private var commandIDs = AtomicInteger(0)
  }

}