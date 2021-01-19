package com.github.k1rakishou.model.source.parser

import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandBufferBuilder
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import com.github.k1rakishou.core_parser.html.KurobaMatcher
import com.github.k1rakishou.core_parser.html.commands.KurobaParserCommand
import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.media.MediaServiceLinkExtraInfo
import com.github.k1rakishou.model.data.media.SoundCloudVideoId
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import okhttp3.ResponseBody
import org.joda.time.Period
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object SoundCloudLinkExtractContentParser : IExtractContentParser {
  private const val SOUNDCLOUD_TITLE_SUFFIX_TO_REMOVE = " | Free Listening on SoundCloud"

  private val normalLinkParserCommandBuffer = createNormalParserCommandBuffer()
  private val albumLinkParserCommandBuffer = createAlbumParserCommandBuffer()

  override fun parse(
    mediaServiceType: MediaServiceType,
    videoId: GenericVideoId,
    responseBody: ResponseBody
  ): MediaServiceLinkExtraInfo {
    videoId as SoundCloudVideoId

    if (videoId.isAlbumLink) {
      return parseAlbumLink(mediaServiceType, videoId, responseBody)
    } else {
      return parseNormalLink(mediaServiceType, videoId, responseBody)
    }
  }

  private fun parseNormalLink(
    mediaServiceType: MediaServiceType,
    videoId: SoundCloudVideoId,
    responseBody: ResponseBody
  ): MediaServiceLinkExtraInfo {
    val parserCommandExecutor = KurobaHtmlParserCommandExecutor<SoundCloudNormalLinkContentCollector>()
    val collector = SoundCloudNormalLinkContentCollector()

    responseBody.use { body ->
      val document = Jsoup.parse(
        body.byteStream(),
        Charsets.UTF_8.name(),
        ""
      )

      parserCommandExecutor.executeCommands(
        document,
        normalLinkParserCommandBuffer,
        collector
      )
    }

    if (collector.isEmpty()) {
      return MediaServiceLinkExtraInfo.empty()
    }

    val fullTitle = buildString {
      if (collector.titleTrackNamePart.isNullOrEmpty() && collector.titleArtistPart.isNullOrEmpty()) {
        append("Unknown")
        return@buildString
      }

      if (!collector.titleTrackNamePart.isNullOrEmpty() && collector.titleArtistPart.isNullOrEmpty()) {
        append("Track: ")
        append(collector.titleTrackNamePart)
        return@buildString
      }

      if (collector.titleTrackNamePart.isNullOrEmpty() && !collector.titleArtistPart.isNullOrEmpty()) {
        append("Artist: ")
        append(collector.titleArtistPart)
        return@buildString
      }

      append(collector.titleTrackNamePart)
      append(" by ")
      append(collector.titleArtistPart)
    }

    val duration = collector.videoDuration?.let { duration -> Period.parse(duration) }

    return MediaServiceLinkExtraInfo(
      fullTitle,
      duration
    )
  }

  private fun parseAlbumLink(
    mediaServiceType: MediaServiceType,
    videoId: SoundCloudVideoId,
    responseBody: ResponseBody
  ): MediaServiceLinkExtraInfo {
    val parserCommandExecutor = KurobaHtmlParserCommandExecutor<SoundCloudAlbumLinkContentCollector>()
    val collector = SoundCloudAlbumLinkContentCollector()

    responseBody.use { body ->
      val document = Jsoup.parse(
        body.byteStream(),
        Charsets.UTF_8.name(),
        ""
      )

      parserCommandExecutor.executeCommands(
        document,
        albumLinkParserCommandBuffer,
        collector
      )
    }

    if (collector.isEmpty()) {
      return MediaServiceLinkExtraInfo.empty()
    }

    val title = collector.fullTitle
      ?.let { fullTitle -> fullTitle.removeSuffix(SOUNDCLOUD_TITLE_SUFFIX_TO_REMOVE) }

    return MediaServiceLinkExtraInfo(
      title,
      null
    )
  }

  // TODO(KurobaEx / @Testme!!!):
  private fun createAlbumParserCommandBuffer(): List<KurobaParserCommand<SoundCloudAlbumLinkContentCollector>> {
    return KurobaHtmlParserCommandBufferBuilder<SoundCloudAlbumLinkContentCollector>()
      .start {
        html()

        nest {
          head()

          nest {
            title(
              attrExtractorBuilderFunc = { extractText() },
              extractorFunc = { _, extractedAttributeValues, collector ->
                collector.fullTitle = extractedAttributeValues.getText()
              }
            )
          }
        }
      }
      .build()
  }

  // TODO(KurobaEx / @Testme!!!):
  private fun createNormalParserCommandBuffer(): List<KurobaParserCommand<SoundCloudNormalLinkContentCollector>> {
    return KurobaHtmlParserCommandBufferBuilder<SoundCloudNormalLinkContentCollector>()
      .start {
        html()

        nest {
          body()

          nest {
            div(matchableBuilderFunc = { id(KurobaMatcher.PatternMatcher.stringEquals("app")) })

            nest {
              noscript(matchableBuilderFunc = { emptyTag() })

              nest {
                article(matchableBuilderFunc = { emptyTag() })

                nest {
                  header(matchableBuilderFunc = { emptyTag() })

                  nest {
                    heading(
                      headingNum = 1,
                      attrExtractorBuilderFunc = { expectAttrWithValue("itemprop", KurobaMatcher.PatternMatcher.stringEquals("name")) }
                    )

                    nest {
                      a(
                        attrExtractorBuilderFunc = { expectAttrWithValue("itemprop", KurobaMatcher.PatternMatcher.stringEquals("url")) },
                        extractorFunc = { node, _, collector ->
                          collector.titleTrackNamePart = (node as Element).text()
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
                        collector.videoDuration = extractedAttrValues.getAttrValue("content")
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
  }

  private data class SoundCloudNormalLinkContentCollector(
    var titleTrackNamePart: String? = null,
    var titleArtistPart: String? = null,
    var videoDuration: String? = null
  ) : KurobaHtmlParserCollector {
    fun isEmpty(): Boolean {
      return titleArtistPart.isNullOrEmpty()
        && titleArtistPart.isNullOrEmpty()
        && videoDuration.isNullOrEmpty()
    }
  }

  private data class SoundCloudAlbumLinkContentCollector(
    var fullTitle: String? = null
  ) : KurobaHtmlParserCollector {
    fun isEmpty(): Boolean = fullTitle.isNullOrEmpty()
  }

}