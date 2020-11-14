package com.github.k1rakishou.chan.core.site.loader.internal

import com.github.k1rakishou.chan.core.site.loader.ChanLoaderException
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsUseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.StorePostsInRepositoryUseCase
import com.github.k1rakishou.chan.core.site.parser.ChanReader
import com.github.k1rakishou.chan.core.site.parser.ChanReaderProcessor
import com.github.k1rakishou.chan.utils.BackgroundUtils
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
  ): ThreadResultWithTimeInfo {
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

      val cleanupDuration = runRollingStickyThreadCleanupRoutineIfNeeded(
        chanDescriptor,
        chanReaderProcessor
      )

      checkNotNull(chanReaderProcessor.getOp()) { "OP is null" }

      val loadTimeInfo = LoadTimeInfo(
        url = url,
        storeDuration = storeDuration,
        storedPostsCount = storedPostsCount,
        parsingDuration = parsingDuration,
        parsedPostsCount = parsedPosts.size,
        postsInChanReaderProcessor = chanReaderProcessor.getTotalPostsCount(),
        cleanupDuration = cleanupDuration
      )

      return@Try ThreadResultWithTimeInfo(
        threadLoadResult = ThreadLoadResult.Loaded(chanDescriptor),
        timeInfo = loadTimeInfo
      )
    }.mapErrorToValue { error ->
      return@mapErrorToValue ThreadResultWithTimeInfo(
        threadLoadResult = ThreadLoadResult.Error(ChanLoaderException(error)),
        timeInfo = null
      )
    }
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

  data class ThreadResultWithTimeInfo(
    val threadLoadResult: ThreadLoadResult,
    val timeInfo: LoadTimeInfo?
  )

  class LoadTimeInfo @OptIn(ExperimentalTime::class) constructor(
    val url: String,
    val storeDuration: Duration,
    val storedPostsCount: Int,
    val parsingDuration: Duration,
    val parsedPostsCount: Int,
    val postsInChanReaderProcessor: Int,
    val cleanupDuration: Duration?
  )

  companion object {
    private const val TAG = "NormalPostLoader"
  }
}