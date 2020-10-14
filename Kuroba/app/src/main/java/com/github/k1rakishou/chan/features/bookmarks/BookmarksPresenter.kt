package com.github.k1rakishou.chan.features.bookmarks

import com.github.k1rakishou.chan.Chan.Companion.inject
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.features.bookmarks.data.BookmarksControllerState
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkItemView
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkSelection
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkStats
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

  private val isSearchMode = AtomicBoolean(false)
  private val bookmarksRefreshed = AtomicBoolean(false)

  private val bookmarksControllerStateSubject = PublishProcessor.create<BookmarksControllerState>()
    .toSerialized()
  private val searchSubject = PublishProcessor.create<String>()
    .toSerialized()

  @OptIn(ExperimentalTime::class)
  override fun onCreate(view: BookmarksView) {
    super.onCreate(view)
    inject(this)

    scope.launch {
      scope.launch {
        bookmarksManager.listenForBookmarksChanges()
          .asFlow()
          .debounce(100.milliseconds)
          .collect {
            withContext(Dispatchers.Default) {
              ModularResult.Try { showBookmarks(null) }.safeUnwrap { error ->
                Logger.e(TAG, "showBookmarks() listenForBookmarksChanges error", error)
                setState(BookmarksControllerState.Error(error.errorMessageOrClassName()))

                return@withContext
              }
            }
          }
      }

      scope.launch {
        bookmarksSelectionHelper.listenForSelectionChanges()
          .asFlow()
          .debounce(100.milliseconds)
          .collect {
            withContext(Dispatchers.Default) {
              ModularResult.Try { showBookmarks(null) }.safeUnwrap { error ->
                Logger.e(TAG, "showBookmarks() listenForSelectionChanges error", error)
                setState(BookmarksControllerState.Error(error.errorMessageOrClassName()))

                return@withContext
              }
            }
          }
      }

      scope.launch {
        searchSubject.asFlow()
          .debounce(250.milliseconds)
          .collect { query ->
            setState(BookmarksControllerState.Loading)

            withContext(Dispatchers.Default) {
              val searchQuery = if (isSearchMode.get() && query.length >= 3) {
                query
              } else {
                null
              }

              ModularResult.Try { showBookmarks(searchQuery) }.safeUnwrap { error ->
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

      // TODO(KurobaEx): do something with query
      ModularResult.Try { showBookmarks(null) }.safeUnwrap { error ->
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

      ModularResult.Try { showBookmarks(null) }.safeUnwrap { error ->
        Logger.e(TAG, "showBookmarks() error", error)
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
    isSearchMode.set(visible)

    if (!visible) {
      // Reset back to normal state when pressing hardware back button
      searchSubject.onNext("")
    }
  }

  fun onSearchEntered(query: String) {
    searchSubject.onNext(query)
  }

  fun onViewBookmarksModeChanged() {
    reloadBookmarks()
  }

  fun onBookmarkStatsClicked(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    bookmarksManager.updateBookmark(
      threadDescriptor,
      BookmarksManager.NotifyListenersOption.NotifyEager
    ) { threadBookmark -> threadBookmark.toggleWatching() }
  }

  suspend fun showBookmarks(searchQuery: String?) {
    BackgroundUtils.ensureBackgroundThread()

    bookmarksManager.awaitUntilInitialized()
    threadBookmarkGroupManager.awaitUntilInitialized()

    val isWatcherEnabled = ChanSettings.watchEnabled.get()

    val threadBookmarkItemViewList = bookmarksManager
      .mapNotNullAllBookmarks<ThreadBookmarkItemView> { threadBookmarkView ->
        val title = threadBookmarkView.title
          ?: "No title"

        if (searchQuery == null || title.contains(searchQuery, ignoreCase = true)) {
          val threadBookmarkStats = getThreadBookmarkStatsOrNull(isWatcherEnabled, threadBookmarkView)

          val selection = if (bookmarksSelectionHelper.isInSelectionMode()) {
            ThreadBookmarkSelection(bookmarksSelectionHelper.isSelected(threadBookmarkView.threadDescriptor))
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

    val groupedBookmarks = threadBookmarkGroupManager.groupBookmarks(threadBookmarkItemViewList)

    // TODO(KurobaEx): sorting!
//    val sortedBookmarks = sortBookmarks(threadBookmarkItemViewList)
    if (groupedBookmarks.isEmpty()) {
      if (isSearchMode.get()) {
        setState(BookmarksControllerState.NothingFound(searchQuery ?: ""))
      } else {
        setState(BookmarksControllerState.Empty)
      }

      return
    }

    setState(BookmarksControllerState.Data(groupedBookmarks))
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun sortBookmarks(bookmarks: List<ThreadBookmarkItemView>): List<ThreadBookmarkItemView> {
    when (ChanSettings.bookmarksSortOrder.get()) {
      ChanSettings.BookmarksSortOrder.CreatedOnAscending -> {
        return bookmarks.sortedWith(BOOKMARK_CREATED_ON_ASC_COMPARATOR)
      }
      ChanSettings.BookmarksSortOrder.CreatedOnDescending -> {
        return bookmarks.sortedWith(BOOKMARK_CREATED_ON_DESC_COMPARATOR)
      }
      ChanSettings.BookmarksSortOrder.UnreadRepliesAscending -> {
        return bookmarks.sortedWith(UNREAD_REPLIES_ASC_COMPARATOR)
      }
      ChanSettings.BookmarksSortOrder.UnreadRepliesDescending -> {
        return bookmarks.sortedWith(UNREAD_REPLIES_DESC_COMPARATOR)
      }
      ChanSettings.BookmarksSortOrder.UnreadPostsAscending -> {
        return bookmarks.sortedWith(UNREAD_POSTS_ASC_COMPARATOR)
      }
      ChanSettings.BookmarksSortOrder.UnreadPostsDescending -> {
        return bookmarks.sortedWith(UNREAD_POSTS_DESC_COMPARATOR)
      }
    }
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
      isError = threadBookmarkView.isError()
    )
  }

  private fun setState(state: BookmarksControllerState) {
    bookmarksControllerStateSubject.onNext(state)
  }

  companion object {
    private const val TAG = "BookmarksPresenter"

    private val BOOKMARK_CREATED_ON_ASC_COMPARATOR = compareBy<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.createdOn }
    private val BOOKMARK_CREATED_ON_DESC_COMPARATOR = compareByDescending<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.createdOn }
    private val UNREAD_REPLIES_ASC_COMPARATOR = compareBy<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadBookmarkStats.newQuotes }
    private val UNREAD_REPLIES_DESC_COMPARATOR = compareByDescending<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadBookmarkStats.newQuotes }
    private val UNREAD_POSTS_ASC_COMPARATOR = compareBy<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadBookmarkStats.newPosts }
    private val UNREAD_POSTS_DESC_COMPARATOR = compareByDescending<ThreadBookmarkItemView> { bookmarkItemView -> bookmarkItemView.threadBookmarkStats.newPosts }
  }
}