package com.github.k1rakishou.model.source.parser

import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.media.MediaServiceLinkExtraInfo
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import com.squareup.moshi.JsonReader
import okhttp3.ResponseBody
import okio.Buffer
import org.joda.time.Duration
import org.jsoup.Jsoup

object StreamableLinkExtractContentParser : IExtractContentParser {
  override fun parse(
    url: String,
    mediaServiceType: MediaServiceType,
    videoId: GenericVideoId,
    responseBody: ResponseBody
  ): MediaServiceLinkExtraInfo {
    val titleAndDuration = responseBody.use { body ->
      val document = Jsoup.parse(
        body.byteStream(),
        Charsets.UTF_8.name(),
        ""
      )

      return@use document.selectFirst("script[type=application/ld+json][data-testid=structured-metadata]")
        ?.html()
        ?.trim()
        ?.let { metadataJson ->
          val reader = JsonReader.of(Buffer().writeUtf8(metadataJson))
          var description: String? = null
          var duration: String? = null

          reader.beginObject()
          while (reader.hasNext()) {
            val name: String? = reader.nextName()
            if ("description" == name) {
              description = reader.nextString()
            }
            else if ("duration" == name) {
              duration = reader.nextString()
            }
            else {
              reader.skipValue()
            }
          }
          reader.endObject()

          val videoDescription = description
            ?.removePrefix("Watch \"")
            ?.removeSuffix("\" on Streamable.")

          val videoDuration = duration?.let { Duration.parse(it).toPeriod().normalizedStandard() }

          return@let videoDescription to videoDuration
        }
    }

    val videoTitle = titleAndDuration?.first
    val videoDuration = titleAndDuration?.second

    if (videoTitle.isNullOrEmpty() && videoDuration == null) {
      return MediaServiceLinkExtraInfo.empty()
    }

    return MediaServiceLinkExtraInfo(
      videoTitle = videoTitle,
      videoDuration = videoDuration
    )
  }

}