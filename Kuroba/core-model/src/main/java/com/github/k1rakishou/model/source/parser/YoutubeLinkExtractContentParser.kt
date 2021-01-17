package com.github.k1rakishou.model.source.parser

import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandBufferBuilder
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import com.github.k1rakishou.core_parser.html.KurobaMatcher
import com.github.k1rakishou.core_parser.html.commands.KurobaParserCommand
import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.media.MediaServiceLinkExtraInfo
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import okhttp3.ResponseBody
import org.joda.time.Period
import org.jsoup.Jsoup

object YoutubeLinkExtractContentParser : IExtractContentParser {
  private val parserCommandBuffer = createParserCommandBuffer()

  override fun parse(
    mediaServiceType: MediaServiceType,
    videoId: GenericVideoId,
    responseBody: ResponseBody
  ): MediaServiceLinkExtraInfo {
    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<YoutubeLinkContentCollector>()

    val collector = YoutubeLinkContentCollector()

    responseBody.use { body ->
      val document = Jsoup.parse(
        body.byteStream(),
        Charsets.UTF_8.name(),
        ""
      )

      parserCommandExecutor.executeCommands(
        document,
        parserCommandBuffer,
        collector
      )
    }

    if (collector.isEmpty()) {
      return MediaServiceLinkExtraInfo.empty()
    }

    val duration = collector.videoDuration?.let { duration -> Period.parse(duration) }

    return MediaServiceLinkExtraInfo(
      collector.videoTitle,
      duration
    )
  }

  // TODO(KurobaEx / @Testme!!!):
  private fun createParserCommandBuffer():  List<KurobaParserCommand<YoutubeLinkContentCollector>> {
    return KurobaHtmlParserCommandBufferBuilder<YoutubeLinkContentCollector>()
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
                    collector.videoTitle = extractedAttrValues.getAttrValue("content")
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
                    collector.videoDuration = extractedAttrValues.getAttrValue("content")
                  }
                )
              }
            }
          }
        }
      }
      .build()
  }

  private data class YoutubeLinkContentCollector(
    var videoTitle: String? = null,
    var videoDuration: String? = null
  ) : KurobaHtmlParserCollector {

    fun isEmpty(): Boolean {
      return videoTitle.isNullOrEmpty()
        && videoDuration.isNullOrEmpty()
    }

  }
}