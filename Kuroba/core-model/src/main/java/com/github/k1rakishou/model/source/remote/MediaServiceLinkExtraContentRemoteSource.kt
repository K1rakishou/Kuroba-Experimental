package com.github.k1rakishou.model.source.remote

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.media.MediaServiceLinkExtraInfo
import com.github.k1rakishou.model.data.video_service.ApiType
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import com.github.k1rakishou.model.source.parser.SoundCloudLinkExtractContentParser
import com.github.k1rakishou.model.source.parser.StreamableLinkExtractContentParser
import com.github.k1rakishou.model.source.parser.YoutubeLinkExtractContentParser
import com.github.k1rakishou.model.util.ensureBackgroundThread
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

open class MediaServiceLinkExtraContentRemoteSource(
  okHttpClient: OkHttpClient,
) : AbstractRemoteSource(okHttpClient) {
  private val TAG = "MediaServiceLinkExtraContentRemoteSource"

  open suspend fun fetchFromNetwork(
    requestUrl: String,
    videoId: GenericVideoId,
    mediaServiceType: MediaServiceType
  ): ModularResult<MediaServiceLinkExtraInfo> {
    Logger.d(TAG, "fetchFromNetwork($requestUrl, $mediaServiceType)")
    ensureBackgroundThread()

    return Try {
      val httpRequest = Request.Builder()
        .url(requestUrl)
        .get()
        .build()

      val response = okHttpClient.suspendCall(httpRequest)
      if (!response.isSuccessful) {
        return@Try MediaServiceLinkExtraInfo.empty()
      }

      return@Try extractMediaServiceLinkExtraInfo(
        mediaServiceType,
        videoId,
        response
      )
    }
  }

  private fun extractMediaServiceLinkExtraInfo(
    mediaServiceType: MediaServiceType,
    videoId: GenericVideoId,
    response: Response
  ): MediaServiceLinkExtraInfo {
    ensureBackgroundThread()

    return response.use { resp ->
      return@use resp.body.use { body ->
        if (body == null) {
          return MediaServiceLinkExtraInfo.empty()
        }

        return@use when (mediaServiceType.apiType) {
          ApiType.Html -> useHtmlParser(mediaServiceType, videoId, body)
          ApiType.Json -> throw NotImplementedError("Not implemented because all current fetchers use HTML")
        }
      }
    }
  }

  private fun useHtmlParser(
    mediaServiceType: MediaServiceType,
    videoId: GenericVideoId,
    body: ResponseBody
  ): MediaServiceLinkExtraInfo {
    return when (mediaServiceType) {
      MediaServiceType.SoundCloud -> {
        SoundCloudLinkExtractContentParser.parse(mediaServiceType, videoId, body)
      }
      MediaServiceType.Streamable -> {
        StreamableLinkExtractContentParser.parse(mediaServiceType, videoId, body)
      }
      MediaServiceType.Youtube -> {
        YoutubeLinkExtractContentParser.parse(mediaServiceType, videoId, body)
      }
    }
  }

}