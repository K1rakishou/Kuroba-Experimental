package com.github.k1rakishou.chan.features.thread_downloading

import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.processDataCollectionConcurrentlyIndexed
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.options.ChanReadOptions
import com.github.k1rakishou.model.repository.ChanPostRepository

class ThreadDownloadingDelegate(
  private val appConstants: AppConstants,
  private val siteManager: SiteManager,
  private val threadDownloadManager: ThreadDownloadManager,
  private val chanThreadManager: ChanThreadManager,
  private val chanPostRepository: ChanPostRepository
) {

  suspend fun doWork(): ModularResult<Unit> {
    return ModularResult.Try { doWorkInternal() }
  }

  private suspend fun doWorkInternal() {
    siteManager.awaitUntilInitialized()
    threadDownloadManager.awaitUntilInitialized()
    chanPostRepository.awaitUntilInitialized()

    if (!threadDownloadManager.hasActiveThreads()) {
      Logger.d(TAG, "doWorkInternal() no active threads left, exiting")
      return
    }

    val threadDescriptors = threadDownloadManager.getAllActiveThreads()
    val batchCount = (appConstants.processorsCount - 1).coerceAtLeast(2)

    processDataCollectionConcurrentlyIndexed(
      dataList = threadDescriptors,
      batchCount = batchCount
    ) { index, threadDescriptor ->
      processThread(threadDescriptor, index, threadDescriptors.size)
    }

    Logger.d(TAG, "doWorkInternal() success")
  }

  private suspend fun processThread(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    index: Int,
    total: Int
  ) {
    // Preload thread from the database if we have anything there first.
    // We want to do this so that we don't have to reparse full threads over and over again.
    // Plus some sites support incremental thread updating so we might as well use it here.
    // If we fail to preload for some reason then just do everything from scratch.
    chanPostRepository.preloadForThread(threadDescriptor)
      .peekError { error -> Logger.e(TAG, "chanPostRepository.preloadForThread($threadDescriptor) error", error) }
      .ignore()

    val threadLoadResult = chanThreadManager.loadThreadOrCatalog(
      threadDescriptor,
      ChanCacheUpdateOptions.UpdateCache,
      ChanLoadOptions.retainAll(),
      ChanCacheOptions.cacheEverywhere(),
      ChanReadOptions.default()
    )

    if (threadLoadResult is ThreadLoadResult.Error) {
      Logger.e(TAG, "processThread($index/$total) loadThreadOrCatalog($threadDescriptor) " +
          "error: ${threadLoadResult.exception.errorMessage}")

      return
    }

    val chanThread = chanThreadManager.getChanThread(threadDescriptor)
    if (chanThread == null) {
      Logger.d(TAG, "processThread($index/$total) getChanThread($threadDescriptor) returned null")
      return
    }

    val originalPost = chanThread.getOriginalPost()

    if (originalPost.archived || originalPost.closed || originalPost.deleted) {
      threadDownloadManager.completeDownloading(threadDescriptor)
    }

    val status = "archived: ${originalPost.archived}, " +
      "closed: ${originalPost.closed}, " +
      "deleted: ${originalPost.deleted}, " +
      "postsCount: ${chanThread.postsCount}"

    Logger.d(TAG, "processThread($index/$total) loadThreadOrCatalog($threadDescriptor) success, status: $status")
  }

  companion object {
    private const val TAG = "ThreadDownloadingDelegate"
  }

}