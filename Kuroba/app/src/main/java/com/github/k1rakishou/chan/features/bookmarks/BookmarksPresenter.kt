package com.github.k1rakishou.chan.features.bookmarks

import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.features.bookmarks.data.*
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkView
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

class BookmarksPresenter(
  private val bookmarksToHighlight: Set<ChanDescriptor.ThreadDescriptor>,
  private val bookmarksSelectionHelper: BookmarksSelectionHelper
) : BasePresenter<BookmarksView>() {

  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var threadBookmarkGroupManager: ThreadBookmarkGroupManager
  @Inject
  lateinit var pageRequestManager: PageRequestManager
  @Inject
  lateinit var archivesManager: ArchivesManager

  private val bookmarksRefreshed = AtomicBoolean(false)

  private val bookmarksControllerStateSubject = PublishProcessor.create<BookmarksControllerState>()
    .toSerialized()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val searchFlow = MutableStateFlow<SearchQuery>(SearchQuery.Closed)

  @OptIn(ExperimentalTime::class)
  override fun onCreate(view: BookmarksView) {
    super.onCreate(view)
    Chan.inject(this)

    scope.launch {
      scope.launch {
        bookmarksManager.listenForBookmarksChanges()
          .asFlow()
          .debounce(100.milliseconds)
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
          .debounce(100.milliseconds)
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
          .debounce(250.milliseconds)
          .collect { searchQuery ->
            if (searchQuery !is SearchQuery.Searching) {
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

      if (bookmarksRefreshed.compareAndSet(false, true)) {
        bookmarksManager.refreshBookmarks()
      }

      Logger.d(TAG, "calling reloadBookmarks() first time")
      reloadBookmarks()
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

      setState(BookmarksControllerState.Loading)
      Logger.d(TAG, "calling showBookmarks() because reloadBookmarks() was called")

      ModularResult.Try { showBookmarks() }.safeUnwrap { error ->
        Logger.e(TAG, "showBookmarks() error", error)
        setState(BookmarksControllerState.Error(error.errorMessageOrClassName()))

        return@launch
      }
    }
  }

  suspend fun deleteBookmarks(selectedItems: List<ChanDescriptor.ThreadDescriptor>): Boolean {
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

  fun onViewBookmarksModeChanged() {
    Logger.d(TAG, "calling reloadBookmarks() because view bookmarks mode changed")

    reloadBookmarks()
  }

  fun onBookmarkStatsClicked(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    bookmarksManager.updateBookmark(
      threadDescriptor,
      BookmarksManager.NotifyListenersOption.NotifyEager
    ) { threadBookmark -> threadBookmark.toggleWatching() }
  }

  private suspend fun showBookmarks() {
    BackgroundUtils.ensureBackgroundThread()

    bookmarksManager.awaitUntilInitialized()
    threadBookmarkGroupManager.awaitUntilInitialized()

    val isWatcherEnabled = ChanSettings.watchEnabled.get()
    val searchQuery = searchFlow.value as? SearchQuery.Searching

    val query = if (searchQuery?.query?.length ?: 0 >= MIN_QUERY_LENGTH) {
      searchQuery?.query
    } else {
      null
    }

    Logger.d(TAG, "showBookmarks($query)")

    val threadBookmarkItemViewList = bookmarksManager
      .mapNotNullAllBookmarks<ThreadBookmarkItemView> { threadBookmarkView ->
        val title = threadBookmarkView.title
          ?: "No title"

        if (query == null || title.contains(query, ignoreCase = true)) {
          val threadBookmarkStats = getThreadBookmarkStatsOrNull(isWatcherEnabled, threadBookmarkView)

          val selection = if (bookmarksSelectionHelper.isInSelectionMode()) {
            val isSelected = bookmarksSelectionHelper.isSelected(threadBookmarkView.threadDescriptor)
            ThreadBookmarkSelection(isSelected)
          } else {
            null
          }

          return@mapNotNullAllBookmarks ThreadBookmarkItemView(
            threadDescriptor = threadBookmarkView.threadDescriptor,
            groupId = threadBookmarkView.groupId,
            title = title,
            highlight = threadBookmarkView.threadDescriptor in bookmarksToHighlight,
            thumbnailUrl = threadBookmarkView.thumbnailUrl,
            threadBookmarkStats = threadBookmarkStats,
            selection = selection,
            createdOn = threadBookmarkView.createdOn
          )
      }

      return@mapNotNullAllBookmarks null
    }

    val groupedBookmarks = threadBookmarkGroupManager.groupBookmarks(threadBookmarkItemViewList.toList())
    sortBookmarks(groupedBookmarks)
    moveDeadBookmarksToEnd(groupedBookmarks)

    if (groupedBookmarks.isEmpty()) {
      if (query != null) {
        setState(BookmarksControllerState.NothingFound(query))
      } else {
        setState(BookmarksControllerState.Empty)
      }

      return
    }

    setState(BookmarksControllerState.Data(groupedBookmarks))
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

  private fun getThreadBookmarkStatsOrNull(
    isWatcherEnabled: Boolean,
    threadBookmarkView: ThreadBookmarkView
  ): ThreadBookmarkStats {
    if (archivesManager.isSiteArchive(threadBookmarkView.threadDescriptor.siteDescriptor())) {
      return ThreadBookmarkStats(
        watching = threadBookmarkView.isWatching(),
        isArchive = true
      )
    }

    val boardPage = pageRequestManager.getPage(threadBookmarkView.threadDescriptor)
    val currentPage = boardPage?.currentPage ?: 0
    val totalPages = boardPage?.totalPages ?: 0

    return ThreadBookmarkStats(
      watching = threadBookmarkView.isWatching(),
      isArchive = false,
      showBookmarkStats = isWatcherEnabled,
      newPosts = threadBookmarkView.newPostsCount(),
      newQuotes = threadBookmarkView.newQuotesCount(),
      totalPosts = threadBookmarkView.totalPostsCount,
      currentPage = currentPage,
      totalPages = totalPages,
      isBumpLimit = threadBookmarkView.isBumpLimit(),
      isImageLimit = threadBookmarkView.isImageLimit(),
      isFirstFetch = threadBookmarkView.isFirstFetch(),
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

    private val BOOKMARK_CREATED_ON_ASC_COMPARATOR = compareBy<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.createdOn }
    private val BOOKMARK_CREATED_ON_DESC_COMPARATOR = compareByDescending<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.createdOn }
    private val UNREAD_REPLIES_ASC_COMPARATOR = compareBy<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadBookmarkStats.newQuotes }
    private val UNREAD_REPLIES_DESC_COMPARATOR = compareByDescending<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadBookmarkStats.newQuotes }
    private val UNREAD_POSTS_ASC_COMPARATOR = compareBy<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadBookmarkStats.newPosts }
    private val UNREAD_POSTS_DESC_COMPARATOR = compareByDescending<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadBookmarkStats.newPosts }

    private val MOVE_DEAD_BOOKMARKS_TO_END_COMPARATOR = compareBy<ThreadBookmarkItemView> { bookmarkItemView ->
      bookmarkItemView.threadBookmarkStats.isDead() || !bookmarkItemView.threadBookmarkStats.watching
    }
  }
}