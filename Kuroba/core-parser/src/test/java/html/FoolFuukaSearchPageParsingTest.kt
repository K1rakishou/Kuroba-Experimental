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

@RunWith(PowerMockRunner::class)
@PrepareForTest(value = [Log::class])
class FoolFuukaSearchPageParsingTest : BaseHtmlParserTest() {
  private val commandBuffer = KurobaHtmlParserCommandBufferBuilder<TestCollector>()
    .start {
      html()

      nest {
        body()

        nest {
          div(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("container-fluid")) })

          nest {
            div(matchableBuilderFunc = { attr("role", KurobaMatcher.PatternMatcher.stringEquals("main")) })

            nest {
              article(matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("clearfix thread")) })

              nest {
                tag(
                  tagName = "aside",
                  matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("posts")) }
                )

                nest {
                  loop {
                    div(matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringContains("post stub")) })

                    article(
                      matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringContains("post doc_id")) },
                      attrExtractorBuilderFunc = {
                        extractAttrValueByKey("id")
                        extractAttrValueByKey("data-board")
                      },
                      extractorFunc = { node, extractedAttributeValues, collector ->
                        val postId = extractedAttributeValues.getAttrValue("id")
                        val boardCode = extractedAttributeValues.getAttrValue("data-board")

                        collector.searchResults += SearchResultEntry(postId?.toLongOrNull(), boardCode)
                      }
                    )
                  }
                }
              }
            }
          }
        }
      }
    }.build()

  @Before
  fun setup() {
    setupLogging("FoolFuukaSearchPageParsingTest")
  }

  @Test
  fun `test parse archived moe search results page`() {
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("parsing/archived_moe_search_results_page.html")
      .readBytes()
    val fileString = String(fileBytes)

    val collector = TestCollector()
    val parserCommandExecutor = KurobaHtmlParserCommandExecutor<TestCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      commandBuffer,
      collector
    )

    assertEquals(25, collector.searchResults.size)
    assertEquals(3825907L, collector.searchResults.first().postNo)
    assertEquals(3766138L, collector.searchResults.last().postNo)
  }

  data class TestCollector(
    val searchResults: MutableList<SearchResultEntry> = mutableListOf()
  ) : KurobaHtmlParserCollector

  class SearchResultEntry(
    var postNo: Long? = null,
    var boardCode: String? = null
  )
}