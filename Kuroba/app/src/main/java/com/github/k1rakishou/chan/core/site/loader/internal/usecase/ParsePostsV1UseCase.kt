package com.github.k1rakishou.chan.core.site.loader.internal.usecase

import com.github.k1rakishou.chan.core.helper.ChanLoadProgressEvent
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.site.parser.PostParseWorker
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.processDataCollectionConcurrently
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.repository.ChanPostRepository
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class ParsePostsV1UseCase(
  verboseLogsEnabled: Boolean,
  chanPostRepository: ChanPostRepository,
  filterEngine: FilterEngine,
  postFilterManager: PostFilterManager,
  savedReplyManager: SavedReplyManager,
  boardManager: BoardManager,
  chanLoadProgressNotifier: ChanLoadProgressNotifier
) : AbstractParsePostsUseCase(
  verboseLogsEnabled,
  chanPostRepository,
  filterEngine,
  postFilterManager,
  savedReplyManager,
  boardManager,
  chanLoadProgressNotifier
) {

  @OptIn(ExperimentalTime::class)
  override suspend fun parseNewPostsPosts(
    chanDescriptor: ChanDescriptor,
    postParser: PostParser,
    postBuildersToParse: List<ChanPostBuilder>
  ): ParsingResult {
    BackgroundUtils.ensureBackgroundThread()

    chanPostRepository.awaitUntilInitialized()
    boardManager.awaitUntilInitialized()

    if (postBuildersToParse.isEmpty()) {
      return ParsingResult(emptyList(), Duration.ZERO, 0, Duration.ZERO)
    }

    val internalIds = getInternalIds(chanDescriptor, postBuildersToParse)
    val filters = loadFilters(chanDescriptor)

    chanLoadProgressNotifier.sendProgressEvent(
      ChanLoadProgressEvent.ProcessingFilters(chanDescriptor, filters.size)
    )

    val filterProcessingDuration = measureTime {
      postParsingProcessFiltersStage(postBuildersToParse, filters)
    }

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor, " +
      "postsToParseSize=${postBuildersToParse.size}), " +
      "internalIds=${internalIds.size}, " +
      "filters=${filters.size}")

    chanLoadProgressNotifier.sendProgressEvent(
      ChanLoadProgressEvent.ParsingPosts(chanDescriptor, postBuildersToParse.size)
    )

    val (parsedPosts, parsingDuration) = measureTimedValue {
      return@measureTimedValue processDataCollectionConcurrently(
        dataList = postBuildersToParse,
        batchCount = THREAD_COUNT * 2,
        dispatcher = dispatcher
      ) { postToParse ->
        return@processDataCollectionConcurrently PostParseWorker(
          savedReplyManager = savedReplyManager,
          postBuilder = postToParse,
          postParser = postParser,
          internalIds = internalIds,
          isParsingCatalog = chanDescriptor is ChanDescriptor.CatalogDescriptor
        ).parse()
      }
    }

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor) -> parsedPosts=${parsedPosts.size}")

    return ParsingResult(
      parsedPosts = parsedPosts,
      filterProcessionTime = filterProcessingDuration,
      filtersCount = filters.size,
      parsingTime = parsingDuration
    )
  }

  companion object {
    private const val TAG = "ParsePostsUseCase"
  }

}