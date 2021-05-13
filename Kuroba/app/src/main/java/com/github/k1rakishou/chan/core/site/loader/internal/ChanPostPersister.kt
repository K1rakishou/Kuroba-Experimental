package com.github.k1rakishou.chan.core.site.loader.internal

import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.loader.ChanLoaderException
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsV1UseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsV2UseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.StorePostsInRepositoryUseCase
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.processor.ChanReaderProcessor
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.catalog.ChanCatalogSnapshot
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.prefs.BooleanSetting
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

internal class ChanPostPersister(
  private val siteManager: SiteManager,
  private val parsePostsV1UseCase: ParsePostsV1UseCase,
  private val parsePostsV2UseCase: ParsePostsV2UseCase,
  private val storePostsInRepositoryUseCase: StorePostsInRepositoryUseCase,
  private val chanPostRepository: ChanPostRepository,
  private val chanCatalogSnapshotRepository: ChanCatalogSnapshotRepository
) : AbstractPostLoader() {

  @OptIn(ExperimentalTime::class)
  suspend fun persistPosts(
    chanReaderProcessor: ChanReaderProcessor,
    cacheOptions: ChanCacheOptions,
    cacheUpdateOptions: ChanCacheUpdateOptions,
    chanDescriptor: ChanDescriptor,
    postParser: PostParser
  ): ThreadResultWithTimeInfo {
    return Try {
      BackgroundUtils.ensureBackgroundThread()
      chanPostRepository.awaitUntilInitialized()

      Logger.d(TAG, "persistPosts($chanDescriptor, $chanReaderProcessor, $cacheOptions, " +
        "${postParser.javaClass.simpleName})")

      if (chanReaderProcessor.chanDescriptor is ChanDescriptor.CatalogDescriptor) {
        val chanCatalogSnapshot = ChanCatalogSnapshot.fromSortedThreadDescriptorList(
          boardDescriptor = chanReaderProcessor.chanDescriptor.boardDescriptor,
          threadDescriptors = chanReaderProcessor.getThreadDescriptors()
        )

        chanCatalogSnapshotRepository.storeChanCatalogSnapshot(chanCatalogSnapshot)
          .peekError { error -> Logger.e(TAG, "storeChanCatalogSnapshot() error", error) }
          .ignore()
      }

      val usePostParserV2 = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
        ?.getSettingBySettingId<BooleanSetting>(SiteSetting.SiteSettingId.UsePostParserV2)
        ?.get()
        ?: false

      val parsingResult = kotlin.run {
        if (usePostParserV2) {
          return@run parsePostsV2UseCase.parseNewPostsPosts(
            chanDescriptor = chanDescriptor,
            postParser = postParser,
            postBuildersToParse = chanReaderProcessor.getToParse()
          )
        } else {
          return@run parsePostsV1UseCase.parseNewPostsPosts(
            chanDescriptor = chanDescriptor,
            postParser = postParser,
            postBuildersToParse = chanReaderProcessor.getToParse()
          )
        }
      }

      if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
        // We loaded the thread, mark it as not deleted (in case it somehow was marked as deleted)
        chanPostRepository.markThreadAsDeleted(chanDescriptor, false)
      }

      val (storedPostsCount, storeDuration) = measureTimedValue {
        storePostsInRepositoryUseCase.storePosts(
          chanDescriptor = chanDescriptor,
          parsedPosts = parsingResult.parsedPosts,
          cacheOptions = cacheOptions,
          cacheUpdateOptions = cacheUpdateOptions
        )
      }

      val loadTimeInfo = LoadTimeInfo(
        usePostParserV2 = usePostParserV2,
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
        threadLoadResult = ThreadLoadResult.Error(ChanLoaderException(error)),
        timeInfo = null
      )
    }
  }

  data class ThreadResultWithTimeInfo(
    val threadLoadResult: ThreadLoadResult,
    val timeInfo: LoadTimeInfo?
  )

  class LoadTimeInfo @OptIn(ExperimentalTime::class) constructor(
    val usePostParserV2: Boolean,
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