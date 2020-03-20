package com.github.adamantcheese.chan.core.loader.impl.external_media_service

import android.graphics.BitmapFactory
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.loader.impl.post_comment.CommentPostLinkableSpan
import com.github.adamantcheese.chan.core.loader.impl.post_comment.ExtraLinkInfo
import com.github.adamantcheese.chan.core.loader.impl.post_comment.LinkInfoRequest
import com.github.adamantcheese.chan.core.loader.impl.post_comment.SpanUpdateBatch
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.groupOrNull
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.model.data.video_service.MediaServiceType
import com.github.adamantcheese.model.repository.MediaServiceLinkExtraContentRepository
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
        // We can't disable it here because we want to always prepend Youtube links with it's icon
        return true
    }

    @ExperimentalCoroutinesApi
    override fun fetch(
            requestUrl: String,
            linkInfoRequest: LinkInfoRequest
    ): Single<ModularResult<SpanUpdateBatch>> {
        BackgroundUtils.ensureBackgroundThread()

        return rxSingle {
            if (!ChanSettings.parseYoutubeTitlesAndDuration.get()) {
                return@rxSingle ModularResult.value(
                        SpanUpdateBatch(
                                requestUrl,
                                ExtraLinkInfo.Success(null, null),
                                linkInfoRequest.oldPostLinkableSpans,
                                youtubeIcon
                        )
                )
            }

            val getLinkExtraContentResult = mediaServiceLinkExtraContentRepository.getLinkExtraContent(
                    mediaServiceType,
                    requestUrl,
                    linkInfoRequest.videoId
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
        BackgroundUtils.ensureBackgroundThread()

        return ModularResult.safeRun {
            val extraLinkInfo = when (mediaServiceLinkExtraContentResult) {
                is ModularResult.Error -> ExtraLinkInfo.Error
                is ModularResult.Value -> {
                    if (mediaServiceLinkExtraContentResult.value.videoDuration == null
                            && mediaServiceLinkExtraContentResult.value.videoDuration == null) {
                        ExtraLinkInfo.NotAvailable
                    } else {
                        ExtraLinkInfo.Success(
                                mediaServiceLinkExtraContentResult.value.videoTitle,
                                mediaServiceLinkExtraContentResult.value.videoDuration
                        )
                    }
                }
            }

            return@safeRun SpanUpdateBatch(
                    url,
                    extraLinkInfo,
                    oldPostLinkableSpans,
                    youtubeIcon
            )
        }.peekError { error ->
            Logger.e(TAG, "Error while processing response", error)
        }
    }

    override fun linkMatchesToService(link: String): Boolean {
        return youtubeLinkPattern.matcher(link).matches()
    }

    override fun extractLinkUniqueIdentifier(link: String): String {
        return extractVideoId(link)
    }

    override fun formatRequestUrl(link: String): String {
        return formatGetYoutubeLinkInfoUrl(extractVideoId(link))
    }

    private fun extractVideoId(link: String): String {
        val matcher = youtubeLinkPattern.matcher(link)
        if (!matcher.find()) {
            throw IllegalStateException("Couldn't match link ($link) with the current service." +
                    " Did you forget to call linkMatchesToService() first?")
        }

        return checkNotNull(matcher.groupOrNull(1)) {
            "Couldn't extract videoId out of the input link ($link)"
        }
    }

    private fun formatGetYoutubeLinkInfoUrl(videoId: String): String {
        return buildString {
            append("https://www.googleapis.com/youtube/v3/videos?part=snippet%2CcontentDetails&id=")

            append(videoId)
            append("&fields=items%28id%2Csnippet%28title%29%2CcontentDetails%28duration%29%29&key=")

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