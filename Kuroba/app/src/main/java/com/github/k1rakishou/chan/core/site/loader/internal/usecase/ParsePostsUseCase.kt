package com.github.k1rakishou.chan.core.site.loader.internal.usecase

import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.lib.KurobaNativeLib
import com.github.k1rakishou.chan.core.lib.data.post_parsing.PostParserContext
import com.github.k1rakishou.chan.core.lib.data.post_parsing.PostToParse
import com.github.k1rakishou.chan.core.lib.data.post_parsing.ThreadToParse
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.site.parser.PostParseWorker
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.mapArray
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.repository.ChanPostRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ParsePostsUseCase(
  private val verboseLogsEnabled: Boolean,
  private val chanPostRepository: ChanPostRepository,
  private val filterEngine: FilterEngine,
  private val postFilterManager: PostFilterManager,
  private val savedReplyManager: SavedReplyManager,
  private val boardManager: BoardManager
) {

  suspend fun parseNewPostsPosts(
    chanDescriptor: ChanDescriptor,
    postParser: PostParser,
    postBuildersToParse: List<ChanPostBuilder>
  ): List<ChanPost> {
    BackgroundUtils.ensureBackgroundThread()

    chanPostRepository.awaitUntilInitialized()
    boardManager.awaitUntilInitialized()

    if (postBuildersToParse.isEmpty()) {
      return emptyList()
    }

    val internalIds = getInternalIds(chanDescriptor, postBuildersToParse)
    val boardDescriptors = getBoardDescriptors(chanDescriptor)
    val filters = loadFilters(chanDescriptor)

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor, " +
      "postsToParseSize=${postBuildersToParse.size}), " +
      "internalIds=${internalIds.size}, " +
      "boardDescriptors=${boardDescriptors.size}, " +
      "filters=${filters.size}")

//    val parsedPosts = parsePostsWithPostParserV1(postBuildersToParse, filters, postParser, internalIds, boardDescriptors, chanDescriptor)
    val parsedPosts = parsePostsWithPostParserV2(postBuildersToParse, filters, postParser, internalIds, boardDescriptors, chanDescriptor)

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor) -> parsedPosts=${parsedPosts.size}")
    return parsedPosts
  }

  private fun parsePostsWithPostParserV2(
    postBuildersToParse: List<ChanPostBuilder>,
    filters: List<ChanFilter>,
    postParser: PostParser,
    internalIds: Set<Long>,
    boardDescriptors: Set<BoardDescriptor>,
    chanDescriptor: ChanDescriptor
  ): List<ChanPost> {
    if (postBuildersToParse.isEmpty()) {
      return emptyList()
    }

    val threadId = postBuildersToParse.first().getOpId()

    val postsToParseArray = postBuildersToParse
      .mapArray { postBuilderToParse ->
        PostToParse(postBuilderToParse.id, postBuilderToParse.postCommentBuilder.getUnparsedComment())
      }

    val postThreadParsed = KurobaNativeLib.parseThreadPosts(
      PostParserContext(threadId, longArrayOf(), internalIds.toLongArray()),
      ThreadToParse(postsToParseArray)
    )

    println("TTTAAA parsed comments count=${postThreadParsed.postCommentsParsedList.size}")

    for (postCommentParsed in postThreadParsed.postCommentsParsedList) {
      println("TTTAAA ${postCommentParsed}")
    }

    return emptyList()
  }

  private suspend fun parsePostsWithPostParserV1(
    postBuildersToParse: List<ChanPostBuilder>,
    filters: List<ChanFilter>,
    postParser: PostParser,
    internalIds: Set<Long>,
    boardDescriptors: Set<BoardDescriptor>,
    chanDescriptor: ChanDescriptor
  ): List<ChanPost> {
    return supervisorScope {
      return@supervisorScope postBuildersToParse
        .chunked(THREAD_COUNT * 2)
        .flatMap { postToParseChunk ->
          val deferredList = postToParseChunk.map { postToParse ->
            return@map async(dispatcher) {
              return@async PostParseWorker(
                filterEngine = filterEngine,
                postFilterManager = postFilterManager,
                savedReplyManager = savedReplyManager,
                filters = filters,
                postBuilder = postToParse,
                postParser = postParser,
                internalIds = internalIds,
                boardDescriptors = boardDescriptors,
                isParsingCatalog = chanDescriptor is ChanDescriptor.CatalogDescriptor
              ).parse()
            }
          }

          return@flatMap deferredList.awaitAll().filterNotNull()
        }
    }
  }

  private fun getBoardDescriptors(chanDescriptor: ChanDescriptor): Set<BoardDescriptor> {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return emptySet()
    }

    val boardDescriptors = hashSetWithCap<BoardDescriptor>(256)
    boardDescriptors.addAll(boardManager.getAllBoardDescriptorsForSite(chanDescriptor.siteDescriptor()))
    return boardDescriptors
  }

  private fun getInternalIds(
    chanDescriptor: ChanDescriptor,
    postBuildersToParse: List<ChanPostBuilder>
  ): Set<Long> {
    val postsToParseNoSet = postBuildersToParse.map { postBuilder -> postBuilder.id }.toSet()

    if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
      return postsToParseNoSet
    }

    return when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        postsToParseNoSet + chanPostRepository.getCachedThreadPostsNos(chanDescriptor)
      }
      is ChanDescriptor.CatalogDescriptor -> {
        postsToParseNoSet
      }
    }
  }

  private fun loadFilters(chanDescriptor: ChanDescriptor): List<ChanFilter> {
    BackgroundUtils.ensureBackgroundThread()

    val board = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
      ?: return emptyList()

    return filterEngine.enabledFilters
      .filter { filter -> filterEngine.matchesBoard(filter, board) }
  }

  companion object {
    private const val TAG = "ParsePostsUseCase"
    private const val threadFactoryName = "post_parser_%d"

    private val THREAD_COUNT = Runtime.getRuntime().availableProcessors()
    private val threadIndex = AtomicInteger(0)
    private val dispatcher: CoroutineDispatcher

    init {
      Logger.d(TAG, "Thread count: $THREAD_COUNT")

      val executor = Executors.newFixedThreadPool(THREAD_COUNT) { runnable ->
        val threadName = String.format(
          Locale.ENGLISH,
          threadFactoryName,
          threadIndex.getAndIncrement()
        )

        return@newFixedThreadPool Thread(runnable, threadName)
      }

      dispatcher = executor.asCoroutineDispatcher()
    }
  }

}