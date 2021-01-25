package html

import android.util.Log
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandBufferBuilder
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import com.github.k1rakishou.core_parser.html.KurobaMatcher
import com.github.k1rakishou.core_parser.html.KurobaParserCommandBuilder
import junit.framework.Assert.assertEquals
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(value = [Log::class])
class FuukaSearchPageParsingTest : BaseHtmlParserTest() {
  private val commandBuffer = KurobaHtmlParserCommandBufferBuilder<TestCollector>()
    .start {
      html()

      nest {
        body()

        nest {
          tag(
            tagName = "table",
            matchableBuilderFunc = { attr("itemtype", KurobaMatcher.PatternMatcher.stringContains("http://schema.org/Comment")) },
          )

          nest {
            tag(
              tagName = "tbody",
              matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher()) }
            )

            nest {
              tag(
                tagName = "tr",
                matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher()) }
              )

              nest {
                tag(
                  tagName = "td",
                  matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("reply")) }
                )

                nest {
                  tag(
                    tagName = "label",
                    matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher()) }
                  )

                  a(
                    matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagHasAttribute("onclick")) },
                    attrExtractorBuilderFunc = { extractAttrValueByKey("href") },
                  )

                  tryExtractRegularPostMediaLink()

                  tag(
                    tagName = "blockquote",
                    matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher()) }
                  )

                  nest {
                    tag(
                      tagName = "p",
                      matchableBuilderFunc = { attr("itemprop", KurobaMatcher.PatternMatcher.stringEquals("text")) },
                      attrExtractorBuilderFunc = { extractHtml() },
                      extractorFunc = { node, extractedAttributeValues, testCollector ->
                        testCollector.comment = extractedAttributeValues.getHtml()
                      }
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

  private fun KurobaParserCommandBuilder<TestCollector>.tryExtractRegularPostMediaLink() {
    val predicate = KurobaMatcher.TagMatcher.tagPredicateMatcher { element ->
      if (!element.hasAttr("href")) {
        return@tagPredicateMatcher false
      }

      return@tagPredicateMatcher element.childNodes()
        .any { node -> node is Element && node.attr("class") == "thumb" }
    }

    executeIf(predicate = {
      tag(KurobaMatcher.TagMatcher.tagWithNameAttributeMatcher("a"))
      tag(predicate)
    }) {
      a(matchableBuilderFunc = {
        tag(KurobaMatcher.TagMatcher.tagWithNameAttributeMatcher("a"))
        tag(predicate)
      })

      nest {
        tag(
          tagName = "img",
          matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("thumb")) },
          attrExtractorBuilderFunc = { extractAttrValueByKey("src") },
          extractorFunc = { _, extractedAttributeValues, fuukaSearchPageCollector ->
            fuukaSearchPageCollector.url = extractedAttributeValues.getAttrValue("src")
          }
        )
      }
    }
  }

  @Before
  fun setup() {
    setupLogging("FuukaSearchPageParsingTest")
  }

  @Test
  fun `test executeIf push state bug`() {
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("parsing/fuuka_search/fuuka_search_execute_if_push_state_bug.html")
      .readBytes()
    val fileString = String(fileBytes)

    val collector = TestCollector()
    val parserCommandExecutor = KurobaHtmlParserCommandExecutor<TestCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      commandBuffer,
      collector
    )

    assertEquals("//i.warosu.org/data/g/thumb/0798/77/1611590883616s.jpg", collector.url)

    val expectedComment = """<a href="/g/post/S79877432">&gt;&gt;79877432</a>
<br>
You are too retarded to remain alive """

    assertEquals(expectedComment, collector.comment)
  }

  data class TestCollector(
    var url: String? = null,
    var comment: String? = null
  ) : KurobaHtmlParserCollector

}