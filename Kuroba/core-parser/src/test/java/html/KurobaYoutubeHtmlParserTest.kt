package html

import android.util.Log
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandBufferBuilder
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import com.github.k1rakishou.core_parser.html.KurobaMatcher
import junit.framework.Assert.assertEquals
import org.joda.time.Period
import org.jsoup.Jsoup
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(value = [Log::class])
class KurobaYoutubeHtmlParserTest : BaseHtmlParserTest() {

  private val kurobaHtmlParserCommandBuffer =
    KurobaHtmlParserCommandBufferBuilder<TestYoutubeCollector>()
      .start {
        htmlElement { html() }

        nest {
          htmlElement { body() }

          nest {
            htmlElement { div(className = KurobaMatcher.stringEquals("watch-main-col")) }

            nest {
              htmlElement {
                meta(
                  attr = {
                    expectAttrWithValue("itemprop", KurobaMatcher.stringEquals("name"))
                    extractAttrValueByKey("content")
                  },
                  extractor = { _, extractedAttrValues, collector ->
                    collector.title = extractedAttrValues.getAttrValue("content")
                  }
                )
              }

              htmlElement {
                meta(
                  attr = {
                    expectAttrWithValue("itemprop", KurobaMatcher.stringEquals("duration"))
                    extractAttrValueByKey("content")
                  },
                  extractor = { _, extractedAttrValues, collector ->
                    collector.duration = extractedAttrValues.getAttrValue("content")
                  }
                )
              }
            }
          }
        }
      }
      .build()

  @Before
  fun setup() {
    setupLogging("KurobaYoutubeHtmlParserTest")
  }

  @Test
  fun `parse test page1 media name and media duration`() {
    val fileBytes =
      javaClass.classLoader!!.getResourceAsStream("parsing/youtube_test_html_page1.html")
        .readBytes()
    val fileString = String(fileBytes)

    val testYoutubeCollector = TestYoutubeCollector()
    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<TestYoutubeCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      kurobaHtmlParserCommandBuffer,
      testYoutubeCollector
    )

    assertEquals("TVアニメ マブラヴ オルタネイティヴ 放送決定記念 特報PV", testYoutubeCollector.title)
    assertEquals("PT2M1S", testYoutubeCollector.duration)
  }

  @Test
  fun `parse test page2 (live) media name and media duration`() {
    val fileBytes =
      javaClass.classLoader!!.getResourceAsStream("parsing/youtube_test_html_page2_live.html")
        .readBytes()
    val fileString = String(fileBytes)

    val testYoutubeCollector = TestYoutubeCollector()
    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<TestYoutubeCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      kurobaHtmlParserCommandBuffer,
      testYoutubeCollector
    )

    assertEquals("【スマホ音ゲー】音ゲー初心者が音ゲーに挑戦する【ゆっくりライブ】チャットしながら生放送", testYoutubeCollector.title)
    assertEquals("PT0M0S", testYoutubeCollector.duration)

    assertEquals(Period.ZERO, Period.parse(testYoutubeCollector.duration!!))
  }

  private data class TestYoutubeCollector(
    var title: String? = null,
    var duration: String? = null
  ) : KurobaHtmlParserCollector

}