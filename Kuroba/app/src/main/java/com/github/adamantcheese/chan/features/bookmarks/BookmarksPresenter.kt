package com.github.adamantcheese.chan.features.bookmarks

import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.core.base.BasePresenter
import com.github.adamantcheese.chan.core.manager.BookmarksManager
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
import kotlin.math.max
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

class BookmarksPresenter(
  private val bookmarksToHighlight: Set<ChanDescriptor.ThreadDescriptor>
) : BasePresenter<BookmarksView>() {

  @Inject
  lateinit var bookmarksManager: BookmarksManager

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
      .distinctUntilChanged()
      .debounce(250, TimeUnit.MILLISECONDS)
      .hide()
  }

  fun onBookmarkSwipedAway(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    bookmarksManager.deleteBookmark(threadDescriptor)
  }

  fun onBookmarkMoved(fromPosition: Int, toPosition: Int) {
    bookmarksManager.onBookmarkMoved(fromPosition, toPosition)
  }

  fun pruneNonActive() {
    bookmarksManager.pruneNonActive()
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

  private suspend fun showBookmarks(searchQuery: String?) {
    BackgroundUtils.ensureBackgroundThread()
    bookmarksManager.awaitUntilInitialized()

    val isWatcherEnabled = ChanSettings.watchEnabled.get()

    val bookmarks = bookmarksManager.mapNotNullBookmarksOrdered { threadBookmarkView ->
      val title = threadBookmarkView.title
        ?: "No title"

      if (searchQuery == null || title.contains(searchQuery, ignoreCase = true)) {
        return@mapNotNullBookmarksOrdered ThreadBookmarkItemView(
          threadDescriptor = threadBookmarkView.threadDescriptor,
          title = title,
          hightlight = threadBookmarkView.threadDescriptor in bookmarksToHighlight,
          thumbnailUrl = threadBookmarkView.thumbnailUrl,
          threadBookmarkStats = ThreadBookmarkStats(
            showBookmarkStats = isWatcherEnabled,
            watching = threadBookmarkView.isWatching(),
            newPosts = max(0, threadBookmarkView.totalPostsCount - threadBookmarkView.seenPostsCount),
            newQuotes = threadBookmarkView.threadBookmarkReplyViews.values.count { reply -> !reply.alreadyRead },
            totalPosts = threadBookmarkView.totalPostsCount,
            isBumpLimit = threadBookmarkView.isBumpLimit(),
            isImageLimit = threadBookmarkView.isImageLimit(),
            isOnLastPage = false,
            isFirstFetch = threadBookmarkView.isFirstFetch()
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