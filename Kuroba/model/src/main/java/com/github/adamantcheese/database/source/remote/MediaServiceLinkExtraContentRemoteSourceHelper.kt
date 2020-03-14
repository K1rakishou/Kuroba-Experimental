package com.github.adamantcheese.database.source.remote

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.base.ModularResult.Companion.safeRun
import com.github.adamantcheese.database.data.video_service.MediaServiceType
import com.google.gson.JsonElement
import org.joda.time.Period
import org.joda.time.format.PeriodFormatterBuilder

internal object MediaServiceLinkExtraContentRemoteSourceHelper {
    private val formatterWithoutHours = PeriodFormatterBuilder()
            .appendLiteral("[")
            .minimumPrintedDigits(0) //don't print hours if none
            .appendHours()
            .appendSuffix(":")
            .minimumPrintedDigits(1) //one digit minutes if no hours
            .printZeroAlways() //always print 0 for minutes, if seconds only
            .appendMinutes()
            .appendSuffix(":")
            .minimumPrintedDigits(2) //always print two digit seconds
            .appendSeconds()
            .appendLiteral("]")
            .toFormatter()

    private val formatterWithHours = PeriodFormatterBuilder()
            .appendLiteral("[")
            .minimumPrintedDigits(0) //don't print hours if none
            .appendHours()
            .appendSuffix(":")
            .minimumPrintedDigits(2) //two digit minutes if hours
            .printZeroAlways() //always print 0 for minutes, if seconds only
            .appendMinutes()
            .appendSuffix(":")
            .minimumPrintedDigits(2) //always print two digit seconds
            .appendSeconds()
            .appendLiteral("]")
            .toFormatter()

    fun tryExtractVideoDuration(
            mediaServiceType: MediaServiceType,
            parser: JsonElement
    ): ModularResult<String> {
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

    private fun tryExtractYoutubeVideoDuration(parser: JsonElement): String {
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
            return formatterWithHours.print(time)
        } else {
            return formatterWithoutHours.print(time)
        }
    }
}