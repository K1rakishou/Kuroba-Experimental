package com.github.adamantcheese.chan.features.bookmarks

import android.content.Context
import android.view.View
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.airbnb.epoxy.EpoxyTouchHelper
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.navigation.RequiresNoBottomNavBar
import com.github.adamantcheese.chan.features.bookmarks.data.BookmarksControllerState
import com.github.adamantcheese.chan.features.bookmarks.epoxy.EpoxyThreadBookmarkView
import com.github.adamantcheese.chan.features.bookmarks.epoxy.EpoxyThreadBookmarkViewModel_
import com.github.adamantcheese.chan.features.bookmarks.epoxy.epoxyThreadBookmarkView
import com.github.adamantcheese.chan.ui.epoxy.epoxyErrorView
import com.github.adamantcheese.chan.ui.epoxy.epoxyLoadingView
import com.github.adamantcheese.chan.ui.epoxy.epoxyTextView
import com.github.adamantcheese.chan.ui.widget.SimpleEpoxySwipeCallbacks
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.AndroidUtils.inflate
import com.github.adamantcheese.chan.utils.Logger
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow

class BookmarksController(context: Context) : Controller(context), BookmarksView, RequiresNoBottomNavBar {
  private lateinit var epoxyRecyclerView: EpoxyRecyclerView

  private val bookmarksPresenter = BookmarksPresenter()
  private val controller = BookmarksEpoxyController()

  override fun onCreate() {
    super.onCreate()

    navigation.title = getString(R.string.controller_bookmarks)
    navigation.swipeable = false

    view = inflate(context, R.layout.controller_bookmarks)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.setController(controller)

    mainScope.launch {
      bookmarksPresenter.listenForStateChanges()
        .asFlow()
        .catch { error ->
          Logger.e(TAG, "Unknown error subscribed to bookmarksPresenter.listenForStateChanges()", error)
        }
        .collect { state -> onStateChanged(state) }
    }

    EpoxyTouchHelper
      .initSwiping(epoxyRecyclerView)
      .right()
      .withTarget(EpoxyThreadBookmarkViewModel_::class.java)
      .andCallbacks(object : SimpleEpoxySwipeCallbacks<EpoxyThreadBookmarkViewModel_>() {
        override fun onSwipeCompleted(
          model: EpoxyThreadBookmarkViewModel_,
          itemView: View?,
          position: Int,
          direction: Int
        ) {
          super.onSwipeCompleted(model, itemView, position, direction)

          bookmarksPresenter.onBookmarkSwipedAway(model.descriptor())
        }
      })

    EpoxyTouchHelper
      .initDragging(controller)
      .withRecyclerView(epoxyRecyclerView)
      .forVerticalList()
      .withTarget(EpoxyThreadBookmarkViewModel_::class.java)
      .andCallbacks(object : EpoxyTouchHelper.DragCallbacks<EpoxyThreadBookmarkViewModel_>() {
        override fun onModelMoved(
          fromPosition: Int,
          toPosition: Int,
          modelBeingMoved: EpoxyThreadBookmarkViewModel_,
          itemView: View?
        ) {
          bookmarksPresenter.onBookmarkMoved(fromPosition, toPosition)
        }
      })

    bookmarksPresenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    bookmarksPresenter.onDestroy()
  }

  private fun onStateChanged(state: BookmarksControllerState) {
    controller.callback = {
      when (state) {
        BookmarksControllerState.Loading -> {
          epoxyLoadingView {
            id("bookmarks_loading_view")
          }
        }
        BookmarksControllerState.Empty -> {
          epoxyTextView {
            id("bookmarks_are_empty_text_view")
            message(context.getString(R.string.bookmarks_are_empty))
          }
        }
        is BookmarksControllerState.Error -> {
          epoxyErrorView {
            id("bookmarks_error_view")
            errorMessage(state.errorText)
          }
        }
        is BookmarksControllerState.Data -> {
          state.bookmarks.forEach { bookmark ->
            val requestData = EpoxyThreadBookmarkView.ImageLoaderRequestData(bookmark.thumbnailUrl)

            epoxyThreadBookmarkView {
              id("thread_bookmark_view_${bookmark.hashCode()}")
              imageLoaderRequestData(requestData)
              descriptor(bookmark.threadDescriptor)
              title(bookmark.title)
              threadBookmarkStats(bookmark.threadBookmarkStats)
              clickListener { bookmarksPresenter.onBookmarkClicked() }
            }
          }
        }
      }
    }

    controller.requestModelBuild()
  }

  private class BookmarksEpoxyController : EpoxyController() {
    var callback: EpoxyController.() -> Unit = {}

    override fun buildModels() {
      callback(this)
    }
  }

  companion object {
    private const val TAG = "BookmarksController"
  }
}