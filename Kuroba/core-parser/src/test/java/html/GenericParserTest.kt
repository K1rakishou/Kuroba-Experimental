package html

import android.util.Log
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandBufferBuilder
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import com.github.k1rakishou.core_parser.html.KurobaMatcher
import junit.framework.Assert
import org.jsoup.Jsoup
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(value = [Log::class])
class GenericParserTest : BaseHtmlParserTest() {
  private val sequentialBuffer = KurobaHtmlParserCommandBufferBuilder<ExecutionStepsCollector>()
    .start {
      html()

      nest {
        body()

        nest {
          div(
            id = KurobaMatcher.PatternMatcher.stringEquals("tag1"),
            extractorFunc = { node, _, testCollector -> testCollector.collectedTags += node.attr("id") }
          )
          div(
            id = KurobaMatcher.PatternMatcher.stringEquals("tag2"),
            extractorFunc = { node, _, testCollector -> testCollector.collectedTags += node.attr("id") }
          )
          div(
            id = KurobaMatcher.PatternMatcher.stringEquals("tag3"),
            extractorFunc = { node, _, testCollector -> testCollector.collectedTags += node.attr("id") }
          )
        }
      }
    }
    .build()

  private val nestedBuffer = KurobaHtmlParserCommandBufferBuilder<ExecutionStepsCollector>()
    .start {
      html()

      nest {
        body()

        nest {
          div(
            id = KurobaMatcher.PatternMatcher.stringEquals("tag1"),
            extractorFunc = { node, _, testCollector ->
              testCollector.collectedTags += node.attr("id")
            }
          )

          nest {
            div(
              id = KurobaMatcher.PatternMatcher.stringEquals("nested_tag1"),
              extractorFunc = { node, _, testCollector ->
                testCollector.collectedTags += node.attr("id")
              }
            )
          }

          div(
            id = KurobaMatcher.PatternMatcher.stringEquals("tag2"),
            extractorFunc = { node, _, testCollector ->
              testCollector.collectedTags += node.attr("id")
            }
          )

          nest {
            div(
              id = KurobaMatcher.PatternMatcher.stringEquals("nested_tag2"),
              extractorFunc = { node, _, testCollector ->
                testCollector.collectedTags += node.attr("id")
              }
            )
          }

          div(
            id = KurobaMatcher.PatternMatcher.stringEquals("tag3"),
            extractorFunc = { node, _, testCollector ->
              testCollector.collectedTags += node.attr("id")
            }
          )

          nest {
            div(
              id = KurobaMatcher.PatternMatcher.stringEquals("nested_tag3"),
              extractorFunc = { node, _, testCollector ->
                testCollector.collectedTags += node.attr("id")
              }
            )

            div(
              id = KurobaMatcher.PatternMatcher.stringEquals("nested_tag4"),
              extractorFunc = { node, _, testCollector ->
                testCollector.collectedTags += node.attr("id")
              }
            )

            nest {
              div(
                id = KurobaMatcher.PatternMatcher.stringEquals("nested_tag5"),
                extractorFunc = { node, _, testCollector ->
                  testCollector.collectedTags += node.attr("id")
                }
              )
            }
          }
        }
      }
    }
    .build()

  private val commandBuffer1 = KurobaHtmlParserCommandBufferBuilder<TestCollector1>()
    .start {
      html()

      nest {
        body()

        nest {
          div(id = KurobaMatcher.PatternMatcher.stringEquals("tag2"))

          nest {
            div(
              id = KurobaMatcher.PatternMatcher.stringEquals("nested_tag2"),
              extractorFunc = { node, _, testCollector ->
                testCollector.nestedValue2 = node.wholeText()
              }
            )
          }

          div(id = KurobaMatcher.PatternMatcher.stringEquals("tag3"))

          nest {
            div(
              id = KurobaMatcher.PatternMatcher.stringEquals("nested_tag3"),
              extractorFunc = { node, _, testCollector ->
                testCollector.nestedValue3 = node.wholeText()
              }
            )
          }
        }
      }
    }
    .build()

