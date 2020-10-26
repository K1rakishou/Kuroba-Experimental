package html

import android.util.Log
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

        println(fullString)
        return@then 0
      }

    Mockito.`when`(Log.e(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
      .then { answer ->
        val fullString = buildString {
          answer.arguments.forEach { argument ->
            append(argument as String)
          }
        }

        println(fullString)
        return@then 0
      }
  }

}