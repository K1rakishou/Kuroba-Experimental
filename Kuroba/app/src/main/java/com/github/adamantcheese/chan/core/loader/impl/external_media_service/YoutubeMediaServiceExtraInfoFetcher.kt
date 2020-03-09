package com.github.adamantcheese.chan.core.loader.impl.external_media_service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.loader.impl.PostExtraContentLoader
import com.github.adamantcheese.chan.core.loader.impl.post_comment.ExtraLinkInfo
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.groupOrNull
import com.github.adamantcheese.database.dto.YoutubeLinkExtraContent
import com.github.adamantcheese.database.repository.YoutubeLinkExtraContentRepository
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.reactivex.Flowable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.rx2.rxFlowable
import okhttp3.Response
import org.joda.time.DateTime
import org.joda.time.Period
import java.util.regex.Pattern

internal class YoutubeMediaServiceExtraInfoFetcher(
        private val youtubeLinkExtraContentRepository: YoutubeLinkExtraContentRepository
) : ExternalMediaServiceExtraInfoFetcher {

    override val fetcherType: FetcherType
        get() = FetcherType.YoutubeFetcher

    override fun isEnabled(): Boolean {
        if (!ChanSettings.parseYoutubeTitles.get() && !ChanSettings.parseYoutubeDuration.get()) {
            return false
        }

        return true
    }

    override fun getIconBitmap(): Bitmap = youtubeIcon

    @ExperimentalCoroutinesApi
    override fun getFromCache(postUid: String, url: String): Flowable<ModularResult<ExtraLinkInfo?>> {
        return rxFlowable {
            val result = youtubeLinkExtraContentRepository.selectByPostUid(postUid, url)
                    .map { youtubeLnkExtraContent ->
                        if (youtubeLnkExtraContent == null) {
                            return@map null
                        }

                        return@map ExtraLinkInfo(
                                youtubeLnkExtraContent.videoTitle,
                                youtubeLnkExtraContent.videoDuration
                        )
                    }

            send(result)
        }
    }

    @ExperimentalCoroutinesApi
    override fun storeIntoCache(
            postUid: String,
            loadableUid: String,
            url: String,
            extraLinkInfo: ExtraLinkInfo
    ): Flowable<ModularResult<Unit>> {
        return rxFlowable {
            val youtubeLinkExtraContent = YoutubeLinkExtraContent(
                    postUid,
                    loadableUid,
                    url,
                    extraLinkInfo.title,
                    extraLinkInfo.duration,
                    DateTime.now()
            )

            val result = youtubeLinkExtraContentRepository.insert(youtubeLinkExtraContent)
            send(result)
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

    override fun extractExtraLinkInfo(response: Response): ExtraLinkInfo {
        try {
            return response.use { resp ->
                return@use resp.body.use { body ->
                    if (body == null) {
                        return ExtraLinkInfo.empty()
                    }

                    val parser = JsonParser.parseString(body.string())

                    val title = tryExtractTitleOrNull(parser)
                    val duration = tryExtractDurationOrNull(parser)

                    return@use ExtraLinkInfo(title, duration)
                }
            }
        } catch (error: Throwable) {
            Logger.e(TAG, "Error while trying to extract extra link info", error)
            return ExtraLinkInfo.empty()
        }
    }

    private fun tryExtractDurationOrNull(parser: JsonElement): String? {
        return try {
            val durationUnparsed = parser.asJsonObject
                    .get("items")
                    .asJsonArray
                    .get(0)
                    .asJsonObject
                    .get("contentDetails")
                    .asJsonObject
                    .get("duration")
                    .asString

            val time = Period.parse(durationUnparsed)
            if (time.hours > 0) {
                PostExtraContentLoader.formatterWithHours.print(time)
            } else {
                PostExtraContentLoader.formatterWithoutHours.print(time)
            }
        } catch (ignored: Throwable) {
            null
        }
    }

    private fun tryExtractTitleOrNull(parser: JsonElement): String? {
        return try {
            parser.asJsonObject
                    .get("items")
                    .asJsonArray
                    .get(0)
                    .asJsonObject
                    .get("snippet")
                    .asJsonObject
                    .get("title")
                    .asString
        } catch (ignored: Throwable) {
            null
        }
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