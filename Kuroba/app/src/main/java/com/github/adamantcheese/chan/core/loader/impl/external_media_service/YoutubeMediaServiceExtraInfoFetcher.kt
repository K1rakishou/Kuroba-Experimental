package com.github.adamantcheese.chan.core.loader.impl.external_media_service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.loader.impl.post_comment.CommentPostLinkableSpan
import com.github.adamantcheese.chan.core.loader.impl.post_comment.ExtraLinkInfo
import com.github.adamantcheese.chan.core.loader.impl.post_comment.LinkInfoRequest
import com.github.adamantcheese.chan.core.loader.impl.post_comment.SpanUpdateBatch
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.groupOrNull
import com.github.adamantcheese.database.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.database.data.video_service.MediaServiceType
import com.github.adamantcheese.database.repository.MediaServiceLinkExtraContentRepository
import io.reactivex.Single
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.rx2.rxSingle
import java.util.regex.Pattern

internal class YoutubeMediaServiceExtraInfoFetcher(
        private val mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository
) : ExternalMediaServiceExtraInfoFetcher {

    override val mediaServiceType: MediaServiceType
        get() = MediaServiceType.Youtube

    override fun isEnabled(): Boolean {
        if (!ChanSettings.parseYoutubeTitles.get() && !ChanSettings.parseYoutubeDuration.get()) {
            return false
        }

        return true
    }

    private fun getIconBitmap(): Bitmap = youtubeIcon

    @ExperimentalCoroutinesApi
    override fun fetch(
            requestUrl: String,
            linkInfoRequest: LinkInfoRequest
    ): Single<ModularResult<SpanUpdateBatch>> {
        return rxSingle {
            val getLinkExtraContentResult = mediaServiceLinkExtraContentRepository.getLinkExtraContent(
                    mediaServiceType,
                    requestUrl,
                    linkInfoRequest.originalUrl
            )

            return@rxSingle processResponse(
                    requestUrl,
                    getLinkExtraContentResult,
                    linkInfoRequest.oldPostLinkableSpans
            )
        }
    }

    private fun processResponse(
            url: String,
            mediaServiceLinkExtraContentResult: ModularResult<MediaServiceLinkExtraContent>,
            oldPostLinkableSpans: List<CommentPostLinkableSpan>
    ): ModularResult<SpanUpdateBatch> {
        try {
            val extraLinkInfo = when (mediaServiceLinkExtraContentResult) {
                is ModularResult.Error -> ExtraLinkInfo.error()
                is ModularResult.Value -> ExtraLinkInfo.value(
                        mediaServiceLinkExtraContentResult.value.videoTitle,
                        mediaServiceLinkExtraContentResult.value.videoDuration
                )
            }

            val spanUpdateBatch = SpanUpdateBatch(
                    url,
                    extraLinkInfo,
                    oldPostLinkableSpans,
                    getIconBitmap()
            )

            return ModularResult.value(spanUpdateBatch)
        } catch (error: Throwable) {
            Logger.e(TAG, "Error while processing response", error)
            return ModularResult.error(error)
        }
    }

    override fun linkMatchesToService(link: String): Boolean {
        return youtubeLinkPattern.matcher(link).matches()
    }

    override fun formatRequestUrl(link: String): String {
        val matcher = youtubeLinkPattern.matcher(link)

        if (!matcher.find()) {
            throw IllegalStateException("Couldn't match link ($link) even " +
                    "though linkMatchesToService matched it earlier.")
        }

        val videoId = checkNotNull(matcher.groupOrNull(1)) {
            "Couldn't extract videoId out of the input link ($link)"
        }

        return formatGetYoutubeLinkInfoUrl(videoId)
    }

    private fun formatGetYoutubeLinkInfoUrl(videoId: String): String {
        return buildString {
            append("https://www.googleapis.com/youtube/v3/videos?part=snippet")

            if (ChanSettings.parseYoutubeDuration.get()) {
                append("%2CcontentDetails")
            }

            append("&id=")
            append(videoId)
            append("&fields=items%28id%2Csnippet%28title%29")

            if (ChanSettings.parseYoutubeDuration.get()) {
                append("%2CcontentDetails%28duration%29")
            }

            append("%29&key=")
            append(ChanSettings.parseYoutubeAPIKey.get())
        }
    }

    companion object {
        private const val TAG = "YoutubeMediaServiceExtraInfoFetcher"

        private val youtubeLinkPattern =
                Pattern.compile("\\b\\w+://(?:youtu\\.be/|[\\w.]*youtube[\\w.]*/.*?(?:v=|\\bembed/|\\bv/))([\\w\\-]{11})(.*)\\b")
        private val youtubeIcon = BitmapFactory.decodeResource(AndroidUtils.getRes(), R.drawable.youtube_icon)
    }
}