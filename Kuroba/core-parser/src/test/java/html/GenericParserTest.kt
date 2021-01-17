package html

import android.util.Log
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandBufferBuilder
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import com.github.k1rakishou.core_parser.html.KurobaMatcher
import junit.framework.Assert
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
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
      htmlElement { html() }

      nest {
        htmlElement { body() }

        nest {
          htmlElement {
            div(
              id = KurobaMatcher.stringEquals("tag1"),
              extractor = { node, testCollector -> testCollector.collectedTags += node.attr("id") }
            )
          }
          htmlElement {
            div(
              id = KurobaMatcher.stringEquals("tag2"),
              extractor = { node, testCollector -> testCollector.collectedTags += node.attr("id") }
            )
          }
          htmlElement {
            div(
              id = KurobaMatcher.stringEquals("tag3"),
              extractor = { node, testCollector -> testCollector.collectedTags += node.attr("id") }
            )
          }
        }
      }
    }
    .build()

  private val nestedBuffer = KurobaHtmlParserCommandBufferBuilder<ExecutionStepsCollector>()
    .start {
      htmlElement { html() }

      nest {
        htmlElement { body() }

        nest {
          htmlElement {
            div(
              id = KurobaMatcher.stringEquals("tag1"),
              extractor = { node, testCollector -> testCollector.collectedTags += node.attr("id") }
            )
          }

          nest {
            htmlElement {
              div(
                id = KurobaMatcher.stringEquals("nested_tag1"),
                extractor = { node, testCollector -> testCollector.collectedTags += node.attr("id") }
              )
            }
          }
        }

        nest {
          htmlElement {
            div(
              id = KurobaMatcher.stringEquals("tag2"),
              extractor = { node, testCollector -> testCollector.collectedTags += node.attr("id") }
            )
          }

          nest {
            htmlElement {
              div(
                id = KurobaMatcher.stringEquals("nested_tag2"),
                extractor = { node, testCollector -> testCollector.collectedTags += node.attr("id") }
              )
            }
          }
        }

        nest {
          htmlElement {
            div(
              id = KurobaMatcher.stringEquals("tag3"),
              extractor = { node, testCollector -> testCollector.collectedTags += node.attr("id") }
            )
          }

          nest {
            htmlElement {
              div(
                id = KurobaMatcher.stringEquals("nested_tag3"),
                extractor = { node, testCollector -> testCollector.collectedTags += node.attr("id") }
              )
            }

            htmlElement {
              div(
                id = KurobaMatcher.stringEquals("nested_tag4"),
                extractor = { node, testCollector -> testCollector.collectedTags += node.attr("id") }
              )
            }

            nest {
              htmlElement {
                div(
                  id = KurobaMatcher.stringEquals("nested_tag5"),
                  extractor = { node, testCollector -> testCollector.collectedTags += node.attr("id") }
                )
              }
            }
          }
        }
      }
    }
    .build()

  private val commandBuffer1 = KurobaHtmlParserCommandBufferBuilder<TestCollector1>()
    .start {
      htmlElement { html() }

      nest {
        htmlElement { body() }

        nest {
          htmlElement { div(id = KurobaMatcher.stringEquals("tag2")) }

          nest {
            htmlElement {
              div(
                id = KurobaMatcher.stringEquals("nested_tag2"),
                extractor = { node, testCollector -> testCollector.nestedValue2 = node.wholeText() }
              )
            }
          }
        }

        nest {
          htmlElement { div(id = KurobaMatcher.stringEquals("tag3")) }

          nest {
            htmlElement {
              div(
                id = KurobaMatcher.stringEquals("nested_tag3"),
                extractor = { node, testCollector -> testCollector.nestedValue3 = node.wholeText() }
              )
            }
          }
        }
      }
    }
    .build()

  private val commandBuffer2 = KurobaHtmlParserCommandBufferBuilder<TestCollector2>()
    .start {
      htmlElement { html() }

      nest {
        htmlElement { body() }

        nest {
          htmlElement { div(id = KurobaMatcher.stringEquals("app")) }

          nest {
            htmlElement { div(className = KurobaMatcher.stringContains("header")) }

            nest {
              htmlElement { div(className = KurobaMatcher.stringContains("header__inner")) }

              nest {
                htmlElement { div(className = KurobaMatcher.stringContains("header__left")) }

                nest {
                  htmlElement { div(className = KurobaMatcher.stringContains("header__logo")) }

                  nest {
                    htmlElement {
                      a(
                        attr = {
                          expectAttr("href")
                          extractText()
                        },
                        extractor = { _, extractedAttributeValues, collector ->
                          collector.headerLogoText = extractedAttributeValues.getText()
                        }
                      )
                    }
                  }
                }
              }
            }

            htmlElement { noscript() }

            nest {
              htmlElement { article() }

              nest {
                htmlElement { header() }

                nest {
                  htmlElement {
                    heading(
                      headingNum = 1,
                      attr = { expectAttrWithValue("itemprop", KurobaMatcher.stringEquals("name")) }
                    )
                  }

                  nest {
                    htmlElement {
                      a(
                        attr = { expectAttrWithValue("itemprop", KurobaMatcher.stringEquals("url")) },
                        extractor = { node, _, collector ->
                          collector.titleTrackNamePart = (node as Element).text()
                        }
                      )
                    }

                    htmlElement {
                      a(
                        attr = {
                          expectAttr("href")
                          extractText()
                        },
                        extractor = { _, extractedAttrValues, collector ->
                          collector.titleArtistPart = extractedAttrValues.getText()
                        }
                      )
                    }
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