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

              parsePages()
            }
          }
        }
      }
    }.build()

  private fun KurobaParserCommandBuilder<TestCollector>.parseSinglePost(): KurobaParserCommandBuilder<TestCollector> {
    article(
      matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringContains("post doc_id")) },
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
        // thread_image_box may not exist if a post has no images
        executeIf(predicate = { className(KurobaMatcher.PatternMatcher.stringEquals("thread_image_box")) }) {
          div(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("thread_image_box")) })

          nest {
            a(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("thread_image_link")) })

            nest {
              // Different archives use different tags for thumbnail url
              val imgTagAttrKeys = listOf("src", "data-src")

              tag(
                tagName = "img",
                matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagHasAnyOfAttributes(imgTagAttrKeys)) },
                attrExtractorBuilderFunc = { extractAttrValueByAnyKey(imgTagAttrKeys) },
                extractorFunc = { _, extractedAttributeValues, testCollector ->
                  testCollector.lastOrNull()?.let { postBuilder ->
                    postBuilder.postImageUrlRawList += requireNotNull(extractedAttributeValues.getAnyAttrValue(imgTagAttrKeys))
                  }
                }
              )
            }
          }
        }

        header()

        nest {
          div(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("post_data")) })

          nest {
            // post_title exists on archived.moe when the h2 tag is empty but does not exist on fireden.net
            executeIf(predicate = { className(KurobaMatcher.PatternMatcher.stringEquals("post_title")) }) {
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
            }

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

  private fun KurobaParserCommandBuilder<TestCollector>.parsePages(): KurobaParserCommandBuilder<TestCollector> {
    div(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("paginate")) })

    return nest {
      tag(
        tagName = "ul",
        matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher()) }
      )

      nest {
        loop {
          tag(
            tagName = "li",
            matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagAnyAttributeMatcher()) }
          )

          nest {
            executeIf(predicate = { attr("href", KurobaMatcher.PatternMatcher.patternFind(PAGE_URL_PATTERN)) }) {

              a(
                matchableBuilderFunc = { attr("href", KurobaMatcher.PatternMatcher.patternFind(PAGE_URL_PATTERN)) },
                attrExtractorBuilderFunc = {
                  extractText()
                  extractAttrValueByKey("href")
                },
                extractorFunc = { _, extractedAttributeValues, testCollector ->
                  extractPages(extractedAttributeValues, testCollector)
                }
              )
            }
          }
        }
      }
    }
  }

  private fun extractPages(
    extractedAttributeValues: ExtractedAttributeValues,
    testCollector: TestCollector
  ) {
    val pageUrl = extractedAttributeValues.getAttrValue("href")
    val tagText = extractedAttributeValues.getText()

    if (pageUrl != null && tagText != null) {
      val pageUrlMatcher = PAGE_URL_PATTERN.matcher(pageUrl)
      if (pageUrlMatcher.find()) {
        val pageNumberMatcher = NUMBER_PATTERN.matcher(tagText)
        if (pageNumberMatcher.matches()) {
          val possiblePage = pageUrlMatcher.group(1)?.toIntOrNull()
          if (possiblePage != null) {
            testCollector.pages += possiblePage
          }
        }
      }
    }
  }

  private fun extractPostNoAndBoardCode(
    extractedAttributeValues: ExtractedAttributeValues,
    collector: TestCollector
  ) {
    val postId = extractedAttributeValues.getAttrValue("id")?.toLongOrNull()
    val boardCode = extractedAttributeValues.getAttrValue("data-board")
      ?: collector.defaultBoardCode

    val simpleThreadDescriptor = SimpleThreadDescriptor(
      boardCode = boardCode,
      postNo = postId
    )

    check(!collector.searchResults.contains(simpleThreadDescriptor)) {
      "Search results already contain ${simpleThreadDescriptor}"
    }

    collector.searchResults[simpleThreadDescriptor] = FoolFuukaSearchEntryPostBuilder()
    collector.searchResults[simpleThreadDescriptor]!!.postNo = requireNotNull(postId)
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
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("parsing/foolfuuka_search/archived_moe_search.html")
      .readBytes()
    val fileString = String(fileBytes)

    val collector = TestCollector(defaultBoardCode = "c")
    val parserCommandExecutor = KurobaHtmlParserCommandExecutor<TestCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      commandBuffer,
      collector
    )

    assertEquals(25, collector.searchResults.size)
    assertEquals(15, collector.pages.size)

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
      assertEquals("https://archived.moe/files/c/thumb/1595/81/1595817713296s.jpg", post.postImageUrlRawList.first())
    }
  }

  @Test
  fun `test parse fireden search results page`() {
    val fileBytes =
      javaClass.classLoader!!.getResourceAsStream("parsing/foolfuuka_search/fireden_search.html")
        .readBytes()
    val fileString = String(fileBytes)

    val collector = TestCollector(defaultBoardCode = "sci")
    val parserCommandExecutor = KurobaHtmlParserCommandExecutor<TestCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      commandBuffer,
      collector
    )

    assertEquals(25, collector.searchResults.size)
    assertEquals(15, collector.pages.size)

    kotlin.run {
      val expectedComment = """
        <span class="greentext"><a href="https://boards.fireden.net/sci/thread/12602942/#12604463" class="backlink" data-function="highlight" data-backlink="true" data-board="sci" data-post="12604463">&gt;&gt;12604463</a></span>
        <br>
        <br>
        P4 final
        <br>
        <br>
        Also, the Flynn effect follows the same patter, there is a slight negative correlation between test performance overtime, and the g loading of the subtests (more g loaded subtest actually indicate we are becoming less intelligent for genetic reasons, that is seen in decreased frequencies of alleles associated with intelligence, and less intelligent people having more kids.
        <br>
        <br>
        If you are wondering why IQ do not automatically siphon out the less g loaded subtests that are pretty much determined from environmental is, actually confounding them makes the tests more useful. The WAIS-IV can help diagnosing Autism, ADHD, depression and even brain dammage as a score profile with underperformance within the Memory and processing speed Indexes indicates (less g loaded) imply that the person may have a condition inhibiting performance, whist general intelligence is still unaffected. 
        <br>
        <br>
        Also, Physical brain damage that lowers IQ test scores, is not deleterious to general intelligence (probably, this is confounded with the fact that stupid people, in regard to general intelligence get into more accidents) If brain damage does not affect g, I don’t think environmental differences would either. (Study titled: Preservation of General Intelligence following Traumatic Brain Injury: Contributions of the Met66 Brain-Derived Neurotrophic Factor) 
      """.trimIndent()

      val firstPost = collector.searchResults.values.first()
      assertEquals(12604467L, firstPost.postNo)
      assertEquals("sci", firstPost.boardCode)
      assertEquals(expectedComment, firstPost.commentRaw)
      assertEquals("Anonymous", firstPost.name)
      assertEquals("2021-01-20T10:12:09.000Z", firstPost.dateTime!!.toString())

      assertEquals(1, firstPost.postImageUrlRawList.size)
      assertEquals("https://img.fireden.net/sci/thumb/1609/80/1609801902509s.jpg", firstPost.postImageUrlRawList.first())
    }

    kotlin.run {
      val expectedComment = "\n they just test them a shit ton, with robust enough testing you can get good enough results to ensure reliability. you also setup the device to monitor itself "

      val lastPost = collector.searchResults.values.last()
      assertEquals(12603137L, lastPost.postNo)
      assertEquals("sci", lastPost.boardCode)
      assertEquals(expectedComment, lastPost.commentRaw)
      assertEquals("Anonymous", lastPost.name)
      assertEquals("2021-01-20T02:10:16.000Z", lastPost.dateTime!!.toString())

      assertTrue(lastPost.postImageUrlRawList.isEmpty())
    }
  }

  @Test
  fun `test parse arch b4k co search results page`() {
    val fileBytes =
      javaClass.classLoader!!.getResourceAsStream("parsing/foolfuuka_search/arch_b4k_co_search.html")
        .readBytes()
    val fileString = String(fileBytes)

    val collector = TestCollector(defaultBoardCode = "v")
    val parserCommandExecutor = KurobaHtmlParserCommandExecutor<TestCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      commandBuffer,
      collector
    )

    assertEquals(25, collector.searchResults.size)
    assertEquals(15, collector.pages.size)

    kotlin.run {
      val expectedComment = """
        <span class="greentext"><a href="https://arch.b4k.co/v/post/541180168/" class="backlink" data-function="highlight" data-backlink="true" data-board="v" data-post="541180168">&gt;&gt;541180168</a></span>
        <br>
        She hadn't actually aborted anything from what I remember, the unedited version starts with her getting a pregnancy test and she tells him that she wants an abortion and it escalates into the slap. 
      """.trimIndent()

      val firstPost = collector.searchResults.values.first()
      assertEquals(541180556L, firstPost.postNo)
      assertEquals("v", firstPost.boardCode)
      assertEquals(expectedComment, firstPost.commentRaw)
      assertEquals("Anonymous", firstPost.name)
      assertEquals("2021-01-20T15:10:17.000Z", firstPost.dateTime!!.toString())

      assertTrue(firstPost.postImageUrlRawList.isEmpty())
    }

    kotlin.run {
      val expectedComment = """
        <span class="greentext"><a href="https://arch.b4k.co/v/post/541166351/" class="backlink" data-function="highlight" data-backlink="true" data-board="v" data-post="541166351">&gt;&gt;541166351</a></span>
        <br>
        Test 
      """.trimIndent()

      val lastPost = collector.searchResults.values.last()
      assertEquals(541166676L, lastPost.postNo)
      assertEquals("v", lastPost.boardCode)
      assertEquals(expectedComment, lastPost.commentRaw)
      assertEquals("Anonymous", lastPost.name)
      assertEquals("2021-01-20T12:10:04.000Z", lastPost.dateTime!!.toString())

      assertTrue(lastPost.postImageUrlRawList.isEmpty())
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
    val defaultBoardCode: String,
    val searchResults: LinkedHashMap<SimpleThreadDescriptor, FoolFuukaSearchEntryPostBuilder> = linkedMapOf(),
    val pages: MutableList<Int> = mutableListOf()
  ) : KurobaHtmlParserCollector {
    fun lastOrNull(): FoolFuukaSearchEntryPostBuilder? = searchResults.values.lastOrNull()
  }

  companion object {
    private val POST_LINK_PATTERN = Pattern.compile("thread\\/(\\d+)\\/#q(\\d+)")
    private val PAGE_URL_PATTERN = Pattern.compile("/page/(\\d+)/$")
    private val NUMBER_PATTERN = Pattern.compile("\\d+")
  }
}