package com.github.k1rakishou.chan.core.site.loader.internal.usecase

import com.github.k1rakishou.chan.core.helper.ChanLoadProgressEvent
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.parallelForEach
import com.github.k1rakishou.common.parallelForEachIndexed
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.filter.FilterAction
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.data.post.PostFilter
import com.github.k1rakishou.model.repository.ChanPostRepository
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

abstract class AbstractParsePostsUseCase(
  protected val verboseLogsEnabled: Boolean,
  protected val chanPostRepository: ChanPostRepository,
  protected val filterEngine: FilterEngine,
  protected val postFilterManager: PostFilterManager,
  protected val postHideManager: PostHideManager,
  protected val savedReplyManager: SavedReplyManager,
  protected val boardManager: BoardManager,
  protected val chanLoadProgressNotifier: ChanLoadProgressNotifier
) {

  abstract suspend fun parseNewPostsPosts(
    chanDescriptor: ChanDescriptor,
    postParser: PostParser,
    postBuildersToParse: List<ChanPostBuilder>
  ): ParsingResult

  protected suspend fun processSavedReplies(postBuildersToParse: List<ChanPostBuilder>) {
    if (postBuildersToParse.isEmpty()) {
      return
    }

    parallelForEach(postBuildersToParse, THREAD_COUNT * 2, Dispatchers.IO) { postToParse ->
      // needed for "Apply to own posts" to work correctly
      postToParse.isSavedReply(savedReplyManager.isSaved(postToParse.postDescriptor()))
    }
  }

  protected suspend fun processFilters(
    chanDescriptor: ChanDescriptor,
    postBuildersToParse: List<ChanPostBuilder>,
    filters: List<ChanFilter>
  ) {
    if (postBuildersToParse.isEmpty() || filters.isEmpty()) {
      return
    }

    val totalPosts = postBuildersToParse.size
    val filtersCount = filters.size

    parallelForEachIndexed(postBuildersToParse, THREAD_COUNT * 2, Dispatchers.IO) { index, postToParse ->
      if (filters.isNotEmpty()) {
        processFilters(postToParse, filters)
      }

      chanLoadProgressNotifier.sendProgressEvent(
        ChanLoadProgressEvent.ProcessingFilters(
          chanDescriptor = chanDescriptor,
          processedPosts = index + 1,
          totalPosts = totalPosts,
          filtersCount = filtersCount
        )
      )

      return@parallelForEachIndexed
    }

    Logger.debug(TAG) {
      "postParsingProcessFiltersStage() " +
        "cacheHits: ${filterEngine.currentCacheHits()}, " +
        "cacheMisses: ${filterEngine.currentCacheMisses()}"
    }
  }

  private fun processFilters(postToParse: ChanPostBuilder, filters: List<ChanFilter>) {
    // Process the filters before finish, because parsing the html is dependent on filter matches
    val postDescriptor = postToParse.postDescriptor()

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

    if (postHideManager.contains(postDescriptor)) {
      val filterDatabaseId = postHideManager.filterDatabaseId(postDescriptor)
      if (filterDatabaseId != null) {
        val chanFilterForPostHide = filters.firstOrNull { chanFilter -> chanFilter.getDatabaseId() == filterDatabaseId }
        if (chanFilterForPostHide != null) {
          if (chanFilterForPostHide.enabled) {
            // Filter is enabled, hide/remove the post
            return
          }

          if (!postHideManager.isManuallyRestored(postDescriptor)) {
            // Filter is disabled, remove ChanPostHide
            // TODO: ideally, this needs batching
            postHideManager.remove(postDescriptor)
            return
          }

          // ChanPostHide was manually restored. We don't want to remove such ChanPostHides otherwise the posts that
          // the user has restored manually will get hidden again on the next thread refresh.
        }

        // Failed to find ChanFilter for this ChanPostHide. Most likely it's filterDatabaseId is null which means it
        // wasn't created by a ChanFilter
      }

      // Do not create a filter for post hides. When we remove a post filter manually (unhide/unremove)
      // we create a post hide with `manuallyRestored` flag set to true. We use that when opening
      // medias to not filter out such posts/threads.
      return
    }

    for (filter in filters) {
      if (filterEngine.matches(filter, postToParse)) {
        postFilterManager.insert(postDescriptor, createPostFilter(filter))
        return
      }
    }

    // None of the filters matched this post so remove PostFilter associated with this post.
    postFilterManager.remove(postDescriptor)
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun createPostFilter(filter: ChanFilter): PostFilter {
    return when (FilterAction.forId(filter.action)) {
      FilterAction.COLOR -> {
        PostFilter(
          ownerFilterId = filter.getDatabaseId(),
          filterEnabled = filter.enabled,
          filterHighlightedColor = filter.color,
          filterReplies = filter.applyToReplies,
          filterOnlyOP = filter.onlyOnOP,
          filterSaved = filter.applyToSaved
        )
      }
      FilterAction.HIDE -> {
        PostFilter(
          ownerFilterId = filter.getDatabaseId(),
          filterEnabled = filter.enabled,
          filterStub = true,
          filterReplies = filter.applyToReplies,
          filterOnlyOP = filter.onlyOnOP
        )
      }
      FilterAction.REMOVE -> {
        PostFilter(
          ownerFilterId = filter.getDatabaseId(),
          filterEnabled = filter.enabled,
          filterRemove = true,
          filterReplies = filter.applyToReplies,
          filterOnlyOP = filter.onlyOnOP
        )
      }
      FilterAction.WATCH -> {
        error("Cannot auto-create WATCH filters")
      }
      FilterAction.AVOID_WATCH -> {
        error("Cannot auto-create AVOID_WATCH filters")
      }
    }
  }

  protected fun getInternalPostDescriptors(
    chanDescriptor: ChanDescriptor,
    postBuildersToParse: List<ChanPostBuilder>
  ): Set<PostDescriptor> {
    val postsToParseNoSet = postBuildersToParse.map { postBuilder -> postBuilder.postDescriptor() }.toSet()

    if (chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
      return postsToParseNoSet
    }

    chanDescriptor as ChanDescriptor.ThreadDescriptor
    return postsToParseNoSet + chanPostRepository.getCachedThreadPostDescriptors(chanDescriptor)
  }

  protected fun loadFilters(chanDescriptor: ChanDescriptor): List<ChanFilter> {
    BackgroundUtils.ensureBackgroundThread()

    return filterEngine.allFiltersSorted
      .filter { filter -> filterEngine.matchesBoard(filter, chanDescriptor.boardDescriptor()) }
  }

  class ParsingResult @OptIn(ExperimentalTime::class) constructor(
    val parsedPosts: List<ChanPost>,
    val filterProcessionTime: Duration,
    val filtersCount: Int,
    val parsingTime: Duration
  )

  companion object {
    private const val TAG = "AbstractParsePostsUseCase"
    val THREAD_COUNT = Runtime.getRuntime().availableProcessors()
  }
}