  private val commandBuffer2 = KurobaHtmlParserCommandBufferBuilder<TestCollector2>()
    .start {
      html()

      nest {
        body()

        nest {
          div(id = KurobaMatcher.PatternMatcher.stringEquals("app"))

          nest {
            div(className = KurobaMatcher.PatternMatcher.stringContains("header"))

            nest {
              div(className = KurobaMatcher.PatternMatcher.stringContains("header__inner"))

              nest {
                div(className = KurobaMatcher.PatternMatcher.stringContains("header__left"))

                nest {
                  div(className = KurobaMatcher.PatternMatcher.stringContains("header__logo"))

                  nest {
                    a(
                      attrExtractorBuilderFunc = {
                        expectAttr("href")
                        extractText()
                      },
                      extractorFunc = { _, extractedAttributeValues, collector ->
                        collector.headerLogoText = extractedAttributeValues.getText()
                      }
                    )
                  }
                }
              }
            }

            noscript(matchableBuilderFunc = { emptyTag() })

            nest {
              article(matchableBuilderFunc = { anyTag() })

              nest {
                header(matchableBuilderFunc = { emptyTag() })

                nest {
                  heading(
                    headingNum = 1,
                    attrExtractorBuilderFunc = {
                      expectAttrWithValue("itemprop", KurobaMatcher.PatternMatcher.stringEquals("name"))
                    }
                  )

                  nest {
                    breakpoint()

                    a(
                      attrExtractorBuilderFunc = {
                        expectAttrWithValue("itemprop", KurobaMatcher.PatternMatcher.stringEquals("url"))
                        extractText()
                      },
                      extractorFunc = { _, extractedAttrValues, collector ->
                        collector.titleTrackNamePart = extractedAttrValues.getText()
                      }
                    )

                    a(
                      attrExtractorBuilderFunc = {
                        expectAttr("href")
                        extractText()
                      },
                      extractorFunc = { _, extractedAttrValues, collector ->
                        collector.titleArtistPart = extractedAttrValues.getText()
                      }
                    )
                  }

                  meta(
                    attrExtractorBuilderFunc = {
                      expectAttrWithValue("itemprop", KurobaMatcher.PatternMatcher.stringEquals("duration"))
                      extractAttrValueByKey("content")
                    },
                    extractorFunc = { _, extractedAttrValues, collector ->
                      collector.duration = extractedAttrValues.getAttrValue("content")
                    }
                  )
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
    setupLogging("GenericParserTest")
  }

  @Test
  fun `test nested commands1`() {
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("parsing/nested_html_tags_parsing_test.html")
      .readBytes()
    val fileString = String(fileBytes)

    val testCollector = TestCollector1()
    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<TestCollector1>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      commandBuffer1,
      testCollector
    )

    Assert.assertEquals(null, testCollector.nestedValue1)
    Assert.assertEquals("Nested value 2", testCollector.nestedValue2)
    Assert.assertEquals("Nested value 3", testCollector.nestedValue3)
  }

  @Test
  fun `test sequential command execution`() {
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("parsing/nested_html_tags_parsing_test.html")
      .readBytes()
    val fileString = String(fileBytes)

    val testCollector = ExecutionStepsCollector()
    val parserCommandExecutor = KurobaHtmlParserCommandExecutor<ExecutionStepsCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      sequentialBuffer,
      testCollector
    )

    Assert.assertEquals("tag1", testCollector.collectedTags[0])
    Assert.assertEquals("tag2", testCollector.collectedTags[1])
    Assert.assertEquals("tag3", testCollector.collectedTags[2])
  }


  @Test
  fun `test nested command execution`() {
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("parsing/nested_html_tags_parsing_test.html")
      .readBytes()
    val fileString = String(fileBytes)

    val testCollector = ExecutionStepsCollector()
    val parserCommandExecutor = KurobaHtmlParserCommandExecutor<ExecutionStepsCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      nestedBuffer,
      testCollector
    )

    Assert.assertEquals("tag1", testCollector.collectedTags[0])
    Assert.assertEquals("nested_tag1", testCollector.collectedTags[1])
    Assert.assertEquals("tag2", testCollector.collectedTags[2])
    Assert.assertEquals("nested_tag2", testCollector.collectedTags[3])
    Assert.assertEquals("tag3", testCollector.collectedTags[4])
    Assert.assertEquals("nested_tag3", testCollector.collectedTags[5])
    Assert.assertEquals("nested_tag4", testCollector.collectedTags[6])
    Assert.assertEquals("nested_tag5", testCollector.collectedTags[7])
  }

  @Test
  fun `test nested commands2`() {
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("parsing/nested_html_tags_parsing_test2.html")
      .readBytes()
    val fileString = String(fileBytes)

    val testCollector = TestCollector2()
    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<TestCollector2>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      commandBuffer2,
      testCollector
    )

    Assert.assertEquals("SoundCloud", testCollector.headerLogoText)
    Assert.assertEquals("KOT. 78", testCollector.titleTrackNamePart)
    Assert.assertEquals("KOT.", testCollector.titleArtistPart)
    Assert.assertEquals("PT02H00M26S", testCollector.duration)
  }

  class TestCollector1(
    var nestedValue1: String? = null,
    var nestedValue2: String? = null,
    var nestedValue3: String? = null,
  ) : KurobaHtmlParserCollector

  class TestCollector2(
    var headerLogoText: String? = null,
    var titleArtistPart: String? = null,
    var titleTrackNamePart: String? = null,
    var duration: String? = null
  ) : KurobaHtmlParserCollector

  class ExecutionStepsCollector(
    val collectedTags: MutableList<String> = mutableListOf()
  ) : KurobaHtmlParserCollector

}