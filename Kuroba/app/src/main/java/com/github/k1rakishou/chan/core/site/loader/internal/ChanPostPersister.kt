package com.github.k1rakishou.chan.core.site.loader.internal

import com.github.k1rakishou.chan.core.site.loader.ChanLoaderException
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsUseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.StorePostsInRepositoryUseCase
import com.github.k1rakishou.chan.core.site.parser.ChanReader
import com.github.k1rakishou.chan.core.site.parser.ChanReaderProcessor
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.options.ChanCacheOptions
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.catalog.ChanCatalogSnapshot
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

internal class ChanPostPersister(
  private val appConstants: AppConstants,
  private val parsePostsUseCase: ParsePostsUseCase,
  private val storePostsInRepositoryUseCase: StorePostsInRepositoryUseCase,
  private val chanPostRepository: ChanPostRepository,
  private val chanCatalogSnapshotRepository: ChanCatalogSnapshotRepository
) : AbstractPostLoader() {

  @OptIn(ExperimentalTime::class)
  suspend fun persistPosts(
    url: String,
    chanReaderProcessor: ChanReaderProcessor,
    cacheOptions: ChanCacheOptions,
    chanDescriptor: ChanDescriptor,
    chanReader: ChanReader
  ): ThreadLoadResult {
    return Try {
      BackgroundUtils.ensureBackgroundThread()
      chanPostRepository.awaitUntilInitialized()

      Logger.d(TAG, "persistPosts($url, $chanReaderProcessor, $cacheOptions, " +
        "$chanDescriptor, ${chanReader.javaClass.simpleName})")

      if (chanReaderProcessor.chanDescriptor is ChanDescriptor.CatalogDescriptor) {
        val chanCatalogSnapshot = ChanCatalogSnapshot.fromSortedThreadDescriptorList(
          chanReaderProcessor.chanDescriptor.boardDescriptor,
          chanReaderProcessor.getThreadDescriptors()
        )

        chanCatalogSnapshotRepository.storeChanCatalogSnapshot(chanCatalogSnapshot)
          .peekError { error -> Logger.e(TAG, "storeChanCatalogSnapshot() error", error) }
          .ignore()
      }

      val cleanupDuration = runRollingStickyThreadCleanupRoutineIfNeeded(
        chanDescriptor,
        chanReaderProcessor
      )

      val (parsedPosts, parsingDuration) = measureTimedValue {
        return@measureTimedValue parsePostsUseCase.parseNewPostsPosts(
          chanDescriptor,
          chanReader,
          chanReaderProcessor.getToParse()
        )
      }

      if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
        // We loaded the thread, mark it as not deleted (in case it somehow was marked as deleted)
        chanPostRepository.markThreadAsDeleted(chanDescriptor, false)
      }

      val (storedPostsCount, storeDuration) = measureTimedValue {
        storePostsInRepositoryUseCase.storePosts(
          parsedPosts,
          cacheOptions,
          chanDescriptor.isCatalogDescriptor()
        )
      }

      val logStr = createLogString(
        url,
        storeDuration,
        storedPostsCount,
        parsingDuration,
        parsedPosts.size,
        chanReaderProcessor.getTotalPostsCount(),
        cleanupDuration
      )

      Logger.d(TAG, logStr)
      checkNotNull(chanReaderProcessor.getOp()) { "OP is null" }

      return@Try ThreadLoadResult.Loaded(chanDescriptor)
    }.mapErrorToValue { error -> ThreadLoadResult.Error(ChanLoaderException(error)) }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun runRollingStickyThreadCleanupRoutineIfNeeded(
    chanDescriptor: ChanDescriptor,
    chanReaderProcessor: ChanReaderProcessor
  ): Duration? {
    val threadCap = chanReaderProcessor.getThreadCap()

    val needCleanupThread = (threadCap != null && threadCap > 0)
      && chanDescriptor is ChanDescriptor.ThreadDescriptor

    if (!needCleanupThread) {
      return null
    }

    Logger.d(TAG, "runRollingStickyThreadCleanupRoutineIfNeeded() " +
      "chanDescriptor=$chanDescriptor, threadCap=$threadCap")

    return measureTime {
      val deleteResult = chanPostRepository.cleanupPostsInRollingStickyThread(
        chanDescriptor as ChanDescriptor.ThreadDescriptor,
        threadCap!!
      )

      if (deleteResult is ModularResult.Error) {
        val errorMsg = "cleanupPostsInRollingStickyThread(${chanDescriptor}, $threadCap) error"
        Logger.e(TAG, errorMsg, deleteResult.error)
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun createLogString(
    url: String,
    storeDuration: Duration,
    storedPostsCount: Int,
    parsingDuration: Duration,
    parsedPostsCount: Int,
    totalPostsCount: Int,
    cleanupDuration: Duration?
  ): String {
    val cachedPostsCount = chanPostRepository.getTotalCachedPostsCount()

    return buildString {
      appendLine("ChanReaderRequest.readJson() stats: url = $url.")
      appendLine("Store new posts took $storeDuration (stored ${storedPostsCount} posts).")
      appendLine("Parse posts took = $parsingDuration, (parsed ${parsedPostsCount} out of $totalPostsCount posts).")
      appendLine("Total in-memory cached posts count = ($cachedPostsCount/${appConstants.maxPostsCountInPostsCache}).")

      if (cleanupDuration != null) {
        appendLine("Sticky thread old post clean up routine took ${cleanupDuration}.")
      }
    }
  }

  companion object {
    private const val TAG = "NormalPostLoader"
  }
}