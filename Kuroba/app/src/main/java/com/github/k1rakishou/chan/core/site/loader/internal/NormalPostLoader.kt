package com.github.k1rakishou.chan.core.site.loader.internal

import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsUseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ReloadPostsFromDatabaseUseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.StorePostsInRepositoryUseCase
import com.github.k1rakishou.chan.core.site.parser.ChanReader
import com.github.k1rakishou.chan.core.site.parser.ChanReaderProcessor
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.source.cache.ChanCacheOptions
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

internal class NormalPostLoader(
  private val appConstants: AppConstants,
  private val parsePostsUseCase: ParsePostsUseCase,
  private val storePostsInRepositoryUseCase: StorePostsInRepositoryUseCase,
  private val reloadPostsFromDatabaseUseCase: ReloadPostsFromDatabaseUseCase,
  private val chanPostRepository: ChanPostRepository
) : AbstractPostLoader() {

  @OptIn(ExperimentalTime::class)
  suspend fun loadPosts(
    url: String,
    chanReaderProcessor: ChanReaderProcessor,
    cacheOptions: ChanCacheOptions,
    chanDescriptor: ChanDescriptor,
    chanReader: ChanReader
  ): ThreadLoadResult {
    chanPostRepository.awaitUntilInitialized()

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

    val (storedPostNoList, storeDuration) = measureTimedValue {
      storePostsInRepositoryUseCase.storePosts(
        parsedPosts,
        cacheOptions,
        chanDescriptor.isCatalogDescriptor()
      )
    }

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      chanPostRepository.markThreadAsDeleted(chanDescriptor, true)
    }

    val (reloadedPosts, reloadingDuration) = measureTimedValue {
      return@measureTimedValue reloadPostsFromDatabaseUseCase.reloadPostsOrdered(
        chanReaderProcessor,
        chanDescriptor
      )
    }

    val cachedPostsCount = chanPostRepository.getTotalCachedPostsCount()

    val logStr = createLogString(
      url,
      storeDuration,
      storedPostNoList,
      reloadingDuration,
      reloadedPosts,
      parsingDuration,
      parsedPosts,
      cachedPostsCount,
      chanReaderProcessor.getTotalPostsCount(),
      cleanupDuration
    )

    Logger.d(TAG, logStr)
    checkNotNull(chanReaderProcessor.getOp()) { "OP is null" }

    return ThreadLoadResult.Loaded(chanDescriptor)
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun runRollingStickyThreadCleanupRoutineIfNeeded(
    chanDescriptor: ChanDescriptor,
    chanReaderProcessor: ChanReaderProcessor
  ): Duration? {
    val threadCap = chanReaderProcessor.getThreadCap()

    val needCleanupThread = (threadCap != null && threadCap > 0)
      && chanDescriptor is ChanDescriptor.ThreadDescriptor

    return if (needCleanupThread) {
      measureTime {
        val deleteResult = chanPostRepository.cleanupPostsInRollingStickyThread(
          chanDescriptor as ChanDescriptor.ThreadDescriptor,
          threadCap!!
        )

        if (deleteResult is ModularResult.Error) {
          Logger.e(
            TAG,
            "cleanupPostsInRollingStickyThread(${chanDescriptor}, $threadCap) error",
            deleteResult.error
          )
        }
      }
    } else {
      null
    }
  }


  @OptIn(ExperimentalTime::class)
  private fun createLogString(
    url: String,
    storeDuration: Duration,
    storedPostNoList: List<Long>,
    reloadingDuration: Duration,
    reloadedPosts: List<ChanPost>,
    parsingDuration: Duration,
    parsedPosts: List<ChanPost>,
    cachedPostsCount: Int,
    totalPostsCount: Int,
    cleanupDuration: Duration?
  ): String {
    val urlToLog = if (isDevBuild()) {
      url
    } else {
      "<url hidden>"
    }

    return buildString {
      appendLine("ChanReaderRequest.readJson() stats: url = $urlToLog.")
      appendLine("Store new posts took $storeDuration (stored ${storedPostNoList.size} posts).")
      appendLine("Reload posts took $reloadingDuration, (reloaded ${reloadedPosts.size} posts).")
      appendLine("Parse posts took = $parsingDuration, (parsed ${parsedPosts.size} out of $totalPostsCount posts).")
      appendLine("Total in-memory cached posts count = ($cachedPostsCount/${appConstants.maxPostsCountInPostsCache}).")

      if (cleanupDuration != null) {
        appendLine("Sticky thread old post clean up routine took ${cleanupDuration}")
      }
    }
  }

  companion object {
    private const val TAG = "NormalPostLoader"
  }
}