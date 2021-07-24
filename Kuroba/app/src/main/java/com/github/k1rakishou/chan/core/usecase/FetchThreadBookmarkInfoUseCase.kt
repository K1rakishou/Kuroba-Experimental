package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.parser.ChanReader
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.processDataCollectionConcurrently
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoPostObject
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException
import kotlin.math.max

@Suppress("FoldInitializerAndIfToElvis")
class FetchThreadBookmarkInfoUseCase(
  private val isDevFlavor: Boolean,
  private val verboseLogsEnabled: Boolean,
  private val appScope: CoroutineScope,
  private val proxiedOkHttpClient: Lazy<ProxiedOkHttpClient>,
  private val siteManager: SiteManager,
  private val bookmarksManager: BookmarksManager,
  private val appConstants: AppConstants
) : ISuspendUseCase<List<ChanDescriptor.ThreadDescriptor>, ModularResult<List<ThreadBookmarkFetchResult>>> {

  override suspend fun execute(parameter: List<ChanDescriptor.ThreadDescriptor>): ModularResult<List<ThreadBookmarkFetchResult>> {
    Logger.d(TAG, "FetchThreadBookmarkInfoUseCase.execute(${parameter.size})")
    return Try { fetchThreadBookmarkInfoBatched(parameter) }
  }

  private suspend fun fetchThreadBookmarkInfoBatched(
    watchingBookmarkDescriptors: List<ChanDescriptor.ThreadDescriptor>
  ): List<ThreadBookmarkFetchResult> {
    val batchSize = (appConstants.processorsCount * BATCH_PER_CORE)
      .coerceAtLeast(MIN_BATCHES_COUNT)

    return processDataCollectionConcurrently(watchingBookmarkDescriptors, batchSize, Dispatchers.IO) { threadDescriptor ->
      val site = siteManager.bySiteDescriptor(threadDescriptor.siteDescriptor())
      if (site == null) {
        Logger.e(TAG, "Site with descriptor ${threadDescriptor.siteDescriptor()} " +
          "not found in siteRepository!")
        return@processDataCollectionConcurrently null
      }

      val threadJsonEndpoint = site.endpoints().thread(threadDescriptor)

      return@processDataCollectionConcurrently fetchThreadBookmarkInfo(
        threadDescriptor,
        threadJsonEndpoint,
        site.chanReader()
      )
    }
  }

  private suspend fun fetchThreadBookmarkInfo(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    threadJsonEndpoint: HttpUrl,
    chanReader: ChanReader
  ): ThreadBookmarkFetchResult {
    if (verboseLogsEnabled) {
      Logger.d(TAG, "fetchThreadBookmarkInfo() threadJsonEndpoint = $threadJsonEndpoint")
    }

    val requestBuilder = Request.Builder()
      .url(threadJsonEndpoint)
      .get()

    siteManager.bySiteDescriptor(threadDescriptor.siteDescriptor())?.let { site ->
      site.requestModifier().modifyCatalogOrThreadGetRequest(
        site = site,
        chanDescriptor = threadDescriptor,
        requestBuilder = requestBuilder
      )
    }

    val request = requestBuilder.build()

    val response = try {
      proxiedOkHttpClient.get().okHttpClient().suspendCall(request)
    } catch (error: IOException) {
      return ThreadBookmarkFetchResult.Error(error, threadDescriptor)
    }

    if (!response.isSuccessful) {
      if (response.code == NOT_FOUND_STATUS) {
        return ThreadBookmarkFetchResult.NotFoundOnServer(threadDescriptor)
      }

      return ThreadBookmarkFetchResult.BadStatusCode(response.code, threadDescriptor)
    }

    val body = response.body
      ?: return ThreadBookmarkFetchResult.Error(EmptyBodyResponseException(), threadDescriptor)

    return body.byteStream().use { inputStream ->
      val postsCount = bookmarksManager.mapBookmark(threadDescriptor) { threadBookmarkView ->
        threadBookmarkView.postsCount()
      }

      if (postsCount == null) {
        return@use ThreadBookmarkFetchResult.AlreadyDeleted(threadDescriptor)
      }

      val threadBookmarkInfoObject = chanReader.readThreadBookmarkInfoObject(
        threadDescriptor,
        max(postsCount, ChanReader.DEFAULT_POST_LIST_CAPACITY),
        request.url.toString(),
        inputStream
      ).safeUnwrap { error -> return@use ThreadBookmarkFetchResult.Error(error, threadDescriptor) }

      if (isDevFlavor && !threadDescriptor.siteDescriptor().isLainchan()) {
        ensureCorrectPostOrder(threadBookmarkInfoObject.simplePostObjects)
      }

      return@use ThreadBookmarkFetchResult.Success(threadBookmarkInfoObject, threadDescriptor)
    }
  }

  private fun ensureCorrectPostOrder(simplePostObjects: List<ThreadBookmarkInfoPostObject>) {
    if (simplePostObjects.isEmpty()) {
      return
    }

    var prevPostNo = 0L

    simplePostObjects.forEach { threadBookmarkInfoPostObject ->
      val currentPostNo = threadBookmarkInfoPostObject.postNo()

      check(prevPostNo <= currentPostNo) {
        "Incorrect post ordering detected: (prevPostNo=$prevPostNo, currentPostNo=${currentPostNo}"
      }

      prevPostNo = currentPostNo
    }
  }

  companion object {
    private const val TAG = "FetchThreadBookmarkInfoUseCase"
    private const val BATCH_PER_CORE = 4
    private const val MIN_BATCHES_COUNT = 8
    private const val NOT_FOUND_STATUS = 404
  }
}

sealed class ThreadBookmarkFetchResult(val threadDescriptor: ChanDescriptor.ThreadDescriptor) {
  class Error(
    val error: Throwable,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ) : ThreadBookmarkFetchResult(threadDescriptor)

  class AlreadyDeleted(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ) : ThreadBookmarkFetchResult(threadDescriptor)

  class NotFoundOnServer(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ) : ThreadBookmarkFetchResult(threadDescriptor)

  class BadStatusCode(
    val statusCode: Int,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ) : ThreadBookmarkFetchResult(threadDescriptor)

  class Success(
    val threadBookmarkInfoObject: ThreadBookmarkInfoObject,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ) : ThreadBookmarkFetchResult(threadDescriptor)
}