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

object StreamableLinkExtractContentParser : IExtractContentParser {
  private val parserCommandBuffer = createParserCommandBuffer()

  override fun parse(
    mediaServiceType: MediaServiceType,
    videoId: GenericVideoId,
    responseBody: ResponseBody
  ): MediaServiceLinkExtraInfo {
    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<StreamableLinkContentCollector>()

    val collector = StreamableLinkContentCollector()

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

    val period = collector.videoDuration
      ?.toFloatOrNull()
      ?.toInt()
      ?.let { seconds -> Period(seconds * 1000L) }

    return MediaServiceLinkExtraInfo(
      collector.videoTitle,
      period
    )
  }

  private fun createParserCommandBuffer():  List<KurobaParserCommand<StreamableLinkContentCollector>> {
    return KurobaHtmlParserCommandBufferBuilder<StreamableLinkContentCollector>()
      .start {
        html()

        nest {
          body()

          nest {
            div(matchableBuilderFunc = {
              className(KurobaMatcher.PatternMatcher.stringEquals("container"))
              attr("id", KurobaMatcher.PatternMatcher.stringEquals("player"))
            })

            nest {
              script(
                matchableBuilderFunc = { attr("data-id", KurobaMatcher.PatternMatcher.stringEquals("player-instream")) },
                attrExtractorBuilderFunc = {
                  extractAttrValueByKey("data-duration")
                  extractAttrValueByKey("data-title")
                },
                extractorFunc = { _, extractAttributeValues, collector ->
                  collector.videoTitle = extractAttributeValues.getAttrValue("data-title")
                  collector.videoDuration = extractAttributeValues.getAttrValue("data-duration")
                }
              )
            }
          }
        }
      }
      .build()
  }

  private data class StreamableLinkContentCollector(
    var videoTitle: String? = null,
    // Streamable's duration is a float value representing seconds
    var videoDuration: String? = null
  ) : KurobaHtmlParserCollector {

    fun isEmpty(): Boolean {
      return videoTitle.isNullOrEmpty()
        && videoDuration.isNullOrEmpty()
    }

  }

}