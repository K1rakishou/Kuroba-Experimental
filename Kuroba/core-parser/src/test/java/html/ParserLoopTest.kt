package html

import android.util.Log
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandBufferBuilder
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import com.github.k1rakishou.core_parser.html.KurobaMatcher
import junit.framework.Assert.assertEquals
import org.jsoup.Jsoup
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.regex.Pattern

@RunWith(PowerMockRunner::class)
@PrepareForTest(value = [Log::class])
class ParserLoopTest : BaseHtmlParserTest() {
  private val commandBuffer = KurobaHtmlParserCommandBufferBuilder<TestCollector>()
    .start {
      htmlElement { html() }

      nest {
        htmlElement { body() }

        nest {
          htmlElement { div(id = KurobaMatcher.stringEquals("main")) }

          nest {
            htmlElement { div(id = KurobaMatcher.stringEquals("inner")) }

            nest {
              loop {
                htmlElement { div(id = KurobaMatcher.patternFind(Pattern.compile("empty\\d+"))) }
                htmlElement { div(id = KurobaMatcher.patternFind(Pattern.compile("d\\d+"))) }

                nest {
                  htmlElement {
                    span(
                      attr = { expectAttrWithValue("class", KurobaMatcher.patternFind(Pattern.compile("test\\d+"))) },
                      extractor = { node, _, collector -> collector.collectedSpanTags.add(node.wholeText()) }
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
    .build()

  @Before
  fun setup() {
    setupLogging("ParserLoopTest")
  }

  @Test
  fun `test loops`() {
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("parsing/loop_test.html")
      .readBytes()
    val fileString = String(fileBytes)

    val testCollector = TestCollector()
    val parserCommandExecutor = KurobaHtmlParserCommandExecutor<TestCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      commandBuffer,
      testCollector
    )

    assertEquals(5, testCollector.collectedSpanTags.size)
    assertEquals("Test1", testCollector.collectedSpanTags[0])
    assertEquals("Test2", testCollector.collectedSpanTags[1])
    assertEquals("Test3", testCollector.collectedSpanTags[2])
    assertEquals("Test4", testCollector.collectedSpanTags[3])
    assertEquals("Test5", testCollector.collectedSpanTags[4])
  }

  class TestCollector(
    var collectedSpanTags: MutableList<String> = mutableListOf(),
  ) : KurobaHtmlParserCollector

}