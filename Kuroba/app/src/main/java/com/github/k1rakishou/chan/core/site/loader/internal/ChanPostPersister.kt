package com.github.k1rakishou.chan.core.site.loader.internal

import com.github.k1rakishou.chan.core.helper.ChanLoadProgressEvent
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.site.loader.ChanLoaderException
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsV1UseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.StorePostsInRepositoryUseCase
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.processor.ChanReaderProcessor
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.PostsFromServerData
import com.github.k1rakishou.model.data.catalog.ChanCatalogSnapshot
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

internal class ChanPostPersister(
  private val boardManager: BoardManager,
  private val parsePostsV1UseCase: ParsePostsV1UseCase,
  private val storePostsInRepositoryUseCase: StorePostsInRepositoryUseCase,
  private val chanPostRepository: ChanPostRepository,
  private val chanCatalogSnapshotRepository: ChanCatalogSnapshotRepository,
  private val chanLoadProgressNotifier: ChanLoadProgressNotifier,
  private val chanCatalogSnapshotCache: ChanCatalogSnapshotCache
) : AbstractPostLoader() {

  @OptIn(ExperimentalTime::class)
  suspend fun persistPosts(
    chanReaderProcessor: ChanReaderProcessor,
    cacheOptions: ChanCacheOptions,
    cacheUpdateOptions: ChanCacheUpdateOptions,
    chanDescriptor: ChanDescriptor,
    postParser: PostParser,
  ): ThreadResultWithTimeInfo {
    return Try {
      BackgroundUtils.ensureBackgroundThread()
      chanPostRepository.awaitUntilInitialized()

      Logger.d(TAG, "persistPosts($chanDescriptor, $chanReaderProcessor, $cacheOptions, " +
        "${postParser.javaClass.simpleName})")

      if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
        val isUnlimitedCatalog = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor)
          ?.isUnlimitedCatalog
          ?: false

        if (isUnlimitedCatalog && chanReaderProcessor.endOfUnlimitedCatalogReached) {
          chanCatalogSnapshotCache.get(chanDescriptor.boardDescriptor)?.onEndOfUnlimitedCatalogReached()
        } else {
          val chanCatalogSnapshot = ChanCatalogSnapshot.fromSortedThreadDescriptorList(
            boardDescriptor = chanDescriptor.boardDescriptor,
            threadDescriptors = chanReaderProcessor.getThreadDescriptors(),
            isUnlimitedCatalog = isUnlimitedCatalog
          )

          chanCatalogSnapshotRepository.storeChanCatalogSnapshot(chanCatalogSnapshot)
            .peekError { error -> Logger.e(TAG, "storeChanCatalogSnapshot() error", error) }
            .ignore()
        }
      }

      val parsingResult = parsePostsV1UseCase.parseNewPostsPosts(
        chanDescriptor = chanDescriptor,
        postParser = postParser,
        postBuildersToParse = chanReaderProcessor.getToParse()
      )

      chanLoadProgressNotifier.sendProgressEvent(
        ChanLoadProgressEvent.PersistingPosts(chanDescriptor, parsingResult.parsedPosts.size)
      )

      val (storedPostsCount, storeDuration) = measureTimedValue {
        storePostsInRepositoryUseCase.storePosts(
          chanDescriptor = chanDescriptor,
          parsedPosts = parsingResult.parsedPosts,
          cacheOptions = cacheOptions,
          cacheUpdateOptions = cacheUpdateOptions,
          postsFromServerData = PostsFromServerData(
            chanReaderProcessor.allPostDescriptorsFromServer,
            chanReaderProcessor.isIncrementalUpdate
          )
        )
      }

      val loadTimeInfo = LoadTimeInfo(
        storeDuration = storeDuration,
        storedPostsCount = storedPostsCount,
        filterProcessingDuration = parsingResult.filterProcessionTime,
        filtersCount = parsingResult.filtersCount,
        parsingDuration = parsingResult.parsingTime,
        parsedPostsCount = parsingResult.parsedPosts.size,
        postsInChanReaderProcessor = chanReaderProcessor.getTotalPostsCount()
      )

      return@Try ThreadResultWithTimeInfo(
        threadLoadResult = ThreadLoadResult.Loaded(chanDescriptor),
        timeInfo = loadTimeInfo
      )
    }.mapErrorToValue { error ->
      return@mapErrorToValue ThreadResultWithTimeInfo(
        threadLoadResult = ThreadLoadResult.Error(chanDescriptor, ChanLoaderException(error)),
        timeInfo = null
      )
    }
  }

  data class ThreadResultWithTimeInfo(
    val threadLoadResult: ThreadLoadResult,
    val timeInfo: LoadTimeInfo?
  )

  class LoadTimeInfo @OptIn(ExperimentalTime::class) constructor(
    val storeDuration: Duration,
    val storedPostsCount: Int,
    val filterProcessingDuration: Duration,
    val filtersCount: Int,
    val parsingDuration: Duration,
    val parsedPostsCount: Int,
    val postsInChanReaderProcessor: Int
  )

  companion object {
    private const val TAG = "NormalPostLoader"
  }
}