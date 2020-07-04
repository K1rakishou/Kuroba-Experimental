package com.github.adamantcheese.chan.features.bookmarks

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.airbnb.epoxy.EpoxyTouchHelper
import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.StartActivity
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.manager.ApplicationVisibilityManager
import com.github.adamantcheese.chan.core.settings.state.PersistableChanState
import com.github.adamantcheese.chan.features.bookmarks.data.BookmarksControllerState
import com.github.adamantcheese.chan.features.bookmarks.epoxy.*
import com.github.adamantcheese.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.adamantcheese.chan.ui.epoxy.epoxyErrorView
import com.github.adamantcheese.chan.ui.epoxy.epoxyLoadingView
import com.github.adamantcheese.chan.ui.epoxy.epoxyTextView
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuSubItem
import com.github.adamantcheese.chan.ui.widget.SimpleEpoxySwipeCallbacks
import com.github.adamantcheese.chan.utils.AndroidUtils.*
import com.github.adamantcheese.chan.utils.DialogUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.addOneshotModelBuildListener
import com.github.adamantcheese.chan.utils.exhaustive
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class BookmarksController(context: Context)
  : Controller(context),
  BookmarksView,
  ToolbarNavigationController.ToolbarSearchCallback {

  @Inject
  lateinit var applicationVisibilityManager: ApplicationVisibilityManager

  private lateinit var epoxyRecyclerView: EpoxyRecyclerView

  private val bookmarksPresenter = BookmarksPresenter()
  private val controller = BookmarksEpoxyController()
  private val viewModeChanged = AtomicBoolean(false)

  override fun onCreate() {
    super.onCreate()

    inject(this)

    navigation.title = getString(R.string.controller_bookmarks)
    navigation.swipeable = false

    navigation.buildMenu()
      .withItem(R.drawable.ic_search_white_24dp) {
        (navigationController as ToolbarNavigationController).showSearch()
      }
      .withItem(ACTION_CHANGE_VIEW_BOOKMARK_MODE, R.drawable.ic_baseline_view_list_24) {
        PersistableChanState.viewThreadBookmarksGridMode.set(
          PersistableChanState.viewThreadBookmarksGridMode.get().not()
        )

        onViewBookmarksModeChanged()

        viewModeChanged.set(true)
        bookmarksPresenter.onViewBookmarksModeChanged()
      }
      .withOverflow(navigationController)
      .withSubItem(
        ACTION_MARK_ALL_BOOKMARKS_AS_SEEN,
        R.string.controller_bookmarks_mark_all_bookmarks_as_seen,
        object : ToolbarMenuSubItem.ClickCallback {
          override fun clicked(subItem: ToolbarMenuSubItem) {
            onMarkAllBookmarksAsSeenClicked(subItem)
          }
        })
      .withSubItem(
        ACTION_PRUNE_NON_ACTIVE_BOOKMARKS,
        R.string.controller_bookmarks_prune_inactive_bookmarks,
        object : ToolbarMenuSubItem.ClickCallback {
          override fun clicked(subItem: ToolbarMenuSubItem) {
            onPruneNonActiveBookmarksClicked(subItem)
          }
        })
      .build()
      .build()

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

    if (PersistableChanState.viewThreadBookmarksGridMode.get()) {
      setupRecyclerSwipingAndDraggingForGridMode()
    } else {
      setupRecyclerSwipingAndDraggingForListMode()
    }

    onViewBookmarksModeChanged()
    updateLayoutManager()

    bookmarksPresenter.onCreate(this)
  }

  private fun onPruneNonActiveBookmarksClicked(subItem: ToolbarMenuSubItem) {
    DialogUtils.createSimpleConfirmationDialog(
      context,
      applicationVisibilityManager.isAppInForeground(),
      R.string.controller_bookmarks_prune_confirmation_message,
      positiveButtonTextId = R.string.controller_bookmarks_prune,
      onPositiveButtonClickListener = {
        bookmarksPresenter.pruneNonActive()
      }
    )
  }

  private fun onMarkAllBookmarksAsSeenClicked(subItem: ToolbarMenuSubItem) {
    bookmarksPresenter.markAllAsSeen()
  }

  private fun updateLayoutManager(forced: Boolean = false) {
    if (PersistableChanState.viewThreadBookmarksGridMode.get()) {
      if (!forced && epoxyRecyclerView.layoutManager is GridLayoutManager) {
        return
      }

      val gridModeBookmarkWidth =
        context.resources.getDimension(R.dimen.thread_grid_bookmark_view_size).toInt()

      val screenWidth = getDisplaySize().x
      val spanCount = (screenWidth / gridModeBookmarkWidth)
        .coerceIn(MIN_SPAN_COUNT, MAX_SPAN_COUNT)

      epoxyRecyclerView.layoutManager = GridLayoutManager(context, spanCount).apply {
        spanSizeLookup = controller.spanSizeLookup
      }
    } else {
      if (epoxyRecyclerView.layoutManager is LinearLayoutManager
        && epoxyRecyclerView.layoutManager !is GridLayoutManager) {
        return
      }

      epoxyRecyclerView.layoutManager = LinearLayoutManager(context)
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    bookmarksPresenter.onDestroy()
  }

  override fun onSearchVisibilityChanged(visible: Boolean) {
    bookmarksPresenter.onSearchModeChanged(visible)
  }

  override fun onSearchEntered(entered: String?) {
    bookmarksPresenter.onSearchEntered(entered ?: "")
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    updateLayoutManager(forced = true)
  }

  private fun onStateChanged(state: BookmarksControllerState) {
    controller.addOneshotModelBuildListener {
      if (viewModeChanged.compareAndSet(true, false)) {
        updateLayoutManager()
      }
    }

    val isGridMode = PersistableChanState.viewThreadBookmarksGridMode.get()

    controller.callback = {
      when (state) {
        BookmarksControllerState.Loading -> {
          updateTitleWithoutStats()

          epoxyLoadingView {
            id("bookmarks_loading_view")
          }
        }
        BookmarksControllerState.Empty -> {
          updateTitleWithoutStats()

          epoxyTextView {
            id("bookmarks_are_empty_text_view")
            message(context.getString(R.string.controller_bookmarks_bookmarks_are_empty))
          }
        }
        is BookmarksControllerState.NothingFound -> {
          updateTitleWithoutStats()

          epoxyTextView {
            id("bookmarks_nothing_found_by_search_query")
            message(context.getString(R.string.controller_bookmarks_nothing_found_by_search_query, state.searchQuery))
          }
        }
        is BookmarksControllerState.Error -> {
          updateTitleWithoutStats()

          epoxyErrorView {
            id("bookmarks_error_view")
            errorMessage(state.errorText)
          }
        }
        is BookmarksControllerState.Data -> {
          updateTitleWithStats(state)

          state.bookmarks.forEach { bookmark ->
            val requestData =
              BaseThreadBookmarkViewHolder.ImageLoaderRequestData(bookmark.thumbnailUrl)

            if (isGridMode) {
              epoxyGridThreadBookmarkViewHolder {
                id("thread_grid_bookmark_view_${bookmark.hashCode()}")
                context(context)
                imageLoaderRequestData(requestData)
                threadDescriptor(bookmark.threadDescriptor)
                titleString(bookmark.title)
                threadBookmarkStats(bookmark.threadBookmarkStats)
                clickListener { onBookmarkClicked(bookmark.threadDescriptor) }
              }
            } else {
              epoxyListThreadBookmarkViewHolder {
                id("thread_list_bookmark_view_${bookmark.hashCode()}")
                context(context)
                imageLoaderRequestData(requestData)
                threadDescriptor(bookmark.threadDescriptor)
                titleString(bookmark.title)
                threadBookmarkStats(bookmark.threadBookmarkStats)
                clickListener { onBookmarkClicked(bookmark.threadDescriptor) }
              }
            }
          }
        }
      }.exhaustive
    }

    controller.requestModelBuild()
  }

  private fun updateTitleWithoutStats() {
    navigation.title = getString(R.string.controller_bookmarks)
    requireNavController().requireToolbar().updateTitle(navigation)
  }

  private fun updateTitleWithStats(state: BookmarksControllerState.Data) {
    navigation.title = formatTitleWithStats(state)
    requireNavController().requireToolbar().updateTitle(navigation)
  }

  private fun formatTitleWithStats(state: BookmarksControllerState.Data): String {
    val totalBookmarksCount = state.bookmarks.size
    if (totalBookmarksCount <= 0) {
      return context.getString(R.string.controller_bookmarks)
    }

    val watchingBookmarksCount = state.bookmarks.count { threadBookmarkItemView ->
      threadBookmarkItemView.threadBookmarkStats.watching
    }

    return context.getString(
      R.string.controller_bookmarks_with_stats,
      watchingBookmarksCount, totalBookmarksCount
    )
  }

  private fun onBookmarkClicked(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    (context as? StartActivity)?.loadThread(threadDescriptor)
  }

  private fun onViewBookmarksModeChanged() {
    val menuItem = navigation.findItem(ACTION_CHANGE_VIEW_BOOKMARK_MODE)
      ?: return

    val drawableId = when (PersistableChanState.viewThreadBookmarksGridMode.get()) {
      // Should be a reverse of whatever viewThreadBookmarksGridMode currently is because the
      // button's meaning is to switch into that mode, not show the current mode
      false -> R.drawable.ic_baseline_view_comfy_24
      true -> R.drawable.ic_baseline_view_list_24
    }

    menuItem.setImage(drawableId)
  }

  private fun setupRecyclerSwipingAndDraggingForListMode() {
    EpoxyTouchHelper
      .initSwiping(epoxyRecyclerView)
      .right()
      .withTarget(EpoxyListThreadBookmarkViewHolder_::class.java)
      .andCallbacks(object : SimpleEpoxySwipeCallbacks<EpoxyListThreadBookmarkViewHolder_>() {
        override fun onSwipeCompleted(
          model: EpoxyListThreadBookmarkViewHolder_,
          itemView: View?,
          position: Int,
          direction: Int
        ) {
          super.onSwipeCompleted(model, itemView, position, direction)

          val threadDescriptor = model.threadDescriptor()
          if (threadDescriptor != null) {
            bookmarksPresenter.onBookmarkSwipedAway(threadDescriptor)
          }
        }
      })

    EpoxyTouchHelper
      .initDragging(controller)
      .withRecyclerView(epoxyRecyclerView)
      .forVerticalList()
      .withTarget(EpoxyListThreadBookmarkViewHolder_::class.java)
      .andCallbacks(object : EpoxyTouchHelper.DragCallbacks<EpoxyListThreadBookmarkViewHolder_>() {
        override fun onModelMoved(
          fromPosition: Int,
          toPosition: Int,
          modelBeingMoved: EpoxyListThreadBookmarkViewHolder_,
          itemView: View?
        ) {
          bookmarksPresenter.onBookmarkMoved(fromPosition, toPosition)
        }
      })
  }

  private fun setupRecyclerSwipingAndDraggingForGridMode() {
    EpoxyTouchHelper
      .initSwiping(epoxyRecyclerView)
      .right()
      .withTarget(EpoxyGridThreadBookmarkViewHolder_::class.java)
      .andCallbacks(object : SimpleEpoxySwipeCallbacks<EpoxyGridThreadBookmarkViewHolder_>() {
        override fun onSwipeCompleted(
          model: EpoxyGridThreadBookmarkViewHolder_,
          itemView: View?,
          position: Int,
          direction: Int
        ) {
          super.onSwipeCompleted(model, itemView, position, direction)

          val threadDescriptor = model.threadDescriptor()
          if (threadDescriptor != null) {
            bookmarksPresenter.onBookmarkSwipedAway(threadDescriptor)
          }
        }
      })

    EpoxyTouchHelper
      .initDragging(controller)
      .withRecyclerView(epoxyRecyclerView)
      .forGrid()
      .withTarget(EpoxyGridThreadBookmarkViewHolder_::class.java)
      .andCallbacks(object : EpoxyTouchHelper.DragCallbacks<EpoxyGridThreadBookmarkViewHolder_>() {
        override fun onModelMoved(
          fromPosition: Int,
          toPosition: Int,
          modelBeingMoved: EpoxyGridThreadBookmarkViewHolder_,
          itemView: View?
        ) {
          bookmarksPresenter.onBookmarkMoved(fromPosition, toPosition)
        }
      })
  }

  private class BookmarksEpoxyController : EpoxyController() {
    var callback: EpoxyController.() -> Unit = {}

    override fun buildModels() {
      callback(this)
    }
  }

  companion object {
    private const val TAG = "BookmarksController"

    private const val MIN_SPAN_COUNT = 1
    private const val MAX_SPAN_COUNT = 6

    private const val ACTION_CHANGE_VIEW_BOOKMARK_MODE = 1000

    private const val ACTION_PRUNE_NON_ACTIVE_BOOKMARKS = 2000
    private const val ACTION_MARK_ALL_BOOKMARKS_AS_SEEN = 2001
  }
}