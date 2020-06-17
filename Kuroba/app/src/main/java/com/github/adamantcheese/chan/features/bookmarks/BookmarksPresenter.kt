package com.github.adamantcheese.chan.features.bookmarks

import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.core.base.BasePresenter
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.features.bookmarks.data.BookmarksControllerState
import com.github.adamantcheese.chan.features.bookmarks.data.ThreadBookmarkItemView
import com.github.adamantcheese.chan.features.bookmarks.data.ThreadBookmarkStats
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.errorMessageOrClassName
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.max

class BookmarksPresenter : BasePresenter<BookmarksView>() {

  @Inject
  lateinit var bookmarksManager: BookmarksManager

  private val bookmarksControllerStateSubject = PublishProcessor.create<BookmarksControllerState>()
    .toSerialized()

  override fun onCreate(view: BookmarksView) {
    super.onCreate(view)
    inject(this)

    scope.launch {
      // TODO(KurobaEx): without this delay we won't see anything in the recycler. Figure out how
      //  to get rid of it.
      delay(250)

      scope.launch {
        bookmarksManager.listenForBookmarksChanges()
          .asFlow()
          .collect {
            withContext(Dispatchers.Default) {
              ModularResult.Try { showBookmarks() }.safeUnwrap { error ->
                Logger.e(TAG, "showBookmarks() error", error)
                setState(BookmarksControllerState.Error(error.errorMessageOrClassName()))

                return@withContext
              }
            }
          }
      }

      scope.launch(Dispatchers.Default) {
        if (bookmarksManager.bookmarksCount() <= 0) {
          setState(BookmarksControllerState.Empty)
          return@launch
        }

        setState(BookmarksControllerState.Loading)

        ModularResult.Try { showBookmarks() }.safeUnwrap { error ->
          Logger.e(TAG, "showBookmarks() error", error)
          setState(BookmarksControllerState.Error(error.errorMessageOrClassName()))

          return@launch
        }
      }
    }
  }

  fun listenForStateChanges(): Flowable<BookmarksControllerState> {
    return bookmarksControllerStateSubject
      .observeOn(AndroidSchedulers.mainThread())
      .distinctUntilChanged()
      .hide()
  }

  fun onBookmarkSwipedAway(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    bookmarksManager.deleteBookmark(threadDescriptor)
  }

  fun onBookmarkMoved(fromPosition: Int, toPosition: Int) {
    bookmarksManager.onBookmarkMoved(fromPosition, toPosition)
  }

  fun onBookmarkClicked() {
    // TODO(KurobaEx):
    println("TTTAAA bookmark clicked")
  }

  private suspend fun showBookmarks() {
    BackgroundUtils.ensureBackgroundThread()
    bookmarksManager.awaitUntilInitialized()

    val bookmarks = bookmarksManager.mapBookmarksOrdered { threadBookmarkView ->
      ThreadBookmarkItemView(
        threadDescriptor = threadBookmarkView.threadDescriptor,
        title = threadBookmarkView.title ?: "No title",
        thumbnailUrl = threadBookmarkView.thumbnailUrl,
        threadBookmarkStats = ThreadBookmarkStats(
          showBookmarkStats = true,
          watching = true,
          newPosts = max(0, threadBookmarkView.watchNewCount - threadBookmarkView.watchLastCount),
          newQuotes = max(0, threadBookmarkView.quoteNewCount - threadBookmarkView.quoteLastCount),
          totalPosts = threadBookmarkView.watchNewCount,
          isBumpLimit = false,
          isImageLimit = false,
          isLastPage = false
        )
      )
    }

    if (bookmarks.isEmpty()) {
      setState(BookmarksControllerState.Empty)
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