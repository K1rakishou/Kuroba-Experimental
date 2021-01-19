package html

import android.util.Log
import com.github.k1rakishou.core_parser.html.ExtractedAttributeValues
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandBufferBuilder
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import com.github.k1rakishou.core_parser.html.KurobaMatcher
import com.github.k1rakishou.core_parser.html.KurobaParserCommandBuilder
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.regex.Pattern

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
                    parseSinglePost()
                  }
                }
              }
            }
          }
        }
      }
    }.build()

  private fun KurobaParserCommandBuilder<TestCollector>.parseSinglePost(): KurobaParserCommandBuilder<TestCollector> {
    div(matchableBuilderFunc = {
      attr(
        "class",
        KurobaMatcher.PatternMatcher.stringContains("post stub")
      )
    })

    article(
      matchableBuilderFunc = {
        attr(
          "class",
          KurobaMatcher.PatternMatcher.stringContains("post doc_id")
        )
      },
      attrExtractorBuilderFunc = {
        extractAttrValueByKey("id")
        extractAttrValueByKey("data-board")
      },
      extractorFunc = { _, extractedAttributeValues, collector ->
        extractPostNoAndBoardCode(extractedAttributeValues, collector)
      }
    )

    nest {
      div(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("post_wrapper")) })

      nest {
        executeIf(predicate = { className(KurobaMatcher.PatternMatcher.stringEquals("post_file")) }) {
          div(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("post_file")) })

          nest {
            a(
              matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("post_file_filename")) },
              attrExtractorBuilderFunc = { extractAttrValueByKey("href") },
              extractorFunc = { _, extractedAttributeValues, testCollector ->
                testCollector.lastOrNull()?.let { postBuilder ->
                  postBuilder.postImageUrlRawList += requireNotNull(extractedAttributeValues.getAttrValue("href"))
                }
              }
            )
          }
        }

        header()

        nest {
          div(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("post_data")) })

          nest {
            heading(
              headingNum = 2,
              matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("post_title")) },
              attrExtractorBuilderFunc = { extractText() },
              extractorFunc = { _, extractedAttributeValues, testCollector ->
                testCollector.lastOrNull()?.let { postBuilder ->
                  postBuilder.subject = extractedAttributeValues.getText()
                }
              }
            )

            span(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("post_poster_data")) })

            nest {
              span(
                matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("post_author")) },
                attrExtractorBuilderFunc = { extractText() },
                extractorFunc = { _, extractedAttributeValues, testCollector ->
                  testCollector.lastOrNull()?.let { postBuilder ->
                    postBuilder.name = requireNotNull(extractedAttributeValues.getText())
                  }
                }
              )
            }

            span(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("time_wrap")) })

            nest {
              tag(
                tagName = "time",
                matchableBuilderFunc = { anyTag() },
                attrExtractorBuilderFunc = { extractAttrValueByKey("datetime") },
                extractorFunc = { _, extractedAttributeValues, testCollector ->
                  testCollector.lastOrNull()?.let { postBuilder ->
                    val dateTimeRaw = requireNotNull(extractedAttributeValues.getAttrValue("datetime"))
                    postBuilder.dateTime = DateTime.parse(dateTimeRaw)
                  }
                }
              )
            }

            a(
              matchableBuilderFunc = { attr("data-function", KurobaMatcher.PatternMatcher.stringEquals("quote")) },
              attrExtractorBuilderFunc = { extractAttrValueByKey("href") },
              extractorFunc = { _, extractedAttributeValues, testCollector ->
                tryExtractIsOpFlag(testCollector, extractedAttributeValues)
              }
            )
          }
        }

        div(
          matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("text")) },
          attrExtractorBuilderFunc = { extractHtml() },
          extractorFunc = { _, extractedAttributeValues, testCollector ->
            testCollector.lastOrNull()?.let { postBuilder ->
              postBuilder.commentRaw = extractedAttributeValues.getHtml()
            }
          }
        )
      }
    }

    return this
  }

  private fun extractPostNoAndBoardCode(
    extractedAttributeValues: ExtractedAttributeValues,
    collector: TestCollector
  ) {
    val postId = extractedAttributeValues.getAttrValue("id")?.toLongOrNull()
    val boardCode = extractedAttributeValues.getAttrValue("data-board")

    val simpleThreadDescriptor = SimpleThreadDescriptor(
      boardCode = boardCode,
      postNo = postId
    )

    collector.searchResults[simpleThreadDescriptor] = FoolFuukaSearchEntryPostBuilder()
    collector.searchResults[simpleThreadDescriptor]!!.postNo = postId
    collector.searchResults[simpleThreadDescriptor]!!.boardCode = boardCode
  }

  private fun tryExtractIsOpFlag(
    testCollector: TestCollector,
    extractedAttributeValues: ExtractedAttributeValues
  ) {
    testCollector.lastOrNull()?.let { postBuilder ->
      val postLink = requireNotNull(extractedAttributeValues.getAttrValue("href"))

      val matcher = POST_LINK_PATTERN.matcher(postLink)
      if (!matcher.find()) {
        throw KurobaHtmlParserCommandExecutor.HtmlParsingException(
          "Bad post link: \'$postLink\'"
        )
      }

      val threadNo = matcher.group(1)?.toLongOrNull()
      val postNo = matcher.group(2)?.toLongOrNull()

      if (threadNo == null || postNo == null) {
        throw KurobaHtmlParserCommandExecutor.HtmlParsingException(
          "Bad post link: \'$postLink\', threadNo: $threadNo, postNo: $postNo"
        )
      }

      postBuilder.isOp = threadNo == postNo
    }
  }

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

    kotlin.run {
      val firstPost = collector.searchResults.values.first()
      assertEquals(3825907L, firstPost.postNo)
      assertEquals("c", firstPost.boardCode)
      assertEquals("\n test ", firstPost.commentRaw)
      assertEquals("Anonymous", firstPost.name)
      assertEquals("2021-01-09T08:26:18.000Z", firstPost.dateTime!!.toString())

      assertTrue(firstPost.postImageUrlRawList.isEmpty())
    }

    kotlin.run {
      val lastPost = collector.searchResults.values.last()
      assertEquals(3766138L, lastPost.postNo)
      assertEquals("c", lastPost.boardCode)
      assertEquals("\n Test ", lastPost.commentRaw)
      assertEquals("Anonymous", lastPost.name)
      assertEquals("2020-09-27T16:37:16.000Z", lastPost.dateTime!!.toString())

      assertTrue(lastPost.postImageUrlRawList.isEmpty())
    }

    kotlin.run {
      val post = collector.searchResults.values
        .first { postBuilder -> postBuilder.postNo == 3778632L }

      assertEquals("c", post.boardCode)
      assertEquals("\n Ignore that, it was a test post. ", post.commentRaw)
      assertEquals("Anonymous", post.name)
      assertEquals("2020-10-19T20:30:15.000Z", post.dateTime!!.toString())

      assertEquals(1, post.postImageUrlRawList.size)
      assertEquals("https://archive.nyafuu.org/c/full_image/1603139415719.png", post.postImageUrlRawList.first())
    }
  }

  class FoolFuukaSearchEntryPostBuilder {
    var isOp: Boolean? = null
    var name: String? = null
    var subject: String? = null
    var postNo: Long? = null
    var boardCode: String? = null
    var dateTime: DateTime? = null
    val postImageUrlRawList = mutableListOf<String>()
    var commentRaw: String? = null
  }

  data class SimpleThreadDescriptor(
    val siteName: String = "archived.moe",
    var boardCode: String? = null,
    var postNo: Long? = null
  )

  data class TestCollector(
    val searchResults: LinkedHashMap<SimpleThreadDescriptor, FoolFuukaSearchEntryPostBuilder> = linkedMapOf()
  ) : KurobaHtmlParserCollector {
    fun lastOrNull(): FoolFuukaSearchEntryPostBuilder? = searchResults.values.lastOrNull()
  }

  companion object {
    private val POST_LINK_PATTERN = Pattern.compile("thread\\/(\\d+)\\/#q(\\d+)")
  }
}