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
import com.github.k1rakishou.common.processDataCollectionConcurrently
import com.github.k1rakishou.common.putIfNotContainsLazy
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.LoaderType
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

class PostExtraContentLoader(
  private val chanThreadManager: ChanThreadManager,
  private val linkExtraInfoFetchers: List<ExternalMediaServiceExtraInfoFetcher>
) : OnDemandContentLoader(LoaderType.PostExtraContentLoader) {

  override suspend fun isCached(postLoaderData: PostLoaderData): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val videoIds = extractVideoIds(postLoaderData)
    if (videoIds.isEmpty()) {
      return true
    }

    val results = processDataCollectionConcurrently(linkExtraInfoFetchers) { fetcher ->
      return@processDataCollectionConcurrently videoIds.all { videoId ->
        try {
          return@all fetcher.isCached(videoId)
        } catch (error: Throwable) {
          return@all false
        }
      }
    }

    return results.all { success -> success }
  }

  override suspend fun startLoading(postLoaderData: PostLoaderData): LoaderResult {
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

    val newSpans = createNewRequests(postLinkableSpans)
    if (newSpans.isEmpty()) {
      return rejected()
    }

    val spanUpdateBatchResultList = processDataCollectionConcurrently(newSpans.entries) { (requestUrl, linkInfoRequest) ->
      fetchExtraLinkInfo(requestUrl, linkInfoRequest)
    }

    val spanUpdateBatchList = spanUpdateBatchResultList.mapNotNull { it.valueOrNull() }
    if (spanUpdateBatchList.isEmpty()) {
      // All results are errors
      return failed()
    }

    return updateSpans(post, spanUpdateBatchList)
  }

  override fun cancelLoading(postLoaderData: PostLoaderData) {
    // I guess there is no real need to cancel these requests since they are lightweight
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

  private suspend fun updateSpans(
    post: ChanPost,
    spanUpdateBatchList: List<SpanUpdateBatch>
  ): LoaderResult {
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
    return succeeded(needUpdateView = true)
  }

  private suspend fun fetchExtraLinkInfo(
    requestUrl: String,
    linkInfoRequest: LinkInfoRequest
  ): ModularResult<SpanUpdateBatch> {
    BackgroundUtils.ensureBackgroundThread()

    val fetcher = linkExtraInfoFetchers.firstOrNull { fetcher ->
      fetcher.mediaServiceType == linkInfoRequest.mediaServiceType
    }

    if (fetcher == null) {
      val error = IllegalStateException("Couldn't find fetcher for mediaServiceType ${linkInfoRequest.mediaServiceType}")
      return ModularResult.error(error)
    }

    return ModularResult.Try {
      withTimeout(TimeUnit.MINUTES.toMillis(MAX_LINK_INFO_FETCH_TIMEOUT_SECONDS)) {
        Logger.d(TAG, "fetchExtraLinkInfo('$requestUrl') fetcher=${fetcher.javaClass.simpleName}")
        fetcher.fetch(requestUrl, linkInfoRequest).unwrap()
      }
    }
  }

  private fun createNewRequests(
    postLinkablePostLinkableSpans: List<CommentPostLinkableSpan>
  ): Map<String, LinkInfoRequest> {
    BackgroundUtils.ensureBackgroundThread()

    val newSpans = mutableMapOf<String, LinkInfoRequest>()

    postLinkablePostLinkableSpans.forEach { postLinkableSpan ->
      val postLinkable = postLinkableSpan.postLinkable

      if (postLinkable.type != PostLinkable.Type.LINK) {
        return@forEach
      }

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

      newSpans.putIfNotContainsLazy(
        key = requestUrl,
        valueFunc = {
          return@putIfNotContainsLazy LinkInfoRequest(
            fetcher.extractLinkVideoId(originalUrl),
            fetcher.mediaServiceType,
            mutableListOf()
          )
        }
      )

      newSpans[requestUrl]!!.oldPostLinkableSpans.add(postLinkableSpan)
    }

    return newSpans
  }

  private fun parseSpans(comment: Spanned): List<CommentPostLinkableSpan> {
    BackgroundUtils.ensureBackgroundThread()

    val postLinkableSpans = comment.getSpans(0, comment.length, PostLinkable::class.java)
    if (postLinkableSpans.isEmpty()) {
      return emptyList()
    }

    val duplicateChecker = hashSetOf<PostLinkableWithPosition>()

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

      val postLinkableWithPosition = PostLinkableWithPosition(postLinkable, start, end)
      if (!duplicateChecker.add(postLinkableWithPosition)) {
        // To avoid bugs when there are duplicate links
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

  private data class PostLinkableWithPosition(
    val postLinkable: PostLinkable,
    val start: Int,
    val end: Int
  )

  companion object {
    private const val TAG = "PostExtraContentLoader"
    private const val MAX_LINK_INFO_FETCH_TIMEOUT_SECONDS = 3L
  }
}