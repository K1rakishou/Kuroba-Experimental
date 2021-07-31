package com.github.k1rakishou.chan.core.loader.impl

import android.text.Spanned
import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.PostLoaderData
import com.github.k1rakishou.chan.core.loader.impl.external_media_service.ExternalMediaServiceExtraInfoFetcher
import com.github.k1rakishou.chan.core.loader.impl.post_comment.CommentPostLinkableSpan
import com.github.k1rakishou.chan.core.loader.impl.post_comment.CommentSpanUpdater
import com.github.k1rakishou.chan.core.loader.impl.post_comment.LinkInfoRequest
import com.github.k1rakishou.chan.core.loader.impl.post_comment.SpanUpdateBatch
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.LoaderType
import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.Single
import java.util.concurrent.TimeUnit

internal class PostExtraContentLoader(
  private val scheduler: Scheduler,
  private val chanThreadManager: ChanThreadManager,
  private val linkExtraInfoFetchers: List<ExternalMediaServiceExtraInfoFetcher>
) : OnDemandContentLoader(LoaderType.PostExtraContentLoader) {

  override fun isCached(postLoaderData: PostLoaderData): Single<Boolean> {
    return Single.defer {
      val videoIds = extractVideoIds(postLoaderData)
      if (videoIds.isEmpty()) {
        return@defer Single.just(true)
      }

      return@defer Flowable.fromIterable(linkExtraInfoFetchers)
        .flatMapSingle { fetcher ->
          return@flatMapSingle Flowable.fromIterable(videoIds)
            .flatMapSingle { videoId ->
              return@flatMapSingle fetcher.isCached(videoId)
                .onErrorReturnItem(false)
            }
            .toList()
            .map { results -> results.all { result -> result } }
        }
        .toList()
        .map { results -> results.all { result -> result } }
    }.subscribeOn(scheduler)
  }

  private fun extractVideoIds(postLoaderData: PostLoaderData): List<GenericVideoId> {
    val post = chanThreadManager.getPost(postLoaderData.postDescriptor)
      ?: return emptyList()

    val comment = post.postComment.originalComment()
    if (comment.isEmpty() || comment !is Spanned) {
      return emptyList()
    }

    val postLinkableSpans = parseSpans(comment)
    if (postLinkableSpans.isEmpty()) {
      return emptyList()
    }

    return createNewRequests(postLinkableSpans)
      .map { (_, linkInfoRequest) -> linkInfoRequest.videoId }
  }

  override fun startLoading(postLoaderData: PostLoaderData): Single<LoaderResult> {
    BackgroundUtils.ensureBackgroundThread()

    val post = chanThreadManager.getPost(postLoaderData.postDescriptor)
    if (post == null) {
      return rejected()
    }

    if (chanThreadManager.isContentLoadedForLoader(postLoaderData.postDescriptor, loaderType)) {
      return rejected()
    }

    val comment = post.postComment.originalComment()
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
          .flatMapSingle({ (requestUrl, linkInfoRequest) ->
            return@flatMapSingle fetchExtraLinkInfo(requestUrl, linkInfoRequest)
          }, false, MAX_CONCURRENT_REQUESTS)
          .toList()
          .flatMap { spanUpdateBatchResultList ->
            val spanUpdateBatchList = spanUpdateBatchResultList
              .mapNotNull { it.valueOrNull() }

            if (spanUpdateBatchList.isEmpty()) {
              // All results are errors
              return@flatMap failed()
            }

            return@flatMap updateSpans(post, spanUpdateBatchList)
          }
          .doOnError { error -> Logger.e(TAG, "Internal unhandled error", error) }
          .onErrorResumeNext { failed() }
      }
      .doOnError { error -> Logger.e(TAG, "External unhandled error", error) }
      .onErrorResumeNext { failed() }
  }

  override fun cancelLoading(postLoaderData: PostLoaderData) {
    // I guess there is no real need to cancel these requests since they are lightweight
  }

  private fun updateSpans(
    post: ChanPost,
    spanUpdateBatchList: List<SpanUpdateBatch>
  ): Single<LoaderResult> {
    BackgroundUtils.ensureBackgroundThread()

    val updated = try {
      CommentSpanUpdater.updateSpansForPostComment(
        chanThreadManager = chanThreadManager,
        postDescriptor = post.postDescriptor,
        spanUpdateBatchList = spanUpdateBatchList
      )
    } catch (error: Throwable) {
      Logger.e(TAG, "Unknown error while trying to update spans for post comment", error)
      return failed()
    }

    if (!updated) {
      // For some unknown reason nothing was updated, reject!
      return rejected()
    }

    chanThreadManager.setContentLoadedForLoader(post.postDescriptor, loaderType)
    // Something was updated we need to redraw the post, so return success
    return succeeded(true)
  }

  private fun fetchExtraLinkInfo(
    requestUrl: String,
    linkInfoRequest: LinkInfoRequest
  ): Single<ModularResult<SpanUpdateBatch>> {
    BackgroundUtils.ensureBackgroundThread()

    val fetcher = linkExtraInfoFetchers.firstOrNull { fetcher ->
      fetcher.mediaServiceType == linkInfoRequest.mediaServiceType
    }

    if (fetcher == null) {
      val error = ModularResult.error<SpanUpdateBatch>(
        IllegalStateException("Couldn't find fetcher for " +
          "mediaServiceType ${linkInfoRequest.mediaServiceType}")
      )

      return Single.just(error)
    }

    return fetcher.fetch(requestUrl, linkInfoRequest)
      .timeout(MAX_LINK_INFO_FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .onErrorReturn { error -> ModularResult.Error(error) }
  }

  private fun createNewRequests(
    postLinkablePostLinkableSpans: List<CommentPostLinkableSpan>
  ): Map<String, LinkInfoRequest> {
    BackgroundUtils.ensureBackgroundThread()

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
          fetcher.extractLinkVideoId(originalUrl),
          fetcher.mediaServiceType,
          mutableListOf()
        )

        newSpans.putIfNotContains(requestUrl, linkInfoRequest)
        newSpans[requestUrl]!!.oldPostLinkableSpans.add(postLinkableSpan)
      }
    }

    return newSpans
  }

  private fun parseSpans(comment: Spanned): List<CommentPostLinkableSpan> {
    BackgroundUtils.ensureBackgroundThread()

    val postLinkableSpans = comment.getSpans(0, comment.length, PostLinkable::class.java)
    if (postLinkableSpans.isEmpty()) {
      return emptyList()
    }

    val duplicateChecker = hashSetOf<PostLinkable>()

    return postLinkableSpans.mapNotNull { postLinkable ->
      if (postLinkable.type != PostLinkable.Type.LINK) {
        // Not a link
        return@mapNotNull null
      }

      if (!duplicateChecker.add(postLinkable)) {
        // To avoid bugs when there are duplicate links
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

  companion object {
    private const val TAG = "PostExtraContentLoader"
    private const val MAX_CONCURRENT_REQUESTS = 4
    private const val MAX_LINK_INFO_FETCH_TIMEOUT_SECONDS = 3L
  }
}