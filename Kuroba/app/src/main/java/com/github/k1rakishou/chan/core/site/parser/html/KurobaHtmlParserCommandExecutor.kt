package com.github.k1rakishou.chan.core.site.parser.html

import com.github.k1rakishou.chan.core.site.parser.html.commands.*
import com.github.k1rakishou.chan.utils.Logger
import org.jsoup.nodes.Document
import org.jsoup.nodes.Node
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * This class is designed to be single threaded!
 * If you want concurrency - create separate instances per each thread!
 * */
class KurobaHtmlParserCommandExecutor<T : KurobaHtmlParserCollector>(
  private val debugMode: Boolean = false
) {
  private val executionStack = Stack<ParserState>()
  private val parserContextStack = Stack<ParserContext>()

  fun executeCommands(
    document: Document,
    kurobaParserCommands: List<KurobaParserCommand<T>>,
    collector: T
  ): Boolean {
    resetParserState()
    var childNodes = document.childNodes()

    for ((parsingStep, kurobaParserCommand) in kurobaParserCommands.withIndex()) {
      val nodeIndex = AtomicInteger(0)

      childNodes = executeKurobaParserCommand(
        childNodes,
        kurobaParserCommand,
        parsingStep,
        kurobaParserCommands.lastIndex,
        collector,
        nodeIndex,
        0
      )
    }

    return true
  }

  private fun resetParserState() {
    executionStack.clear()
    parserContextStack.clear()
  }

  private fun executeKurobaParserCommand(
    childNodes: List<Node>,
    kurobaParserCommand: KurobaParserCommand<T>,
    currentParsingStepIndex: Int,
    lastParsingStepIndex: Int,
    collector: T,
    nodeIndex: AtomicInteger,
    depth: Int
  ): List<Node> {
    if (debugMode) {
      val depthIndicator = " ".repeat(depth)
      Logger.d(
        TAG, "${depthIndicator}(${currentParsingStepIndex}/${lastParsingStepIndex}, " +
          "nodes=${childNodes.size}) command=${kurobaParserCommand}"
      )
    }

    when (kurobaParserCommand) {
      is KurobaParserCommandGroup<*> -> {
        kurobaParserCommand as KurobaParserCommandGroup<T>

        var innerNodes = childNodes
        val innerNodeIndex = AtomicInteger(0)

        if (getOrCreateCurrentParserContext().preserveIndex) {
          innerNodeIndex.set(nodeIndex.get())
        }

        for ((innerStep, kurobaParserNestedCommand) in kurobaParserCommand.commands.withIndex()) {
          innerNodes = executeKurobaParserCommand(
            innerNodes,
            kurobaParserNestedCommand,
            innerStep,
            kurobaParserCommand.commands.lastIndex,
            collector,
            innerNodeIndex,
            depth + 1
          )
        }

        if (getOrCreateCurrentParserContext().preserveIndex) {
          nodeIndex.set(innerNodeIndex.get())
        }

        return innerNodes
      }
      is KurobaParserStepCommand<*> -> {
        return executeStepCommand(
          childNodes,
          kurobaParserCommand,
          currentParsingStepIndex,
          lastParsingStepIndex,
          nodeIndex,
          collector
        )
      }
      is KurobaParserPushStateCommand<*> -> {
        val parserState = ParserState(childNodes)
        executionStack.push(parserState)

        return childNodes
      }
      is KurobaParserPopStateCommand<*> -> {
        val prevParserState = executionStack.pop()
        return prevParserState.childNodes
      }
      is KurobaEnterPreserveIndexStateCommand<*> -> {
        val newContext = getCurrentParserContextOrNull()
          ?.copy(preserveIndex = true)
          ?: ParserContext(preserveIndex = true)

        parserContextStack.push(newContext)
        return childNodes
      }
      is KurobaExitPreserveIndexStateCommand<*> -> {
        parserContextStack.pop()
        return childNodes
      }
      is KurobaBreakpointCommand<*> -> {
        return childNodes
      }
      else -> throw IllegalAccessException("Unknown command: $kurobaParserCommand")
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun executeStepCommand(
    childNodes: List<Node>,
    kurobaParserCommand: KurobaParserStepCommand<*>,
    currentParsingStepIndex: Int,
    lastParsingStepIndex: Int,
    nodeIndex: AtomicInteger,
    collector: T
  ): List<Node> {
    kurobaParserCommand as KurobaParserStepCommand<T>

    while (true) {
      val childNode = childNodes.getOrNull(nodeIndex.getAndIncrement())
        ?: break

      if (kurobaParserCommand.executeStep(childNode, collector)) {
        val nextChildNodes = childNode.childNodes()

        if (!getOrCreateCurrentParserContext().preserveIndex) {
          nodeIndex.set(0)
        }

        if (childNodes.isEmpty() && currentParsingStepIndex != lastParsingStepIndex) {
          Logger.e(
            TAG, "Parser possible failure at step ${currentParsingStepIndex}, " +
              "kurobaParserCommand ($kurobaParserCommand) returned empty child node list"
          )
          return emptyList()
        }

        return nextChildNodes
      }
    }

    return emptyList()
  }

  private fun getOrCreateCurrentParserContext(): ParserContext {
    if (parserContextStack.isEmpty()) {
      parserContextStack.push(ParserContext())
    }

    return parserContextStack.peek()
  }

  private fun getCurrentParserContextOrNull(): ParserContext? {
    if (parserContextStack.isEmpty()) {
      return null
    }

    return parserContextStack.peek()
  }

  data class ParserState(
    val childNodes: List<Node>
  )

  data class ParserContext(
    val preserveIndex: Boolean = false
  )

  companion object {
    private const val TAG = "KurobaHtmlParserCommandExecutor"

    const val TAG_IMG = "img"
    const val TAG_BLOCK_QUOTE = "blockquote"
    const val TAG_STRONG = "strong"
    const val DIV_TAG = "div"
    const val FORM_TAG = "form"
    const val BODY_TAG = "body"
    const val HTML_TAG = "html"
    const val HEAD_TAG = "head"
    const val A_TAG = "a"
    const val NOSCRIPT_TAG = "noscript"
    const val ARTICLE_TAG = "article"
    const val HEADER_TAG = "header"
    const val HEADING_TAG = "h"
    const val META_TAG = "meta"

    const val CLASS_ATTR = "class"
    const val STYLE_ATTR = "style"
    const val ID_ATTR = "id"
  }
}