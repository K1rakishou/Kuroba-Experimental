package html

import android.util.Log
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandBufferBuilder
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import com.github.k1rakishou.core_parser.html.KurobaMatcher
import org.jsoup.Jsoup
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(value = [Log::class])
class KurobaStreamableHtmlParserTest : BaseHtmlParserTest() {

  private val kurobaHtmlParserCommandBuffer = KurobaHtmlParserCommandBufferBuilder<TestStreamableCollector>()
    .start {
      htmlElement { html() }

      nest {
        htmlElement { body() }

        nest {
          htmlElement {
            script(
              attr = {
                expectAttrWithValue("data-id", KurobaMatcher.stringEquals("player-instream"))
                extractAttrValueByKey("data-duration")
                extractAttrValueByKey("data-title")
              },
              extractor = { _, extractAttributeValues, collector ->
                collector.title = extractAttributeValues.getAttrValue("data-title")
                collector.duration = extractAttributeValues.getAttrValue("data-duration")
              }
            )
          }
        }
      }
    }
    .build()

  @Before
  fun setup() {
    setupLogging("KurobaStreamableHtmlParserTest")
  }

  @Test
  fun `parse test page 1 title and duration`() {
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("parsing/streamable_test_html_page1.html")
      .readBytes()
    val fileString = String(fileBytes)

    val testStreamableCollector = TestStreamableCollector()
    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<TestStreamableCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      kurobaHtmlParserCommandBuffer,
      testStreamableCollector
    )

    Assert.assertEquals("Sakurakaze - Miko ft. A-chan [VRMix.01]", testStreamableCollector.title)
    Assert.assertEquals("227.333333", testStreamableCollector.duration)
  }

  @Test
  fun `parse test page 2 title and duration`() {
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("parsing/streamable_test_html_page2.html")
      .readBytes()
    val fileString = String(fileBytes)

    val testStreamableCollector = TestStreamableCollector()
    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<TestStreamableCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      kurobaHtmlParserCommandBuffer,
      testStreamableCollector
    )

    Assert.assertEquals("Streamable Video", testStreamableCollector.title)
    Assert.assertEquals("5.133333", testStreamableCollector.duration)
  }

  @Test
  fun `parse test page 3 title and duration`() {
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("parsing/streamable_test_html_page3.html")
      .readBytes()
    val fileString = String(fileBytes)

    val testStreamableCollector = TestStreamableCollector()
    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<TestStreamableCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      kurobaHtmlParserCommandBuffer,
      testStreamableCollector
    )

    Assert.assertEquals("PekoMiko - Santa Claus Is Coming To Town", testStreamableCollector.title)
    Assert.assertEquals("81.3", testStreamableCollector.duration)
  }

  class TestStreamableCollector(
    var title: String? = null,
    var duration: String? = null
  ) : KurobaHtmlParserCollector

}