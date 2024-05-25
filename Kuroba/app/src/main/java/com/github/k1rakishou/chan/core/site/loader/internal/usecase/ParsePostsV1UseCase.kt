package com.github.k1rakishou.chan.core.site.loader.internal.usecase

import com.github.k1rakishou.chan.core.helper.ChanLoadProgressEvent
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostFilterManagerImpl
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.PostHideManagerImpl
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.site.parser.PostParseWorker
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.parallelForEachIndexed
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.repository.ChanPostRepository
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class ParsePostsV1UseCase(
  chanPostRepository: ChanPostRepository,
  filterEngine: FilterEngine,
  postFilterManager: PostFilterManager,
  postHideManager: PostHideManager,
  savedReplyManager: SavedReplyManager,
  boardManager: BoardManager,
  chanLoadProgressNotifier: ChanLoadProgressNotifier
) : AbstractParsePostsUseCase(
  chanPostRepository,
  filterEngine,
  postFilterManager,
  postHideManager,
  savedReplyManager,
  boardManager,
  chanLoadProgressNotifier
) {

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
    processSavedReplies(postBuildersToParse)

    chanLoadProgressNotifier.sendProgressEvent(
      ChanLoadProgressEvent.ParsingPosts(
        chanDescriptor = chanDescriptor,
        totalPosts = postBuildersToParse.size
      )
    )

    val savedPosts = when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        emptySet<PostDescriptor>()
      }
      is ChanDescriptor.ThreadDescriptor -> {
        savedReplyManager.getThreadSavedReplies(chanDescriptor)
          .map { it.postDescriptor }
          .toSet()
      }
    }

    val hiddenOrRemovedPosts = when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        emptyMap()
      }
      is ChanDescriptor.ThreadDescriptor -> {
        val resultMap = mutableMapOf<PostDescriptor, Int>()
        val hiddenOrRemovedPosts = postHideManager.getHiddenPostsForThread(chanDescriptor)

        for (hiddenOrRemovedPost in hiddenOrRemovedPosts) {
          if (hiddenOrRemovedPost.manuallyRestored) {
            continue
          }

          resultMap[hiddenOrRemovedPost.postDescriptor] = if (hiddenOrRemovedPost.onlyHide) {
            PostParser.HIDDEN_POST
          } else {
            PostParser.REMOVED_POST
          }
        }

        resultMap
      }
    }

    val (parsedPosts, parsingDuration) = measureTimedValue {
      return@measureTimedValue parallelForEachIndexed(
        dataList = postBuildersToParse,
        parallelization = THREAD_COUNT * 2,
        dispatcher = Dispatchers.IO
      ) { index, postToParse ->
        val chanPost = PostParseWorker(
          postBuilder = postToParse,
          postParser = postParser,
          internalIds = internalIds,
          savedPosts = savedPosts,
          hiddenOrRemovedPosts = hiddenOrRemovedPosts,
          isParsingCatalog = chanDescriptor is ChanDescriptor.ICatalogDescriptor
        ).parse()

        chanLoadProgressNotifier.sendProgressEvent(
          ChanLoadProgressEvent.ParsingPosts(
            chanDescriptor = chanDescriptor,
            parsedPosts = index + 1,
            totalPosts = postBuildersToParse.size
          )
        )

        return@parallelForEachIndexed chanPost
      }
    }

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor) -> parsedPosts=${parsedPosts.size}")

    val filters = loadFilters(chanDescriptor)

    chanLoadProgressNotifier.sendProgressEvent(
      ChanLoadProgressEvent.ProcessingFilters(
        chanDescriptor = chanDescriptor,
        filtersCount = filters.size
      )
    )

    val filterProcessingDuration = measureTime {
      processFilters(
        chanDescriptor = chanDescriptor,
        postBuildersToParse = postBuildersToParse,
        filters = filters
      )
    }

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor, " +
      "postsToParseSize=${postBuildersToParse.size}), " +
      "internalIds=${internalIds.size}, " +
      "filters=${filters.size}")

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