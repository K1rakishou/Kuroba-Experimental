package com.github.adamantcheese.chan.core.loader.impl

import android.text.Spanned
import com.github.adamantcheese.chan.core.loader.LoaderResult
import com.github.adamantcheese.chan.core.loader.LoaderType
import com.github.adamantcheese.chan.core.loader.OnDemandContentLoader
import com.github.adamantcheese.chan.core.loader.PostLoaderData
import com.github.adamantcheese.chan.core.loader.impl.external_media_service.ExternalMediaServiceExtraInfoFetcher
import com.github.adamantcheese.chan.core.loader.impl.post_comment.CommentPostLinkableSpan
import com.github.adamantcheese.chan.core.loader.impl.post_comment.CommentSpanUpdater
import com.github.adamantcheese.chan.core.loader.impl.post_comment.LinkInfoRequest
import com.github.adamantcheese.chan.core.loader.impl.post_comment.SpanUpdateBatch
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.ui.text.span.PostLinkable
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.putIfNotContains
import io.reactivex.*
import okhttp3.*
import org.joda.time.format.PeriodFormatterBuilder
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class PostExtraContentLoader(
        private val okHttpClient: OkHttpClient,
        private val scheduler: Scheduler,
        private val linkExtraInfoFetchers: List<ExternalMediaServiceExtraInfoFetcher>
) : OnDemandContentLoader(LoaderType.PostExtraContentLoader) {

    override fun isAlreadyCached(postLoaderData: PostLoaderData): Boolean {
        // TODO(ODL): Add caching
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

        val postLinkableSpans = parseSpans(comment)
        if (postLinkableSpans.isEmpty()) {
            return reject()
        }

        // TODO(ODL): cache video link titles and durations

        return Single.fromCallable { createNewRequests(postLinkableSpans) }
                .flatMap { newSpans ->
                    if (newSpans.isEmpty()) {
                        return@flatMap reject()
                    }

                    return@flatMap Flowable.fromIterable(newSpans.entries)
                            .subscribeOn(scheduler)
                            .flatMap({ (url, linkInfoRequest) ->
                                return@flatMap fetchExtraLinkInfo(url, linkInfoRequest)
                            }, MAX_CONCURRENT_REQUESTS)
                            .toList()
                            .flatMap { spanUpdateBatchList ->
                                return@flatMap updateSpans(spanUpdateBatchList, postLoaderData)
                            }
                }
                .doOnError { error -> Logger.e(TAG, "Unhandled error", error) }
                .onErrorResumeNext { error() }
    }

    private fun updateSpans(
            spanUpdateBatchList: List<SpanUpdateBatch>,
            postLoaderData: PostLoaderData
    ): Single<LoaderResult> {
        val filteredBatches = spanUpdateBatchList.filter { spanUpdate ->
            !spanUpdate.extraLinkInfo.isEmpty()
        }

        if (filteredBatches.isEmpty()) {
            // No new spans, reject!
            return reject()
        }

        val updated = try {
            CommentSpanUpdater.updateSpansForPostComment(
                    postLoaderData.post,
                    filteredBatches
            )
        } catch (error: Throwable) {
            Logger.e(TAG, "Unknown error while trying to update spans for post comment", error)
            return error()
        }

        if (!updated) {
            // For some unknown reason nothing was updated, reject!
            return reject()
        }

        // Something was updated we need to redraw the post, so return success
        return success()
    }

    private fun fetchExtraLinkInfo(
            url: String,
            linkInfoRequest: LinkInfoRequest
    ): Flowable<SpanUpdateBatch> {
        val flowable = Flowable.create<SpanUpdateBatch>({ emitter ->
            try {
                val httpRequest = Request.Builder()
                        .url(url)
                        .get()
                        .build()

                val call = okHttpClient.newCall(httpRequest)

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        emitter.tryOnError(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        processResponse(linkInfoRequest, url, response, emitter)
                    }
                })
            } catch (error: Throwable) {
                emitter.tryOnError(error)
            }
        }, BackpressureStrategy.BUFFER)

        return flowable
                .timeout(MAX_LINK_INFO_FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun processResponse(
            linkInfoRequest: LinkInfoRequest,
            url: String,
            response: Response,
            emitter: FlowableEmitter<SpanUpdateBatch>
    ) {
        try {
            val fetcher = linkExtraInfoFetchers.firstOrNull { fetcher ->
                fetcher.fetcherType == linkInfoRequest.fetcherType
            }

            if (fetcher == null) {
                emitter.tryOnError(IllegalStateException("Couldn't find fetcher for link $url"))
                return
            }

            val extraLinkInfo = fetcher.extractExtraLinkInfo(response)
            val iconBitmap = fetcher.getIconBitmap()

            val spanUpdateBatch = SpanUpdateBatch(
                    url,
                    extraLinkInfo,
                    linkInfoRequest.oldPostLinkableSpans,
                    iconBitmap
            )

            emitter.onNext(spanUpdateBatch)
            emitter.onComplete()
        } catch (error: Throwable) {
            Logger.e(TAG, "Error while processing response", error)
            emitter.tryOnError(error)
        }
    }

    private fun createNewRequests(postLinkablePostLinkableSpans: List<CommentPostLinkableSpan>): Map<String, LinkInfoRequest> {
        val newSpans = mutableMapOf<String, LinkInfoRequest>()

        postLinkablePostLinkableSpans.forEach { postLinkableSpan ->
            val postLinkable = postLinkableSpan.postLinkable

            if (postLinkable.type == PostLinkable.Type.LINK) {
                val originalLink = postLinkable.key.toString()
                val fetcher = linkExtraInfoFetchers.firstOrNull { fetcher ->
                    fetcher.linkMatchesToService(originalLink)
                }

                if (fetcher == null) {
                    // No fetcher found for this link type
                    return@forEach
                }

                val requestUrl = fetcher.formatRequestUrl(originalLink)
                val linkInfoRequest = LinkInfoRequest(
                        fetcher.fetcherType,
                        mutableListOf()
                )

                newSpans.putIfNotContains(requestUrl, linkInfoRequest)
                newSpans[requestUrl]!!.oldPostLinkableSpans.add(postLinkableSpan)
            }
        }

        return newSpans
    }

    private fun parseSpans(comment: Spanned): List<CommentPostLinkableSpan> {
        val postLinkableSpans = comment.getSpans(0, comment.length, PostLinkable::class.java)
        if (postLinkableSpans.isEmpty()) {
            return emptyList()
        }

        return postLinkableSpans.mapNotNull { postLinkable ->
            if (postLinkable.type != PostLinkable.Type.LINK) {
                // Not a link
                return@mapNotNull null
            }

            val start = comment.getSpanStart(postLinkable)
            val end = comment.getSpanEnd(postLinkable)

            if (start == -1 || end == -1) {
                // Something is wrong with this span
                return@mapNotNull null
            }

            if (start >= end) {
                Logger.e(TAG, "Start ($start) is greater or equals to end ($end) for some unknown reason")
                return@mapNotNull null
            }

            return@mapNotNull CommentPostLinkableSpan(
                    postLinkable,
                    start,
                    end
            )
        }
    }

    override fun cancelLoading(postLoaderData: PostLoaderData) {
        // I guess there is no real need to cancel these requests since they are lightweight
    }

    companion object {
        private const val TAG = "PostExtraContentLoader"
        private const val MAX_CONCURRENT_REQUESTS = 4
        private const val MAX_LINK_INFO_FETCH_TIMEOUT_SECONDS = 3L

        internal val formatterWithoutHours = PeriodFormatterBuilder()
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

        internal val formatterWithHours = PeriodFormatterBuilder()
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
    }
}