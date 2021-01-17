package com.github.k1rakishou.core_parser.html

import android.annotation.SuppressLint
import android.util.Log
import com.github.k1rakishou.core_parser.html.commands.KurobaBreakpointCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaCommandPopState
import com.github.k1rakishou.core_parser.html.commands.KurobaCommandPushState
import com.github.k1rakishou.core_parser.html.commands.KurobaParserCommand
import com.github.k1rakishou.core_parser.html.commands.KurobaParserStepCommand
import org.jsoup.nodes.Document
import org.jsoup.nodes.Node
import java.util.*

/**
 * This class is designed to be single threaded!
 * If you want concurrency - create separate instances per each thread!
 * */
class KurobaHtmlParserCommandExecutor<T : KurobaHtmlParserCollector>(
  private val debugMode: Boolean = false
) {
  private val parserStateStack = Stack<ParserState>()

  @SuppressLint("LongLogTag")
  fun executeCommands(
    document: Document,
    kurobaParserCommands: List<KurobaParserCommand<T>>,
    collector: T
  ): Boolean {
    parserStateStack.clear()

    if (kurobaParserCommands.isEmpty()) {
      return true
    }

    var commandIndex = 0
    var nodeIndex = 0
    var nodes = document.childNodes()

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
        is KurobaParserStepCommand<T> -> {
          val start = nodeIndex

          for (index in start until nodes.size) {
            ++nodeIndex
            val node = nodes[index]

            if (command.executeStep(node, collector)) {
              break
            }
          }

          ++commandIndex
        }
        is KurobaCommandPushState<T> -> {
          parserStateStack.push(ParserState(nodeIndex, nodes))

          ++commandIndex
          nodes = nodes[nodeIndex - 1].childNodes()
          nodeIndex = 0
        }
        is KurobaCommandPopState<T> -> {
          val prevParserState = parserStateStack.pop()

          nodeIndex = prevParserState.nodeIndex
          nodes = prevParserState.nodes

          ++commandIndex
        }
      }
    }

    return true
  }

  data class ParserState(
    var nodeIndex: Int,
    val nodes: List<Node>
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