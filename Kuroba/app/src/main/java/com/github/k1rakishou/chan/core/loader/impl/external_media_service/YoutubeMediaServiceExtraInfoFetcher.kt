package com.github.k1rakishou.chan.core.loader.impl.external_media_service

import android.graphics.BitmapFactory
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.loader.impl.post_comment.ExtraLinkInfo
import com.github.k1rakishou.chan.core.loader.impl.post_comment.LinkInfoRequest
import com.github.k1rakishou.chan.core.loader.impl.post_comment.SpanUpdateBatch
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getRes
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository
import java.util.regex.Pattern

internal class YoutubeMediaServiceExtraInfoFetcher(
  private val mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository
) : ExternalMediaServiceExtraInfoFetcher() {

  override val mediaServiceType: MediaServiceType
    get() = MediaServiceType.Youtube

  override fun isEnabled(): Boolean {
    return AppModuleAndroidUtils.shouldLoadForNetworkType(
      ChanSettings.parseYoutubeTitlesAndDuration.get()
    )
  }

  override suspend fun isCached(videoId: GenericVideoId): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    return mediaServiceLinkExtraContentRepository.isCached(videoId, mediaServiceType)
      .unwrap()
  }

  override suspend fun fetch(
    requestUrl: String,
    linkInfoRequest: LinkInfoRequest
  ): ModularResult<SpanUpdateBatch> {
    BackgroundUtils.ensureBackgroundThread()

    if (!isEnabled()) {
      return ModularResult.value(
        SpanUpdateBatch(
          requestUrl,
          ExtraLinkInfo.Success(mediaServiceType, null, null),
          linkInfoRequest.oldPostLinkableSpans,
          youtubeIcon
        )
      )
    }

    return genericFetch(
      TAG,
      youtubeIcon,
      requestUrl,
      linkInfoRequest,
      mediaServiceLinkExtraContentRepository
    )
  }

  override fun linkMatchesToService(link: String): Boolean {
    return youtubeLinkPattern.matcher(link).matches()
  }

  override fun extractLinkVideoId(link: String): GenericVideoId {
    return GenericVideoId(extractVideoId(link))
  }

  override fun formatRequestUrl(link: String): String {
    return link
  }

  private fun extractVideoId(link: String): String {
    val matcher = youtubeLinkPattern.matcher(link)
    if (!matcher.matches()) {
      throw IllegalStateException("Couldn't match link ($link) with the current service. " +
        "Did you forget to call linkMatchesToService() first?")
    }

    return checkNotNull(matcher.groupOrNull(1)) {
      "Couldn't extract videoId out of the input link ($link)"
    }
  }

  companion object {
    private const val TAG = "YoutubeMediaServiceExtraInfoFetcher"

    private val youtubeLinkPattern =
      Pattern.compile("\\b\\w+://(?:youtu\\.be/|[\\w.]*youtube[\\w.]*/.*?(?:v=|\\bembed/|\\bv/))([\\w\\-]{5,11})(.*)\\b")
    private val youtubeIcon = BitmapFactory.decodeResource(getRes(), R.drawable.youtube_icon)
  }
}