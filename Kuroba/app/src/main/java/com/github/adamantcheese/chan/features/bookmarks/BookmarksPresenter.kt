package com.github.adamantcheese.chan.features.bookmarks

import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.core.base.BasePresenter
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.core.manager.PageRequestManager
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.features.bookmarks.data.BookmarksControllerState
import com.github.adamantcheese.chan.features.bookmarks.data.ThreadBookmarkItemView
import com.github.adamantcheese.chan.features.bookmarks.data.ThreadBookmarkStats
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.errorMessageOrClassName
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
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

  private val isSearchMode = AtomicBoolean(false)

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
          .debounce(350.milliseconds)
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

      reloadBookmarks()
    }
  }

  private fun reloadBookmarks() {
    scope.launch(Dispatchers.Default) {
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
      .observeOn(AndroidSchedulers.mainThread())
      .debounce(250, TimeUnit.MILLISECONDS)
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

  fun hasNonActiveBookmarks(): Boolean {
    return bookmarksManager.hasNonActiveBookmarks()
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
        val boardPage = pageRequestManager.getPage(threadBookmarkView.threadDescriptor)
        val currentPage = boardPage?.currentPage ?: 0
        val totalPages = boardPage?.totalPages ?: 0

        return@mapNotNullBookmarksOrdered ThreadBookmarkItemView(
          threadDescriptor = threadBookmarkView.threadDescriptor,
          title = title,
          hightlight = threadBookmarkView.threadDescriptor in bookmarksToHighlight,
          thumbnailUrl = threadBookmarkView.thumbnailUrl,
          threadBookmarkStats = ThreadBookmarkStats(
            showBookmarkStats = isWatcherEnabled,
            watching = threadBookmarkView.isWatching(),
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

  private fun setState(state: BookmarksControllerState) {
    bookmarksControllerStateSubject.onNext(state)
  }

  companion object {
    private const val TAG = "BookmarksPresenter"
  }
}