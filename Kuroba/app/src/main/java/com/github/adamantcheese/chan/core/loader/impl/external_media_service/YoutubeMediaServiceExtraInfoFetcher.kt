package com.github.adamantcheese.chan.core.loader.impl.external_media_service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.loader.impl.PostExtraContentLoader
import com.github.adamantcheese.chan.core.loader.impl.post_comment.ExtraLinkInfo
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.groupOrNull
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.Response
import org.joda.time.Period
import java.util.regex.Pattern

internal class YoutubeMediaServiceExtraInfoFetcher : ExternalMediaServiceExtraInfoFetcher {

    override val fetcherType: FetcherType
        get() = FetcherType.YoutubeFetcher

    override fun getIconBitmap(): Bitmap = youtubeIcon

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
        private val youtubeIcon
                = BitmapFactory.decodeResource(AndroidUtils.getRes(), R.drawable.youtube_icon)
    }
}