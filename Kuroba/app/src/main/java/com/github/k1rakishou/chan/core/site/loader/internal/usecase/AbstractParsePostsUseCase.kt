package com.github.k1rakishou.chan.core.site.loader.internal.usecase

import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.site.parser.PostParseWorker
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.filter.FilterAction
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.data.post.PostFilter
import com.github.k1rakishou.model.repository.ChanPostRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

abstract class AbstractParsePostsUseCase(
  protected val verboseLogsEnabled: Boolean,
  protected val chanPostRepository: ChanPostRepository,
  protected val filterEngine: FilterEngine,
  protected val postFilterManager: PostFilterManager,
  protected val savedReplyManager: SavedReplyManager,
  protected val boardManager: BoardManager
) {

  abstract suspend fun parseNewPostsPosts(
    chanDescriptor: ChanDescriptor,
    postParser: PostParser,
    postBuildersToParse: List<ChanPostBuilder>
  ): List<ChanPost>

  protected suspend fun postParsingProcessStage(
    postBuildersToParse: List<ChanPostBuilder>,
    filters: List<ChanFilter>
  ) {
    supervisorScope {
      return@supervisorScope postBuildersToParse
        .chunked(THREAD_COUNT * 2)
        .forEach { postToParseChunk ->
          val deferredList = postToParseChunk.map { postToParse ->
            return@map async(dispatcher) {
              // needed for "Apply to own posts" to work correctly
              postToParse.isSavedReply(savedReplyManager.isSaved(postToParse.postDescriptor))

              // Process the filters before finish, because parsing the html is dependent on filter matches
              processPostFilter(filters, postToParse)
            }
          }

          deferredList.awaitAll()
        }
    }
  }


  protected suspend fun parsePostsWithPostParser(
    postBuildersToParse: List<ChanPostBuilder>,
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
                savedReplyManager = savedReplyManager,
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

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun processPostFilter(filters: List<ChanFilter>, post: ChanPostBuilder) {
    val postDescriptor = post.postDescriptor

    if (postFilterManager.contains(postDescriptor)) {
      // Fast path. We have already processed this post so we don't want to do that again. This
      // should make filter processing way faster after the initial processing but it's kinda
      // dangerous in case a post is updated on the server which "shouldn't" happen normally. It
      // can happen when a poster is getting banned with a message and we can't handle that for now,
      // because 4chan as well as other sites do not provide "last_modified" parameter for posts.
      // There is a workaround for that - to compare post that we got from the server with the one
      // in the database and if they differ update the "last_modified" but it will make everything
      // slower. Maybe it's doable by calculating a post hash and store it in the memory cache and
      // in the database too.
      return
    }

    for (filter in filters) {
      if (filter.isWatchFilter()) {
        // Do not auto create watch filters, this may end up pretty bad
        continue
      }

      if (filterEngine.matches(filter, post)) {
        postFilterManager.insert(postDescriptor, createPostFilter(filter))
        return
      }

      postFilterManager.remove(postDescriptor)
    }
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun createPostFilter(filter: ChanFilter): PostFilter {
    return when (FilterAction.forId(filter.action)) {
      FilterAction.COLOR -> {
        PostFilter(
          enabled = filter.enabled,
          filterHighlightedColor = filter.color,
          filterReplies = filter.applyToReplies,
          filterOnlyOP = filter.onlyOnOP,
          filterSaved = filter.applyToSaved
        )
      }
      FilterAction.HIDE -> {
        PostFilter(
          enabled = filter.enabled,
          filterStub = true,
          filterReplies = filter.applyToReplies,
          filterOnlyOP = filter.onlyOnOP
        )
      }
      FilterAction.REMOVE -> {
        PostFilter(
          enabled = filter.enabled,
          filterRemove = true,
          filterReplies = filter.applyToReplies,
          filterOnlyOP = filter.onlyOnOP
        )
      }
      FilterAction.WATCH -> {
        throw IllegalStateException("Cannot auto-create WATCH filters")
      }
    }
  }

  protected fun getBoardDescriptors(chanDescriptor: ChanDescriptor): Set<BoardDescriptor> {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return emptySet()
    }

    val boardDescriptors = hashSetWithCap<BoardDescriptor>(256)
    boardDescriptors.addAll(boardManager.getAllBoardDescriptorsForSite(chanDescriptor.siteDescriptor()))
    return boardDescriptors
  }

  protected fun getInternalIds(
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

  protected fun loadFilters(chanDescriptor: ChanDescriptor): List<ChanFilter> {
    BackgroundUtils.ensureBackgroundThread()

    val board = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
      ?: return emptyList()

    return filterEngine.enabledFilters
      .filter { filter -> filterEngine.matchesBoard(filter, board) }
  }

  companion object {
    private const val TAG = "AbstractParsePostsUseCase"
    private const val threadFactoryName = "post_parser_%d"

    private val threadIndex = AtomicInteger(0)
    val THREAD_COUNT = Runtime.getRuntime().availableProcessors()
    val dispatcher: CoroutineDispatcher

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