package com.github.k1rakishou.chan.core.site.parser.html

import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.Logger
import junit.framework.Assert.assertEquals
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(value = [Logger::class, AndroidUtils::class])
class KurobaSoundCloudHtmlParserTest : BaseHtmlParserTest() {

  private val kurobaHtmlParserCommandBuffer = KurobaHtmlParserCommandBufferBuilder<TestSoundCloudCollector>()
    .group(groupName = "Main group") {
      htmlElement { html() }
      htmlElement { body() }
      htmlElement { div(id = KurobaMatcher.stringEquals("app")) }

      group(groupName = "Parse header text") {
        htmlElement {
          div(className = KurobaMatcher.stringEquals("header sc-selection-disabled show fixed g-dark g-z-index-header"))
        }
        htmlElement {
          div(className = KurobaMatcher.stringEquals("header__inner l-container l-fullwidth"))
        }
        htmlElement {
          div(className = KurobaMatcher.stringEquals("header__left left"))
        }
        htmlElement {
          div(
            className = KurobaMatcher.stringEquals("header__logo left"),
            extractor = { node, collector ->
              collector.headerLogoText = (node as Element).text()
            }
          )
        }
      }

      group(groupName = "Parse video title and duration") {
        htmlElement { noscript() }
        htmlElement { article() }
        htmlElement { header() }

        group(groupName = "Parse video track name and artist name") {
          htmlElement {
            heading(
              headingNum = 1,
              attr = { expectAttrWithValue("itemprop", KurobaMatcher.stringEquals("name")) }
            )
          }

          preserveCommandIndex {
            group(groupName = "Parse track name") {
              htmlElement {
                a(
                  attr = { expectAttrWithValue("itemprop", KurobaMatcher.stringEquals("url")) },
                  extractor = { node, _, collector ->
                    collector.titleTrackNamePart = (node as Element).text()
                  }
                )
              }
            }
            group(groupName = "Parse video artist name") {
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
          }
        }

        group(groupName = "Parse video duration") {
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

    val testKurobaParserCollector = TestSoundCloudCollector()
    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<TestSoundCloudCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      kurobaHtmlParserCommandBuffer,
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

    val testKurobaParserCollector = TestSoundCloudCollector()
    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<TestSoundCloudCollector>(debugMode = true)

    parserCommandExecutor.executeCommands(
      Jsoup.parse(fileString),
      kurobaHtmlParserCommandBuffer,
      testKurobaParserCollector
    )

    assertEquals("SoundCloud", testKurobaParserCollector.headerLogoText)
    assertEquals("Enter The ANISON MATRIX!!", testKurobaParserCollector.titleTrackNamePart)
    assertEquals("Go-qualia", testKurobaParserCollector.titleArtistPart)
    assertEquals("PT00H52M57S", testKurobaParserCollector.duration)
  }

  class TestSoundCloudCollector(
    var headerLogoText: String? = null,
    var titleArtistPart: String? = null,
    var titleTrackNamePart: String? = null,
    var duration: String? = null
  ) : KurobaHtmlParserCollector

}