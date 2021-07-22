package com.github.k1rakishou.chan.features.filter_watches

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.watcher.FilterWatcherDelegate
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkStats
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkView
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.filter.ChanFilterWatchGroup
import com.github.k1rakishou.model.repository.ChanFilterWatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

class FilterWatchesPresenter : BasePresenter<FilterWatchesControllerView>() {

  @Inject
  lateinit var filterWatcherDelegate: FilterWatcherDelegate
  @Inject
  lateinit var filterWatchRepository: ChanFilterWatchRepository
  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var chanFilterManager: ChanFilterManager
  @Inject
  lateinit var pageRequestManager: PageRequestManager
  @Inject
  lateinit var archivesManager: ArchivesManager

  private val filterWatchesControllerStateFlow = MutableSharedFlow<FilterWatchesControllerState>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  init {
    Chan.getComponent().inject(this)
  }

  @OptIn(ExperimentalTime::class)
  override fun onCreate(view: FilterWatchesControllerView) {
    super.onCreate(view)

    scope.launch {
      filterWatcherDelegate.listenForBookmarkFilterWatchGroupsUpdatedFlowUpdates()
        .collect {
          Logger.d(TAG, "Reloading filter watches because filter watches were updated.")
          reloadFilterWatches()
        }
    }

    scope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .debounce(100.milliseconds)
        .collect {
          Logger.d(TAG, "Reloading filter watches because bookmarks were updated.")
          reloadFilterWatches()
        }
    }

