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
import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.media.SoundCloudVideoId
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository
import java.util.regex.Pattern

internal class SoundCloudMediaServiceExtraInfoFetcher(
  private val mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository
) : ExternalMediaServiceExtraInfoFetcher() {

  override val mediaServiceType: MediaServiceType
    get() = MediaServiceType.SoundCloud

  override fun isEnabled(): Boolean {
    return AppModuleAndroidUtils.shouldLoadForNetworkType(
      ChanSettings.parseSoundCloudTitlesAndDuration.get()
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
          soundCloudIcon
        )
      )
    }

    return genericFetch(
      TAG,
      soundCloudIcon,
      requestUrl,
      linkInfoRequest,
      mediaServiceLinkExtraContentRepository
    )
  }

  // set of tracks (we can only get title)
  // https://soundcloud.com/eoinlyness/sets/isolation-tapes

  // single track (we can get title and duration)
  // https://soundcloud.com/slave-beaver-revolt/deluge
  override fun linkMatchesToService(link: String): Boolean {
    if (soundCloudTrackLinkPattern.matcher(link).matches()) {
      return true
    }

    if (soundCloudAlbumLinkPattern.matcher(link).matches()) {
      return true
    }

    return false
  }

  override fun extractLinkVideoId(link: String): GenericVideoId {
    val normalizedLink = normalizeLink(link)

    var matcher = soundCloudTrackLinkPattern.matcher(normalizedLink)
    if (matcher.matches()) {
      val videoId = requireNotNull(matcher.group(1)) {
        "Couldn't extract videoId out of the input link ($normalizedLink)"
      }

      return SoundCloudVideoId(
        id = videoId,
        isAlbumLink = false
      )
    }

    matcher = soundCloudAlbumLinkPattern.matcher(normalizedLink)
    if (matcher.matches()) {
      val videoId = requireNotNull(matcher.group(1)) {
        "Couldn't extract videoId out of the input link ($normalizedLink)"
      }

      return SoundCloudVideoId(
        id = videoId,
        isAlbumLink = true
      )
    }

    throw IllegalStateException("Couldn't match link ($normalizedLink) with the current service. " +
      "Did you forget to call linkMatchesToService() first?")
  }

  private fun normalizeLink(link: String): String {
    if (link.contains(soundCloudMobileLink)) {
      return link.replace(soundCloudMobileLink, soundCloudNormalLink)
    }

    return link
  }

  override fun formatRequestUrl(link: String): String {
    return normalizeLink(link)
  }

  companion object {
    private const val TAG = "SoundCloudMediaServiceExtraInfoFetcher"

    private const val soundCloudMobileLink = "://m.soundcloud.com"
    private const val soundCloudNormalLink = "://soundcloud.com"

    private val soundCloudAlbumLinkPattern = Pattern.compile("https:\\/\\/(?:m\\.)?soundcloud\\.com\\/([a-zA-Z\\-\\_0-9]+\\/sets\\/[a-zA-Z\\-\\_0-9]+)")
    private val soundCloudTrackLinkPattern = Pattern.compile("https:\\/\\/(?:m\\.)?soundcloud\\.com\\/([a-zA-Z\\-\\_0-9]+\\/[a-zA-Z\\-\\_0-9]+)")

    private val soundCloudIcon = BitmapFactory.decodeResource(getRes(), R.drawable.soundcloud_icon)
  }
}