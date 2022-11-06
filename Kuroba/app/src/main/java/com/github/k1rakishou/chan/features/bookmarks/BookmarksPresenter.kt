package com.github.k1rakishou.chan.features.bookmarks

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.features.bookmarks.data.BookmarksControllerState
import com.github.k1rakishou.chan.features.bookmarks.data.GroupOfThreadBookmarkItemViews
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkItemView
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkSelection
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkStats
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkView
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class BookmarksPresenter(
  private val bookmarksToHighlight: Set<ChanDescriptor.ThreadDescriptor>,
  private val bookmarksManager: BookmarksManager,
  private val threadBookmarkGroupManager: ThreadBookmarkGroupManager,
  private val pageRequestManager: PageRequestManager,
  private val archivesManager: ArchivesManager,
  private val bookmarksSelectionHelper: BookmarksSelectionHelper,
  private val threadDownloadManager: ThreadDownloadManager
) : BasePresenter<BookmarksView>() {

  private val bookmarksRefreshed = AtomicBoolean(false)
  private val isReorderingMode = AtomicBoolean(false)

  private val bookmarksControllerStateSubject = PublishProcessor.create<BookmarksControllerState>()
    .toSerialized()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val searchFlow = MutableStateFlow<SearchQuery>(SearchQuery.Closed)

  @OptIn(ExperimentalTime::class)
  override fun onCreate(view: BookmarksView) {
    super.onCreate(view)

    scope.launch {
      scope.launch {
        bookmarksManager.listenForBookmarksChanges()
          .debounce(Duration.milliseconds(100))
          .collect {
            withContext(Dispatchers.Default) {
              Logger.d(TAG, "calling showBookmarks() because bookmarks have changed")

              ModularResult.Try { showBookmarks() }.safeUnwrap { error ->
                Logger.e(TAG, "showBookmarks() listenForBookmarksChanges error", error)
                setState(BookmarksControllerState.Error(error.errorMessageOrClassName()))

                return@withContext
              }
            }
          }
      }

      scope.launch {
        bookmarksSelectionHelper.listenForSelectionChanges()
          .debounce(Duration.milliseconds(100))
          .collect {
            withContext(Dispatchers.Default) {
              Logger.d(TAG, "calling showBookmarks() because bookmark selection has changed")

              ModularResult.Try { showBookmarks() }.safeUnwrap { error ->
                Logger.e(TAG, "showBookmarks() listenForSelectionChanges error", error)
                setState(BookmarksControllerState.Error(error.errorMessageOrClassName()))

                return@withContext
              }
            }
          }
      }

      scope.launch {
        searchFlow
          .debounce(Duration.milliseconds(125))
          .collect { searchQuery ->
            if (searchQuery is SearchQuery.Opened) {
              // To avoid refreshing bookmarks list twice
              return@collect
            }

            withContext(Dispatchers.Default) {
              Logger.d(TAG, "calling showBookmarks() because search query was entered")

              ModularResult.Try { showBookmarks() }.safeUnwrap { error ->
                Logger.e(TAG, "showBookmarks() searchSubject error", error)
                setState(BookmarksControllerState.Error(error.errorMessageOrClassName()))

                return@withContext
              }
            }
          }
      }
    }
  }

  fun listenForStateChanges(): Flowable<BookmarksControllerState> {
    return bookmarksControllerStateSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error ->
        Logger.e(TAG, "Unknown error subscribed to bookmarksPresenter.listenForStateChanges()", error)
      }
      .onErrorReturn { error -> BookmarksControllerState.Error(error.errorMessageOrClassName()) }
      .hide()
  }

  fun onBookmarkMoving(
    groupId: String,
    fromBookmarkDescriptor: ChanDescriptor.ThreadDescriptor,
    toBookmarkDescriptor: ChanDescriptor.ThreadDescriptor
  ): Boolean {
    return runBlocking(Dispatchers.Default) {
      val result = threadBookmarkGroupManager.onBookmarkMoving(
        groupId = groupId,
        fromBookmarkDescriptor = fromBookmarkDescriptor,
        toBookmarkDescriptor = toBookmarkDescriptor
      )

      if (!result) {
        return@runBlocking false
      }

      showBookmarks(null)
      return@runBlocking true
    }
  }

  fun onBookmarkMoved(groupId: String) {
    scope.launch(Dispatchers.Default) {
      threadBookmarkGroupManager.persistGroup(groupId)
    }
  }

  fun toggleBookmarkExpandState(groupId: String) {
    scope.launch(Dispatchers.Default) {
      threadBookmarkGroupManager.toggleBookmarkExpandState(groupId)
      Logger.d(TAG, "calling showBookmarks() because bookmark selection was toggled")

      ModularResult.Try { showBookmarks() }.safeUnwrap { error ->
        Logger.e(TAG, "showBookmarks() error", error)
        setState(BookmarksControllerState.Error(error.errorMessageOrClassName()))

        return@launch
      }
    }
  }

  fun reloadBookmarks() {
    scope.launch(Dispatchers.Default) {
      bookmarksManager.awaitUntilInitialized()

      if (bookmarksManager.bookmarksCount() <= 0) {
        setState(BookmarksControllerState.Empty)
        return@launch
      }

      val loadingStateCancellationJob = launch {
        delay(125)
        setState(BookmarksControllerState.Loading)
      }

      Logger.d(TAG, "calling showBookmarks() because reloadBookmarks() was called")

      ModularResult.Try { showBookmarks(loadingStateCancellationJob) }.safeUnwrap { error ->
        Logger.e(TAG, "showBookmarks() error", error)

        // Cancel the setState(Loading) event
        if (!loadingStateCancellationJob.isCancelled) {
          loadingStateCancellationJob.cancel()
        }

        setState(BookmarksControllerState.Error(error.errorMessageOrClassName()))

        return@launch
      }
    }
  }

  fun deleteBookmarks(selectedItems: List<ChanDescriptor.ThreadDescriptor>): Boolean {
    return bookmarksManager.deleteBookmarks(selectedItems)
  }

  fun hasBookmarks(): Boolean {
    return bookmarksManager.bookmarksCount() > 0
  }

  fun pruneNonActive() {
    bookmarksManager.pruneNonActive()
  }

  fun clearAllBookmarks() {
    bookmarksManager.deleteAllBookmarks()
  }

  fun markAllAsSeen() {
    bookmarksManager.readAllPostsAndNotifications()
  }

  fun onSearchModeChanged(visible: Boolean) {
    if (visible) {
      searchFlow.value = SearchQuery.Opened
    } else {
      // Reset back to normal state when pressing hardware back button
      searchFlow.value = SearchQuery.Closed
    }
  }

  fun onSearchEntered(query: String) {
    searchFlow.value = SearchQuery.Searching(query)
  }

  fun isInSearchMode(): Boolean = searchFlow.value !is SearchQuery.Closed

  fun isInReorderingMode(): Boolean = isReorderingMode.get()

  fun updateReorderingMode(enterReorderingMode: Boolean): Boolean {
    if (!isReorderingMode.compareAndSet(enterReorderingMode.not(), enterReorderingMode)) {
      // Already in reordering mode or already not in reordering mode
      return false
    }

    Logger.d(TAG, "calling reloadBookmarks() because reordering mode changed")

    reloadBookmarks()
    return true
  }

  fun markAsRead(threadDescriptors: List<ChanDescriptor.ThreadDescriptor>) {
    if (threadDescriptors.isEmpty()) {
      return
    }

    bookmarksManager.enqueuePersistFunc {
      val updatedBookmarkDescriptors = bookmarksManager.updateBookmarksNoPersist(threadDescriptors) { threadBookmark ->
        threadBookmark.readAllPostsAndNotifications()
      }

      if (updatedBookmarkDescriptors.isNotEmpty()) {
        bookmarksManager.persistBookmarksManually(updatedBookmarkDescriptors)
      }
    }
  }

  fun onViewBookmarksModeChanged() {
    Logger.d(TAG, "calling reloadBookmarks() because view bookmarks mode changed")

    reloadBookmarks()
  }

  fun onBookmarkStatsClicked(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    bookmarksManager.enqueuePersistFunc {
      val updatedBookmarkDescriptor = bookmarksManager.updateBookmarkNoPersist(threadDescriptor) { threadBookmark ->
        threadBookmark.toggleWatching()
      }

      if (updatedBookmarkDescriptor != null) {
        bookmarksManager.persistBookmarkManually(threadDescriptor)
      }
    }
  }

  private suspend fun showBookmarks(loadingStateCancellationJob: Job? = null) {
    BackgroundUtils.ensureBackgroundThread()
    bookmarksManager.awaitUntilInitialized()

    val isWatcherEnabled = ChanSettings.watchEnabled.get()
    val searchQuery = searchFlow.value as? SearchQuery.Searching

    val query = if (searchQuery?.query?.length ?: 0 >= MIN_QUERY_LENGTH) {
      searchQuery?.query
    } else {
      null
    }

    Logger.d(TAG, "showBookmarks($query)")

    val downloadingThreadDescriptors = threadDownloadManager.getDownloadingThreadDescriptors()

    val threadBookmarkItemViewList = bookmarksManager
      .mapAllBookmarks<ThreadBookmarkItemView> { threadBookmarkView ->
        val title = threadBookmarkView.title
          ?: "No title"

        val threadBookmarkStats = getThreadBookmarkStats(
          isWatcherEnabled = isWatcherEnabled,
          threadBookmarkView = threadBookmarkView,
          downloadingThreadDescriptors = downloadingThreadDescriptors
        )

        val selection = if (bookmarksSelectionHelper.isInSelectionMode()) {
          val isSelected = bookmarksSelectionHelper.isSelected(threadBookmarkView.threadDescriptor)
          ThreadBookmarkSelection(isSelected)
        } else {
          null
        }

        return@mapAllBookmarks ThreadBookmarkItemView(
          threadDescriptor = threadBookmarkView.threadDescriptor,
          title = title,
          highlight = threadBookmarkView.threadDescriptor in bookmarksToHighlight,
          thumbnailUrl = threadBookmarkView.thumbnailUrl,
          threadBookmarkStats = threadBookmarkStats,
          selection = selection,
          createdOn = threadBookmarkView.createdOn
        )
      }

    val groupedBookmarks = threadBookmarkGroupManager.groupBookmarks(
      threadBookmarkViewList = threadBookmarkItemViewList.toList(),
      bookmarksToHighlight = bookmarksToHighlight,
      hasSearchQuery = query.isNotNullNorEmpty()
    )

    val groupedFilteredBookmarks = if (query.isNotNullNorEmpty()) {
      processSearchQuery(groupedBookmarks, query)
    } else {
      groupedBookmarks
    }

    // The function call order matters!
    // First we need to do the general sorting.
    sortBookmarks(groupedFilteredBookmarks)

    // Then we need to move dead bookmarks to bottom.
    moveDeadBookmarksToEnd(groupedFilteredBookmarks)

    // Bookmarks with replies have the highest priority, so we are moving them at the latest step.
    moveBookmarksWithUnreadRepliesToTop(groupedFilteredBookmarks)

    // Cancel the setState(Loading) event
    loadingStateCancellationJob?.let { job ->
      if (!job.isCancelled) {
        job.cancel()
      }
    }

    if (groupedFilteredBookmarks.isEmpty()) {
      if (query != null) {
        setState(BookmarksControllerState.NothingFound(query))
      } else {
        setState(BookmarksControllerState.Empty)
      }

      return
    }

    setState(BookmarksControllerState.Data(isReorderingMode.get(), groupedFilteredBookmarks))
  }

  private fun processSearchQuery(
    groupedBookmarks: List<GroupOfThreadBookmarkItemViews>,
    query: String
  ): List<GroupOfThreadBookmarkItemViews> {
    val resultGroupedBookmarks = mutableListOf<GroupOfThreadBookmarkItemViews>()

    for (groupedBookmark in groupedBookmarks) {
      if (groupedBookmark.groupInfoText.contains(query, ignoreCase = true)) {
        resultGroupedBookmarks += groupedBookmark
        continue
      }

      groupedBookmark.threadBookmarkItemViews.mutableIteration { mutableIterator, threadBookmarkItemView ->
        if (!threadBookmarkItemView.title.contains(query, ignoreCase = true)) {
          mutableIterator.remove()
        }

        return@mutableIteration true
      }

      resultGroupedBookmarks += groupedBookmark
    }

    return resultGroupedBookmarks
  }

  private fun moveBookmarksWithUnreadRepliesToTop(
    bookmarks: List<GroupOfThreadBookmarkItemViews>
  ) {
    if (!ChanSettings.moveBookmarksWithUnreadRepliesToTop.get()) {
      return
    }

    val bookmarksSortOrder = ChanSettings.bookmarksSortOrder.get()

    if (bookmarksSortOrder == ChanSettings.BookmarksSortOrder.UnreadRepliesAscending
      || bookmarksSortOrder == ChanSettings.BookmarksSortOrder.UnreadRepliesDescending) {
      return
    }

    bookmarks.forEach { groupOfThreadBookmarkItemViews ->
      groupOfThreadBookmarkItemViews.threadBookmarkItemViews.sortWith(
        MOVE_BOOKMARKS_WITH_UNREAD_REPLIES_TO_TOP_COMPARATOR
      )
    }
  }

  private fun moveDeadBookmarksToEnd(
    bookmarks: List<GroupOfThreadBookmarkItemViews>
  ) {
    if (!ChanSettings.moveNotActiveBookmarksToBottom.get()) {
      return
    }

    bookmarks.forEach { groupOfThreadBookmarkItemViews ->
      groupOfThreadBookmarkItemViews.threadBookmarkItemViews.sortWith(
        MOVE_DEAD_BOOKMARKS_TO_END_COMPARATOR
      )
    }
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun sortBookmarks(
    bookmarks: List<GroupOfThreadBookmarkItemViews>
  ) {
    val comparator = when (val sortOrder = ChanSettings.bookmarksSortOrder.get()) {
      ChanSettings.BookmarksSortOrder.CreatedOnAscending -> BOOKMARK_CREATED_ON_ASC_COMPARATOR
      ChanSettings.BookmarksSortOrder.CreatedOnDescending -> BOOKMARK_CREATED_ON_DESC_COMPARATOR
      ChanSettings.BookmarksSortOrder.ThreadIdAscending -> THREAD_ID_ASC_COMPARATOR
      ChanSettings.BookmarksSortOrder.ThreadIdDescending -> THREAD_ID_DESC_COMPARATOR
      ChanSettings.BookmarksSortOrder.UnreadRepliesAscending -> UNREAD_REPLIES_ASC_COMPARATOR
      ChanSettings.BookmarksSortOrder.UnreadRepliesDescending -> UNREAD_REPLIES_DESC_COMPARATOR
      ChanSettings.BookmarksSortOrder.UnreadPostsAscending -> UNREAD_POSTS_ASC_COMPARATOR
      ChanSettings.BookmarksSortOrder.UnreadPostsDescending -> UNREAD_POSTS_DESC_COMPARATOR
      ChanSettings.BookmarksSortOrder.CustomAscending,
      ChanSettings.BookmarksSortOrder.CustomDescending -> {
        handleCustomOrder(sortOrder, bookmarks)
        return
      }
    }

    bookmarks.forEach { groupOfThreadBookmarkItemViews ->
      groupOfThreadBookmarkItemViews.threadBookmarkItemViews.sortWith(comparator)
    }
  }

  private fun handleCustomOrder(
    sortOrder: ChanSettings.BookmarksSortOrder?,
    bookmarks: List<GroupOfThreadBookmarkItemViews>
  ) {
    if (sortOrder == ChanSettings.BookmarksSortOrder.CustomAscending) {
      return
    }

    check(sortOrder == ChanSettings.BookmarksSortOrder.CustomDescending) {
      "Unexpected sortOrder! sortOrder: $sortOrder"
    }

    bookmarks.forEach { groupOfThreadBookmarkItemViews ->
      groupOfThreadBookmarkItemViews.threadBookmarkItemViews.reverse()
    }

    return
  }

  private fun getThreadBookmarkStats(
    isWatcherEnabled: Boolean,
    threadBookmarkView: ThreadBookmarkView,
    downloadingThreadDescriptors: Set<ChanDescriptor.ThreadDescriptor>
  ): ThreadBookmarkStats {
    if (archivesManager.isSiteArchive(threadBookmarkView.threadDescriptor.siteDescriptor())) {
      return ThreadBookmarkStats(
        watching = threadBookmarkView.isWatching(),
        isArchive = true,
        isFilterWatchBookmark = threadBookmarkView.isFilterWatchBookmark(),
        isDownloading = threadBookmarkView.threadDescriptor in downloadingThreadDescriptors
      )
    }

    val boardPage = pageRequestManager.getPage(threadBookmarkView.threadDescriptor)
    val currentPage = boardPage?.currentPage ?: 0
    val totalPages = boardPage?.totalPages ?: 0

    return ThreadBookmarkStats(
      watching = threadBookmarkView.isWatching(),
      isArchive = false,
      isWatcherEnabled = isWatcherEnabled,
      newPosts = threadBookmarkView.newPostsCount(),
      newQuotes = threadBookmarkView.newQuotesCount(),
      totalPosts = threadBookmarkView.totalPostsCount,
      currentPage = currentPage,
      totalPages = totalPages,
      isBumpLimit = threadBookmarkView.isBumpLimit(),
      isImageLimit = threadBookmarkView.isImageLimit(),
      isFirstFetch = threadBookmarkView.isFirstFetch(),
      isFilterWatchBookmark = threadBookmarkView.isFilterWatchBookmark(),
      isDownloading = threadBookmarkView.threadDescriptor in downloadingThreadDescriptors,
      isDeleted = threadBookmarkView.isThreadDeleted(),
      isError = threadBookmarkView.isError()
    )
  }

  private fun setState(state: BookmarksControllerState) {
    bookmarksControllerStateSubject.onNext(state)
  }

  sealed class SearchQuery {
    object Closed : SearchQuery()
    object Opened : SearchQuery()
    class Searching(val query: String) : SearchQuery()
  }

  companion object {
    private const val TAG = "BookmarksPresenter"
    private const val MIN_QUERY_LENGTH = 3

    private val BOOKMARK_CREATED_ON_ASC_COMPARATOR =
      compareBy<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.createdOn }
    private val BOOKMARK_CREATED_ON_DESC_COMPARATOR =
      compareByDescending<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.createdOn }

    private val THREAD_ID_ASC_COMPARATOR =
      compareBy<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadDescriptor }
    private val THREAD_ID_DESC_COMPARATOR =
      compareByDescending<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadDescriptor }

    private val UNREAD_REPLIES_ASC_COMPARATOR =
      compareBy<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadBookmarkStats.newQuotes }
    private val UNREAD_REPLIES_DESC_COMPARATOR =
      compareByDescending<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadBookmarkStats.newQuotes }

    private val UNREAD_POSTS_ASC_COMPARATOR =
      compareBy<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadBookmarkStats.newPosts }
    private val UNREAD_POSTS_DESC_COMPARATOR =
      compareByDescending<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadBookmarkStats.newPosts }

    private val MOVE_DEAD_BOOKMARKS_TO_END_COMPARATOR =
      compareBy<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadBookmarkStats.isDeadOrNotWatching() }
    private val MOVE_BOOKMARKS_WITH_UNREAD_REPLIES_TO_TOP_COMPARATOR =
      compareByDescending<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadBookmarkStats.newQuotes }
  }
}