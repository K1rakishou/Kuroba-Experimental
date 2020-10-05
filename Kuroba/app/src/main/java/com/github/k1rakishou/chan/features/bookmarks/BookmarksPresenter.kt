package com.github.k1rakishou.chan.features.bookmarks

import com.github.k1rakishou.chan.Chan.Companion.inject
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.features.bookmarks.data.BookmarksControllerState
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkItemView
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
  private val bookmarksToHighlight: Set<ChanDescriptor.ThreadDescriptor>
) : BasePresenter<BookmarksView>() {

  @Inject
  lateinit var bookmarksManager: BookmarksManager
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

  private fun reloadBookmarks() {
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

  fun onBookmarkSwipedAway(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    bookmarksManager.deleteBookmark(threadDescriptor)
  }

  fun onBookmarkMoved(fromPosition: Int, toPosition: Int) {
    bookmarksManager.onBookmarkMoved(fromPosition, toPosition)
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

  private suspend fun showBookmarks(searchQuery: String?) {
    BackgroundUtils.ensureBackgroundThread()
    bookmarksManager.awaitUntilInitialized()

    val isWatcherEnabled = ChanSettings.watchEnabled.get()

    val bookmarks = bookmarksManager.mapNotNullBookmarksOrdered { threadBookmarkView ->
      val title = threadBookmarkView.title
        ?: "No title"

      if (searchQuery == null || title.contains(searchQuery, ignoreCase = true)) {
        val threadBookmarkStats = getThreadBookmarkStatsOrNull(isWatcherEnabled, threadBookmarkView)

        return@mapNotNullBookmarksOrdered ThreadBookmarkItemView(
          threadDescriptor = threadBookmarkView.threadDescriptor,
          title = title,
          highlight = threadBookmarkView.threadDescriptor in bookmarksToHighlight,
          thumbnailUrl = threadBookmarkView.thumbnailUrl,
          threadBookmarkStats = threadBookmarkStats
        )
      }

      return@mapNotNullBookmarksOrdered null
    }

    if (bookmarks.isEmpty()) {
      if (isSearchMode.get()) {
        setState(BookmarksControllerState.NothingFound(searchQuery ?: ""))
      } else {
        setState(BookmarksControllerState.Empty)
      }

      return
    }

    setState(BookmarksControllerState.Data(bookmarks))
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
  }
}