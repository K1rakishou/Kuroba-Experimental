package com.github.k1rakishou.core_parser.html

import android.annotation.SuppressLint
import android.util.Log
import com.github.k1rakishou.core_parser.html.commands.KurobaBeginConditionCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaBeginLoopCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaBreakpointCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaCommandPopState
import com.github.k1rakishou.core_parser.html.commands.KurobaCommandPushState
import com.github.k1rakishou.core_parser.html.commands.KurobaConditionElseBranchCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaEndConditionCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaEndLoopCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaParserCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaParserStepCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaPeekCollectorCommand
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import java.util.*

/**
 * This class is designed to be single threaded!
 * If you want concurrency - create separate instances per each thread!
 * */
class KurobaHtmlParserCommandExecutor<T : KurobaHtmlParserCollector>(
  private val debugMode: Boolean = false
) {
  private val parserState = ParserState()

  // TODO(KurobaEx): this should return ModularResult instead of throwing an exception
  @Throws(HtmlParsingException::class)
  fun executeCommands(
    document: Document,
    kurobaParserCommands: List<KurobaParserCommand<T>>,
    collector: T
  ) {
    parserState.clear()

    if (kurobaParserCommands.isEmpty()) {
      return
    }

    executeCommandsInternal(document, kurobaParserCommands, collector)
  }

  @SuppressLint("LongLogTag")
  private fun executeCommandsInternal(
    document: Document,
    kurobaParserCommands: List<KurobaParserCommand<T>>,
    collector: T
  ) {
    var commandIndex = 0
    var nodeIndex = 0
    var nodes = KurobaHtmlParserUtils.filterEmptyNodes(document.childNodes())

    while (true) {
      if (commandIndex >= kurobaParserCommands.size) {
        break
      }

      val command = kurobaParserCommands[commandIndex]

      if (debugMode) {
        Log.d(TAG, "executeCommand commandIndex=$commandIndex, nodeIndex=$nodeIndex, command=$command")
      }

      when (command) {
        is KurobaBreakpointCommand<T> -> {
          ++commandIndex
        }
        is KurobaPeekCollectorCommand<T> -> {
          command.collectorAccessor(collector)
          ++commandIndex
        }
        is KurobaParserStepCommand<T> -> {
          val start = nodeIndex
          var executed = false

          for (index in start until nodes.size) {
            val node = nodes[index]
            ++nodeIndex

            if (command.executeStep(node, collector)) {
              executed = true
              break
            }
          }

          if (!executed) {
            val nodesDumped = buildString {
              appendLine()
              appendLine("Nodes: ")

              appendLine("{")

              for (index in start until nodes.size) {
                val node = nodes[index]
                appendLine(node.toString())
              }

              appendLine("}")
            }

            throw HtmlParsingException("Failed to execute command: $command, " +
              "nodesCount: ${nodes.size}, startIndex=$start, commandIndex=$commandIndex, $nodesDumped")
          }

          ++commandIndex
        }
        is KurobaCommandPushState<T> -> {
          parserState.parserStateStack.push(ParserNestingState(nodeIndex, nodes))

          ++commandIndex
          nodes = KurobaHtmlParserUtils.filterEmptyNodes(nodes[nodeIndex - 1].childNodes())

          nodeIndex = 0
        }
        is KurobaCommandPopState<T> -> {
          val prevParserState = parserState.parserStateStack.pop()

          nodeIndex = prevParserState.nodeIndex
          nodes = prevParserState.nodes

          ++commandIndex
        }
        is KurobaBeginLoopCommand<T> -> {
          val parserLoopState = ParserLoopState(
            // commandIndex + 1 because commandIndex is the KurobaBeginLoopCommand which we want
            // to skip on the next iteration to avoid infinite loops
            commandIndex + 1,
            command.loopId,
            command.matchables
          )

          parserState.parserLoopStack.push(parserLoopState)

          ++commandIndex
        }
        is KurobaEndLoopCommand<T> -> {
          val parserLoopState = parserState.parserLoopStack.peek()

          checkNotNull(parserLoopState) { "Empty loop stack!" }
          check(command.loopId == parserLoopState.loopId) {
            "Bad loopId! Expected=${command.loopId}, actual=${parserLoopState.loopId}"
          }

          val node = nodes.getOrNull(nodeIndex)

          if (node != null && tryMatchConditionMatchableWithNode(parserLoopState.predicateMatchables, node)) {
            // continue loop
            commandIndex = parserLoopState.loopStartCommandIndex
          } else {
            // end loop
            parserState.parserLoopStack.pop()
            ++commandIndex
          }
        }
        is KurobaBeginConditionCommand<T> -> {
          val start = nodeIndex
          var foundMatch = false

          parserState.successfullyExecutedConditions.remove(command.conditionId)

          for (index in start until nodes.size) {
            val node = nodes[index]

            if (tryMatchConditionMatchableWithNode(command.conditionMatchables, node)) {
              foundMatch = true
              break
            }

            ++nodeIndex
          }

          if (foundMatch) {
            ++commandIndex
            parserState.successfullyExecutedConditions.add(command.conditionId)

            if (command.resetNodeIndex) {
              nodeIndex = start
            }
          } else {
            nodeIndex = start

            commandIndex = findEndOfConditionBlockOrThrow(
              commandIndex,
              kurobaParserCommands,
              command.conditionId
            )
          }
        }
        is KurobaConditionElseBranchCommand<T> -> {
          val shouldRunElseBranch = parserState.successfullyExecutedConditions.contains(
            command.conditionId
          ).not()

          // Increase commandIndex to skip current KurobaConditionElseBranchCommand. We need to do
          // this is both cases when executing the else branch and when not.
          // 1. When executing it we simply need to move to the next command.
          // 2. When not executing, we need to increment it so that findEndOfConditionBlockOrThrow()
          // won't return us the very same command we are currently at.
          ++commandIndex

          if (!shouldRunElseBranch) {
            commandIndex = findEndOfConditionBlockOrThrow(
              commandIndex,
              kurobaParserCommands,
              command.conditionId
            )
          }
        }
        is KurobaEndConditionCommand<T> -> {
          ++commandIndex
        }
      }
    }
  }

  /**
   * Searches for either KurobaConditionElseBranchCommand or KurobaEndConditionCommand and continues
   * it's execution from there.
   * */
  private fun findEndOfConditionBlockOrThrow(
    commandIndex: Int,
    kurobaParserCommands: List<KurobaParserCommand<T>>,
    conditionId: Int
  ): Int {
    for (index in commandIndex until kurobaParserCommands.size) {
      val kurobaParserCommand = kurobaParserCommands[index]

      if (kurobaParserCommand is KurobaConditionElseBranchCommand
        && kurobaParserCommand.conditionId == conditionId
      ) {
        return index
      }

      if (kurobaParserCommand is KurobaEndConditionCommand
        && kurobaParserCommand.conditionId == conditionId
      ) {
        return index
      }
    }

    throw HtmlParsingException("Failed to find end of condition for conditionId=$conditionId")
  }

  private fun tryMatchConditionMatchableWithNode(
    conditionMatchables: List<Matchable>,
    node: Node
  ): Boolean {
    for (conditionMatchable in conditionMatchables) {
      when (conditionMatchable) {
        is PatternMatchable -> {
          if (!conditionMatchable.matcher.matches(node.attr(conditionMatchable.attrName))) {
            return false
          }
        }
        is TagMatchable -> {
          if (node !is Element) {
            return false
          }

          if (!conditionMatchable.matcher.matches(node)) {
            return false
          }
        }
      }
    }

    return true
  }

  data class ParserNestingState(
    var nodeIndex: Int,
    val nodes: List<Node>
  )

  data class ParserLoopState(
    val loopStartCommandIndex: Int,
    val loopId: Int,
    val predicateMatchables: List<Matchable>
  )

  class ParserState(
    val parserStateStack: Stack<ParserNestingState> = Stack<ParserNestingState>(),
    val parserLoopStack: Stack<ParserLoopState> = Stack<ParserLoopState>(),
    val successfullyExecutedConditions: MutableSet<Int> = mutableSetOf()
  ) {
    fun clear() {
      parserLoopStack.clear()
      parserStateStack.clear()
      successfullyExecutedConditions.clear()
    }
  }

  class HtmlParsingException(message: String) : Exception(message)

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
    const val TITLE_TAG = "title"
    const val NOSCRIPT_TAG = "noscript"
    const val SCRIPT_TAG = "script"
    const val ARTICLE_TAG = "article"
    const val HEADER_TAG = "header"
    const val HEADING_TAG = "h"
    const val META_TAG = "meta"
    const val SPAN_TAG = "span"

    const val CLASS_ATTR = "class"
    const val STYLE_ATTR = "style"
    const val ID_ATTR = "id"
  }
}