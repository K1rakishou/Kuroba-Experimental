package com.github.adamantcheese.chan.core.loader.impl

import android.text.Spanned
import android.text.style.CharacterStyle
import com.github.adamantcheese.chan.core.loader.LoaderResult
import com.github.adamantcheese.chan.core.loader.LoaderType
import com.github.adamantcheese.chan.core.loader.OnDemandContentLoader
import com.github.adamantcheese.chan.core.loader.PostLoaderData
import com.github.adamantcheese.chan.core.misc.CommentSpanUpdater
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.ui.text.span.PostLinkable
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.groupOrNull
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import org.joda.time.Period
import org.joda.time.format.PeriodFormatterBuilder
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class PostExtraContentLoader(
        private val okHttpClient: OkHttpClient
) : OnDemandContentLoader(LoaderType.PostExtraContentLoader) {

    // TODO(ODL): change this to an injected schedulers after merge with dev
    private val schedulers: Scheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    override fun isAlreadyCached(postLoaderData: PostLoaderData): Boolean {
        // TODO(ODL)
        return false
    }

    override fun startLoading(postLoaderData: PostLoaderData): Single<LoaderResult> {
        if (!ChanSettings.parseYoutubeTitles.get() && !ChanSettings.parseYoutubeDuration.get()) {
            return reject()
        }

        val comment = postLoaderData.post.comment
        if (comment.isEmpty() || comment !is Spanned) {
            return reject()
        }

        val spans = parseSpans(comment)
        if (spans.isEmpty()) {
            return reject()
        }

        // TODO(ODL): cache video link titles and durations
        // TODO(ODL): need to somehow filter out comments where all post linkables were already processed
        // TODO(ODL): sometimes one link is being processed twice (apparently) which results in
        //  having doubled titles and durations

        return Single.fromCallable { createNewSpans(spans) }
                .flatMap { fullLoaderInfo ->
                    if (fullLoaderInfo.newSpansWithRequests.isEmpty()) {
                        return@flatMap reject()
                    }

                    return@flatMap Flowable.fromIterable(fullLoaderInfo.newSpansWithRequests)
                            .subscribeOn(schedulers)
                            .flatMap({ fetchExtraLinkInfo(it) }, MAX_CONCURRENT_REQUESTS)
                            .toList()
                            .flatMap { spanUpdateList -> updateSpans(spanUpdateList, postLoaderData) }
                }
                .onErrorResumeNext { error() }
    }

    private fun updateSpans(
            spanUpdateList: List<CommentSpanUpdater.SpanUpdate>,
            postLoaderData: PostLoaderData
    ): Single<LoaderResult> {
        val nonEmptySpanUpdates = spanUpdateList.filter { spanUpdate ->
            !spanUpdate.extraLinkInfo.isEmpty()
        }

        if (nonEmptySpanUpdates.isEmpty()) {
            // No new spans, reject!
            return reject()
        }

        val updated = try {
            CommentSpanUpdater.updateSpansForPostComment(
                    postLoaderData.post,
                    nonEmptySpanUpdates
            )
        } catch (error: Throwable) {
            Logger.e(TAG, "Unknown error while trying to update spans for post comment")
            return error()
        }

        if (!updated) {
            // For some reason nothing was updated, reject!
            return reject()
        }

        // Something was updated we need to redraw the post, so return success
        return success()
    }

    private fun fetchExtraLinkInfo(
            requestWithSpan: RequestWithOriginalSpan
    ): Flowable<CommentSpanUpdater.SpanUpdate> {
        val flowable = Flowable.create<CommentSpanUpdater.SpanUpdate>({ emitter ->
            try {
                val httpRequest = Request.Builder()
                        .url(requestWithSpan.requestUrl)
                        .get()
                        .build()

                val call = okHttpClient.newCall(httpRequest)

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        emitter.tryOnError(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val extraLinkInfo = tryExtractExtraLinkInfo(response)
                            val infoWithSpan = CommentSpanUpdater.SpanUpdate(
                                    extraLinkInfo,
                                    requestWithSpan.span
                            )

                            emitter.onNext(infoWithSpan)
                            emitter.onComplete()
                        } catch (error: Throwable) {
                            emitter.onError(error)
                        }
                    }
                })
            } catch (error: Throwable) {
                emitter.tryOnError(error)
            }
        }, BackpressureStrategy.BUFFER)

        return flowable
                .timeout(MAX_LINK_INFO_FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun tryExtractExtraLinkInfo(response: Response): CommentSpanUpdater.ExtraLinkInfo {
        return response.use { resp ->
            return@use resp.body.use { body ->
                if (body == null) {
                    return CommentSpanUpdater.ExtraLinkInfo.empty()
                }

                val parser = JsonParser.parseString(body.string())

                val title = tryExtractTitleOrNull(parser)
                val duration = tryExtractDurationOrNull(parser)

                return@use CommentSpanUpdater.ExtraLinkInfo(title, duration)
            }
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
                formatterWithHours.print(time)
            } else {
                formatterWithoutHours.print(time)
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

    private fun createNewSpans(
            spans: List<CommentSpanUpdater.CommentSpan>
    ): NewSpans {
        val newSpansWithRequests = mutableListOf<RequestWithOriginalSpan>()

        spans.forEach { span ->
            val style = span.style

            if (style is PostLinkable && style.type == PostLinkable.Type.LINK) {
                val link = style.key
                // TODO(ODL): hide into a separate class
                val matcher = youtubeLinkPattern.matcher(link)

                if (matcher.find()) {
                    val videoId = matcher.groupOrNull(1)
                            ?: return@forEach
                    val requestUrl = formatGetYoutubeLinkInfoUrl(videoId)
                    newSpansWithRequests.add(RequestWithOriginalSpan(requestUrl, span))
                }
            }
        }

        return NewSpans(newSpansWithRequests)
    }

    // TODO(ODL): hide into a separate Youtube link handler class
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

    private fun parseSpans(comment: Spanned): List<CommentSpanUpdater.CommentSpan> {
        val spans = comment.getSpans(0, comment.length, CharacterStyle::class.java)
        if (spans.isEmpty()) {
            return emptyList()
        }

        return spans.mapNotNull { span ->
            val start = comment.getSpanStart(span)
            val end = comment.getSpanEnd(span)

            if (start >= end) {
                Logger.e(TAG, "Start ($start) is greater or equals to end ($end) for some unknown reason" )
                return@mapNotNull null
            }

            return@mapNotNull CommentSpanUpdater.CommentSpan(
                    span is PostLinkable,
                    span,
                    start,
                    end
            )
        }
    }

    override fun cancelLoading(postLoaderData: PostLoaderData) {
        // TODO(ODL)
    }

    private data class NewSpans(
            val newSpansWithRequests: List<RequestWithOriginalSpan>
    )

    private data class RequestWithOriginalSpan(
            val requestUrl: String,
            val span: CommentSpanUpdater.CommentSpan
    )

    companion object {
        private const val TAG = "PostExtraContentLoader"
        private const val MAX_CONCURRENT_REQUESTS = 4
        private const val MAX_LINK_INFO_FETCH_TIMEOUT_SECONDS = 3L

        // TODO(ODL): hide into a separate class
        private val youtubeLinkPattern =
                Pattern.compile("\\b\\w+://(?:youtu\\.be/|[\\w.]*youtube[\\w.]*/.*?(?:v=|\\bembed/|\\bv/))([\\w\\-]{11})(.*)\\b")

        private val formatterWithoutHours = PeriodFormatterBuilder().appendLiteral("[")
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

        private val formatterWithHours = PeriodFormatterBuilder().appendLiteral("[")
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
    }
}