    scope.launch {
      val job = scope.launch {
        delay(50L)
        updateState(FilterWatchesControllerState.Loading)
      }

      reloadFilterWatches(job)
    }
  }

  fun listenForStateUpdates(): SharedFlow<FilterWatchesControllerState> {
    return filterWatchesControllerStateFlow
  }

  private suspend fun reloadFilterWatches(loadingStateUpdateJob: Job? = null) {
    withContext(Dispatchers.Default) {
      reloadFilterWatchesInternal(loadingStateUpdateJob)
    }
  }

  private suspend fun reloadFilterWatchesInternal(loadingStateUpdateJob: Job? = null) {
    BackgroundUtils.ensureBackgroundThread()

    Logger.d(TAG, "reloadFilterWatchesInternal() getFilterWatchGroups() called, waiting for dependencies")

    bookmarksManager.awaitUntilInitialized()
    chanFilterManager.awaitUntilInitialized()
    archivesManager.awaitUntilInitialized()

    Logger.d(TAG, "reloadFilterWatchesInternal() getFilterWatchGroups() called, done")

    val getFilterWatchGroupsResult = filterWatchRepository.getFilterWatchGroups()
    loadingStateUpdateJob?.cancel()

    if (getFilterWatchGroupsResult is ModularResult.Error) {
      val error = getFilterWatchGroupsResult.error

      Logger.e(TAG, "reloadFilterWatchesInternal() getFilterWatchGroups() error", error)
      updateState(FilterWatchesControllerState.Error(error.errorMessageOrClassName()))
      return
    }

    val filterWatchGroups = (getFilterWatchGroupsResult as ModularResult.Value).value
    if (filterWatchGroups.isEmpty()) {
      updateState(FilterWatchesControllerState.Empty)
      return
    }

    val groupedFilterWatches = try {
      createGroupsOfFilterWatches(filterWatchGroups)
    } catch (error: Throwable) {
      Logger.e(TAG, "reloadFilterWatchesInternal() createGroupsOfFilterWatches() error", error)
      updateState(FilterWatchesControllerState.Error(error.errorMessageOrClassName()))
      return
    }

    if (groupedFilterWatches.isEmpty()) {
      updateState(FilterWatchesControllerState.Empty)
      return
    }

    updateState(FilterWatchesControllerState.Data(groupedFilterWatches))
  }

  private fun createGroupsOfFilterWatches(
    filterWatchGroupsInput: List<ChanFilterWatchGroup>
  ): List<GroupOfFilterWatches> {
    val filterWatchGroupsMutable = filterWatchGroupsInput.toMutableList()

    val filterIdSet = filterWatchGroupsInput
      .map { chanFilterWatchGroup -> chanFilterWatchGroup.ownerChanFilterDatabaseId }
      .toSet()

    val filters = mutableListWithCap<ChanFilter>(filterIdSet)

    chanFilterManager.viewAllFilters { chanFilter ->
      if (chanFilter.isEnabledWatchFilter() && chanFilter.getDatabaseId() in filterIdSet) {
        filters += chanFilter
      }
    }

    if (filters.isEmpty()) {
      return emptyList()
    }

    val groupOfFilterWatchesList = mutableListWithCap<GroupOfFilterWatches>(filters.size)
    val isFilterWatcherEnabled = ChanSettings.filterWatchEnabled.get()

    for (filter in filters) {
      val thisFilterWatchGroups = findAllFilterWatchGroupsForThisFilter(
        filter,
        filterWatchGroupsMutable
      )

      if (thisFilterWatchGroups.isEmpty()) {
        Logger.e(TAG, "Failed to find filter watch group for filter $filter")
        continue
      }

      val bookmarkDescriptors = thisFilterWatchGroups
        .map { chanFilterWatchGroup -> chanFilterWatchGroup.threadDescriptor }

      val filterWatches = mutableListOf<FilterWatch>()

      bookmarksManager.viewBookmarks(bookmarkDescriptors) { threadBookmarkView ->
        filterWatches += FilterWatch(
          threadDescriptor = threadBookmarkView.threadDescriptor,
          title = threadBookmarkView.title ?: threadBookmarkView.threadDescriptor.toString(),
          thumbnailUrl = threadBookmarkView.thumbnailUrl,
          threadBookmarkStats = getThreadBookmarkStats(isFilterWatcherEnabled, threadBookmarkView),
          highlight = false
        )
      }

      filterWatches
        .sortByDescending { filterWatch -> filterWatch.threadDescriptor.threadNo }

      groupOfFilterWatchesList += GroupOfFilterWatches(
        requireNotNull(filter.pattern) { "Must not be null!" },
        filterWatches
      )
    }

    return groupOfFilterWatchesList
  }

  private fun getThreadBookmarkStats(
    isFilterWatcherEnabled: Boolean,
    threadBookmarkView: ThreadBookmarkView
  ): ThreadBookmarkStats {
    if (archivesManager.isSiteArchive(threadBookmarkView.threadDescriptor.siteDescriptor())) {
      return ThreadBookmarkStats(
        watching = threadBookmarkView.isWatching(),
        isArchive = true,
        isFilterWatchBookmark = threadBookmarkView.isFilterWatchBookmark()
      )
    }

    val boardPage = pageRequestManager.getPage(threadBookmarkView.threadDescriptor)
    val currentPage = boardPage?.currentPage ?: 0
    val totalPages = boardPage?.totalPages ?: 0

    return ThreadBookmarkStats(
      watching = threadBookmarkView.isWatching(),
      isArchive = false,
      isWatcherEnabled = isFilterWatcherEnabled,
      newPosts = threadBookmarkView.newPostsCount(),
      newQuotes = threadBookmarkView.newQuotesCount(),
      totalPosts = threadBookmarkView.totalPostsCount,
      currentPage = currentPage,
      totalPages = totalPages,
      isBumpLimit = threadBookmarkView.isBumpLimit(),
      isImageLimit = threadBookmarkView.isImageLimit(),
      isFirstFetch = threadBookmarkView.isFirstFetch(),
      isFilterWatchBookmark = threadBookmarkView.isFilterWatchBookmark(),
      isDeleted = threadBookmarkView.isThreadDeleted(),
      isError = threadBookmarkView.isError()
    )
  }

  private fun findAllFilterWatchGroupsForThisFilter(
    filter: ChanFilter,
    filterWatchGroups: MutableList<ChanFilterWatchGroup>
  ): List<ChanFilterWatchGroup> {
    val foundFilterWatchGroups = mutableListOf<ChanFilterWatchGroup>()

    filterWatchGroups.mutableIteration { iterator, filterWatchGroup ->
      if (filterWatchGroup.ownerChanFilterDatabaseId == filter.getDatabaseId()) {
        foundFilterWatchGroups += filterWatchGroup
        iterator.remove()
      }

      return@mutableIteration true
    }

    return foundFilterWatchGroups
  }

  private suspend fun updateState(newState: FilterWatchesControllerState) {
    filterWatchesControllerStateFlow.emit(newState)
  }

  companion object {
    private const val TAG = "FilterWatchesPresenter"
  }
}
