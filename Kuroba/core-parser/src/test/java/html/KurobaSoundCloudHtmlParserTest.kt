package html

import android.util.Log
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandBufferBuilder
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import com.github.k1rakishou.core_parser.html.KurobaMatcher
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
class KurobaSoundCloudHtmlParserTest : BaseHtmlParserTest() {

  private val kurobaNormalLinkHtmlParserCommandBuffer = KurobaHtmlParserCommandBufferBuilder<TestNormalSoundCloudCollector>()
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
                  htmlElement {
                    div(
                      className = KurobaMatcher.stringContains("header__logo"),
                      extractor = { node, collector ->
                        collector.headerLogoText = (node as Element).text()
                      }
                    )
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

  private val kurobaAlbumLinkHtmlParserCommandBuffer = KurobaHtmlParserCommandBufferBuilder<TestAlbumSoundCloudCollector>()
    .start {
      htmlElement { html() }

      nest {
        htmlElement { head() }

        nest {
          htmlElement {
            title(
              attr = { extractText() },
              extractor = { _, extractedAttributeValues, collector ->
                collector.titleFull = extractedAttributeValues.getText()
              }
            )
          }
        }
      }
    }
    .build()

  @Before
  fun setup() {
    setupLogging("KurobaSoundCloudHtmlParserTest")
  }

  @Test
  fun `parse test page1 title, media name and media duration`() {
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("parsing/soundcloud_test_html_page1.html")
        .readBytes()
    val fileString = String(fileBytes)

    val testKurobaParserCollector = TestNormalSoundCloudCollector()
    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<TestNormalSoundCloudCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      kurobaNormalLinkHtmlParserCommandBuffer,
      testKurobaParserCollector
    )

    assertEquals("SoundCloud", testKurobaParserCollector.headerLogoText)
    assertEquals("KOT. 78", testKurobaParserCollector.titleTrackNamePart)
    assertEquals("KOT.", testKurobaParserCollector.titleArtistPart)
    assertEquals("PT02H00M26S", testKurobaParserCollector.duration)
  }

  @Test
  fun `parse test page2 title, media name and media duration`() {
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("parsing/soundcloud_test_html_page2.html")
      .readBytes()
    val fileString = String(fileBytes)

    val testKurobaParserCollector = TestNormalSoundCloudCollector()
    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<TestNormalSoundCloudCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      kurobaNormalLinkHtmlParserCommandBuffer,
      testKurobaParserCollector
    )

    assertEquals("SoundCloud", testKurobaParserCollector.headerLogoText)
    assertEquals("Enter The ANISON MATRIX!!", testKurobaParserCollector.titleTrackNamePart)
    assertEquals("Go-qualia", testKurobaParserCollector.titleArtistPart)
    assertEquals("PT00H52M57S", testKurobaParserCollector.duration)
  }

  @Test
  fun `parse test page3 (Album) media name`() {
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("parsing/soundcloud_test_html_page3_album.html")
      .readBytes()
    val fileString = String(fileBytes)

    val testKurobaParserCollector = TestAlbumSoundCloudCollector()
    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<TestAlbumSoundCloudCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      kurobaAlbumLinkHtmlParserCommandBuffer,
      testKurobaParserCollector
    )

    assertEquals("Isolation Tapes by Eoin Lyness | Free Listening on SoundCloud", testKurobaParserCollector.titleFull)
  }

  class TestNormalSoundCloudCollector(
    var headerLogoText: String? = null,
    var titleArtistPart: String? = null,
    var titleTrackNamePart: String? = null,
    var duration: String? = null
  ) : KurobaHtmlParserCollector

  class TestAlbumSoundCloudCollector(
    var titleFull: String? = null
  ) : KurobaHtmlParserCollector

}