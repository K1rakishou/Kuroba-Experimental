package html

import android.util.Log
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.powermock.api.mockito.PowerMockito


abstract class BaseHtmlParserTest {

  fun setupLogging(tag: String) {
    PowerMockito.mockStatic(Log::class.java)

    Mockito.`when`(Log.d(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
      .then { answer ->
        val fullString = buildString {
          answer.arguments.forEach { argument ->
            append(argument as String)
          }
        }

        println("$tag $fullString")
        return@then 0
      }

    Mockito.`when`(Log.e(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
      .then { answer ->
        val fullString = buildString {
          answer.arguments.forEach { argument ->
            append(argument as String)
          }
        }

        println("$tag $fullString")
        return@then 0
      }
  }

  fun Node.wholeText(): String {
    if (this is TextNode) {
      return text()
    }

    if (this is Element) {
      return text()
    }

    throw IllegalArgumentException("Unknown node: ${this.javaClass.simpleName}")
  }

}