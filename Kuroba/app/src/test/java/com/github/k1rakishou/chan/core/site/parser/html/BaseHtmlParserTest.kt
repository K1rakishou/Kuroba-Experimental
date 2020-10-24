package com.github.k1rakishou.chan.core.site.parser.html

import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.Logger
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.powermock.api.mockito.PowerMockito


abstract class BaseHtmlParserTest {

  fun setupLogging(tag: String) {
    PowerMockito.mockStatic(AndroidUtils::class.java)
    Mockito.`when`(AndroidUtils.getApplicationLabel())
      .thenReturn(tag)

    PowerMockito.mockStatic(Logger::class.java)

    Mockito.`when`(Logger.d(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
      .then { answer ->
        val fullString = buildString {
          answer.arguments.forEach { argument ->
            append(argument as String)
          }
        }

        println(fullString)
      }
  }

}