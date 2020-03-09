package com.github.adamantcheese.chan.core.loader.impl

import android.graphics.Bitmap
import android.text.Spanned
import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.chan.core.loader.LoaderResult
import com.github.adamantcheese.chan.core.loader.LoaderType
import com.github.adamantcheese.chan.core.loader.OnDemandContentLoader
import com.github.adamantcheese.chan.core.loader.PostLoaderData
import com.github.adamantcheese.chan.core.loader.impl.external_media_service.ExternalMediaServiceExtraInfoFetcher
import com.github.adamantcheese.chan.core.loader.impl.post_comment.CommentPostLinkableSpan
import com.github.adamantcheese.chan.core.loader.impl.post_comment.CommentSpanUpdater
import com.github.adamantcheese.chan.core.loader.impl.post_comment.LinkInfoRequest
import com.github.adamantcheese.chan.core.loader.impl.post_comment.SpanUpdateBatch
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

    override fun startLoading(postLoaderData: PostLoaderData): Single<LoaderResult> {
        val comment = postLoaderData.post.comment
        if (comment.isEmpty() || comment !is Spanned) {
            return rejected()
        }

        val postLinkableSpans = parseSpans(comment)
        if (postLinkableSpans.isEmpty()) {
            return rejected()
        }

        return Single.fromCallable { createNewRequests(postLinkableSpans) }
                .flatMap { newSpans ->
                    if (newSpans.isEmpty()) {
                        return@flatMap rejected()
                    }

                    return@flatMap Flowable.fromIterable(newSpans.entries)
                            .subscribeOn(scheduler)
                            .flatMap({ (url, linkInfoRequest) ->
                                return@flatMap fetchExtraLinkInfo(
                                        postLoaderData.getPostUniqueId(),
                                        postLoaderData.getLoadableUniqueId(),
                                        url,
                                        linkInfoRequest
                                )
                            }, MAX_CONCURRENT_REQUESTS)
                            .toList()
                            .flatMap { spanUpdateBatchResultList ->
                                val spanUpdateBatchList = spanUpdateBatchResultList
                                        .mapNotNull { it.valueOrNull() }

                                return@flatMap updateSpans(spanUpdateBatchList, postLoaderData)
                            }
                }
                .doOnError { error -> Logger.e(TAG, "Unhandled error", error) }
                .onErrorResumeNext { failed() }
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
            return rejected()
        }

        val updated = try {
            CommentSpanUpdater.updateSpansForPostComment(
                    postLoaderData.post,
                    filteredBatches
            )
        } catch (error: Throwable) {
            Logger.e(TAG, "Unknown error while trying to update spans for post comment", error)
            return failed()
        }

        if (!updated) {
            // For some unknown reason nothing was updated, reject!
            return rejected()
        }

        // Something was updated we need to redraw the post, so return success
        return succeeded()
    }

    private fun fetchExtraLinkInfo(
            postUid: String,
            loadableUid: String,
            requestUrl: String,
            linkInfoRequest: LinkInfoRequest
    ): Flowable<ModularResult<SpanUpdateBatch>> {
        val fetcher = linkExtraInfoFetchers.firstOrNull { fetcher ->
            fetcher.fetcherType == linkInfoRequest.fetcherType
        }

        if (fetcher == null) {
            val error = ModularResult.error<SpanUpdateBatch>(
                    IllegalStateException("Couldn't find fetcher for fetcher " +
                            "type ${linkInfoRequest.fetcherType}")
            )

            return Flowable.just(error)
        }

        val iconBitmap = fetcher.getIconBitmap()

        // TODO(ODL): move this into the database module (and it should probably be renamed into
        //  model module)
        return fetcher.getFromCache(postUid, linkInfoRequest.originalUrl)
                .flatMap { extraLinkInfoResult ->
                    if (extraLinkInfoResult is ModularResult.Value) {
                        val extraLinkInfo = extraLinkInfoResult.value
                        if (extraLinkInfo != null) {
                            val spanUpdateBatch = SpanUpdateBatch(
                                    requestUrl,
                                    extraLinkInfo,
                                    linkInfoRequest.oldPostLinkableSpans,
                                    iconBitmap
                            )

                            return@flatMap Flowable.just(
                                    ModularResult.value(spanUpdateBatch)
                            )
                        }
                    }

                    return@flatMap fetchFromNetwork(requestUrl, linkInfoRequest, fetcher, iconBitmap)
                }
                .flatMap { spanUpdateBatchResult ->
                    when (spanUpdateBatchResult) {
                        is ModularResult.Error -> {
                            val result = ModularResult.error<SpanUpdateBatch>(spanUpdateBatchResult.error)
                            return@flatMap Flowable.just(result)
                        }
                        is ModularResult.Value -> {
                            val spanUpdateBatch = spanUpdateBatchResult.value

                            return@flatMap fetcher.storeIntoCache(
                                            postUid,
                                            loadableUid,
                                            linkInfoRequest.originalUrl,
                                            spanUpdateBatch.extraLinkInfo
                                    )
                                    .map { spanUpdateBatchResult }
                        }
                        else -> {
                            throw IllegalStateException(
                                    "Unknown result type: ${spanUpdateBatchResult::class.simpleName}"
                            )
                        }
                    }
                }
                .timeout(MAX_LINK_INFO_FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun fetchFromNetwork(
            url: String,
            linkInfoRequest: LinkInfoRequest,
            fetcher: ExternalMediaServiceExtraInfoFetcher,
            iconBitmap: Bitmap
    ): Flowable<ModularResult<SpanUpdateBatch>> {
        return Flowable.create({ emitter ->
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
                        processResponse(
                                linkInfoRequest,
                                url,
                                fetcher,
                                iconBitmap,
                                response,
                                emitter
                        )
                    }
                })
            } catch (error: Throwable) {
                emitter.tryOnError(error)
            }
        }, BackpressureStrategy.BUFFER)
    }

    private fun processResponse(
            linkInfoRequest: LinkInfoRequest,
            url: String,
            fetcher: ExternalMediaServiceExtraInfoFetcher,
            iconBitmap: Bitmap,
            response: Response,
            emitter: FlowableEmitter<ModularResult<SpanUpdateBatch>>
    ) {
        try {
            val extraLinkInfo = fetcher.extractExtraLinkInfo(response)
            val spanUpdateBatch = SpanUpdateBatch(
                    url,
                    extraLinkInfo,
                    linkInfoRequest.oldPostLinkableSpans,
                    iconBitmap
            )

            emitter.onNext(ModularResult.value(spanUpdateBatch))
            emitter.onComplete()
        } catch (error: Throwable) {
            Logger.e(TAG, "Error while processing response", error)
            emitter.onNext(ModularResult.error(error))
            emitter.onComplete()
        }
    }

    private fun createNewRequests(
            postLinkablePostLinkableSpans: List<CommentPostLinkableSpan>
    ): Map<String, LinkInfoRequest> {
        val newSpans = mutableMapOf<String, LinkInfoRequest>()

        postLinkablePostLinkableSpans.forEach { postLinkableSpan ->
            val postLinkable = postLinkableSpan.postLinkable

            if (postLinkable.type == PostLinkable.Type.LINK) {
                val originalUrl = postLinkable.key.toString()
                val fetcher = linkExtraInfoFetchers.firstOrNull { fetcher ->
                    fetcher.linkMatchesToService(originalUrl)
                }

                if (fetcher == null) {
                    // No fetcher found for this link type
                    return@forEach
                }

                if (!fetcher.isEnabled()) {
                    // Fetcher may be disabled by some settings or some other conditions
                    return@forEach
                }

                val requestUrl = fetcher.formatRequestUrl(originalUrl)
                val linkInfoRequest = LinkInfoRequest(
                        originalUrl,
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