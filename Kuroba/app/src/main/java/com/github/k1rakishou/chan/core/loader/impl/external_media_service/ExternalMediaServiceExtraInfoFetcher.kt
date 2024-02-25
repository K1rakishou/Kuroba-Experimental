package com.github.k1rakishou.chan.core.loader.impl.external_media_service

import android.graphics.Bitmap
import com.github.k1rakishou.chan.core.loader.impl.post_comment.ExtraLinkInfo
import com.github.k1rakishou.chan.core.loader.impl.post_comment.LinkInfoRequest
import com.github.k1rakishou.chan.core.loader.impl.post_comment.SpanUpdateBatch
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository

/**
 * Base interface for link extra info fetcher.
 * */
internal abstract class ExternalMediaServiceExtraInfoFetcher {
  /**
   * Each fetcher must have it's own type
   * */
  abstract val mediaServiceType: MediaServiceType

  abstract fun isEnabled(): Boolean

  abstract suspend fun isCached(videoId: GenericVideoId): Boolean

  abstract suspend fun fetch(requestUrl: String, linkInfoRequest: LinkInfoRequest): ModularResult<SpanUpdateBatch>

  /**
   * Whether this fetcher can parse the link
   * */
  abstract fun linkMatchesToService(link: String): Boolean

  /**
   * May be either a unique ID representing this extra info, or, if a media service links do not
   * have a unique id, the whole link
   * */
  abstract fun extractLinkVideoId(link: String): GenericVideoId

  /**
   * An url where the GET request will be sent to retrieve the url metadata
   * */
  abstract fun formatRequestUrl(link: String): String

  protected open suspend fun genericFetch(
    tag: String,
    icon: Bitmap,
    requestUrl: String,
    linkInfoRequest: LinkInfoRequest,
    mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository
  ): ModularResult<SpanUpdateBatch> {
    BackgroundUtils.ensureBackgroundThread()

    val getLinkExtraContentResult = mediaServiceLinkExtraContentRepository.getLinkExtraContent(
      mediaServiceType,
      requestUrl,
      linkInfoRequest.videoId
    )

    return ModularResult.Try {
      val extraLinkInfo = when (getLinkExtraContentResult) {
        is ModularResult.Error -> ExtraLinkInfo.Error
        is ModularResult.Value -> {
          if (getLinkExtraContentResult.value.isValid()) {
            ExtraLinkInfo.Success(
              mediaServiceType,
              getLinkExtraContentResult.value.videoTitle,
              getLinkExtraContentResult.value.videoDuration
            )
          } else {
            ExtraLinkInfo.NotAvailable
          }
        }
      }

      return@Try SpanUpdateBatch(
        requestUrl,
        extraLinkInfo,
        linkInfoRequest.oldPostLinkableSpans,
        icon
      )
    }.onError { error ->
      Logger.e(tag, "Error while processing response", error)
    }
  }
}