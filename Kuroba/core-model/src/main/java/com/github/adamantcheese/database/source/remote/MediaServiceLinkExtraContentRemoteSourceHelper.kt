package com.github.adamantcheese.database.source.remote

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.base.ModularResult.Companion.safeRun
import com.github.adamantcheese.database.data.video_service.MediaServiceType
import com.google.gson.JsonElement
import org.joda.time.Period

internal object MediaServiceLinkExtraContentRemoteSourceHelper {
    fun tryExtractVideoDuration(
            mediaServiceType: MediaServiceType,
            parser: JsonElement
    ): ModularResult<Period> {
        return safeRun {
            return@safeRun when (mediaServiceType) {
                MediaServiceType.Youtube -> tryExtractYoutubeVideoDuration(parser)
            }
        }
    }

    fun tryExtractVideoTitle(
            mediaServiceType: MediaServiceType,
            parser: JsonElement
    ): ModularResult<String> {
        return safeRun {
            return@safeRun when (mediaServiceType) {
                MediaServiceType.Youtube -> tryExtractYoutubeVideoTitle(parser)
            }
        }
    }

    private fun tryExtractYoutubeVideoTitle(parser: JsonElement): String {
        return parser.asJsonObject
                .get("items")
                .asJsonArray
                .get(0)
                .asJsonObject
                .get("snippet")
                .asJsonObject
                .get("title")
                .asString
    }

    private fun tryExtractYoutubeVideoDuration(parser: JsonElement): Period {
        val durationUnparsed = parser.asJsonObject
                .get("items")
                .asJsonArray
                .get(0)
                .asJsonObject
                .get("contentDetails")
                .asJsonObject
                .get("duration")
                .asString

        return Period.parse(durationUnparsed)
    }
}