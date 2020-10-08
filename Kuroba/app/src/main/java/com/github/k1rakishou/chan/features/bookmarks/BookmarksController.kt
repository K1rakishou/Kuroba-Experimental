package com.github.k1rakishou.chan.features.bookmarks

import android.content.Context
import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyTouchHelper
import com.github.k1rakishou.chan.Chan.Companion.inject
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.StartActivity
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.DialogFactory
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.settings.state.PersistableChanState
import com.github.k1rakishou.chan.features.bookmarks.data.BookmarksControllerState
import com.github.k1rakishou.chan.features.bookmarks.epoxy.*
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem
import com.github.k1rakishou.chan.ui.view.FastScroller
import com.github.k1rakishou.chan.ui.view.FastScrollerHelper
import com.github.k1rakishou.chan.ui.widget.SimpleEpoxySwipeCallbacks
import com.github.k1rakishou.chan.utils.AndroidUtils.*
import com.github.k1rakishou.chan.utils.addOneshotModelBuildListener
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class BookmarksController(
  context: Context,
  bookmarksToHighlight: List<ChanDescriptor.ThreadDescriptor>
) : Controller(context),
  BookmarksView,
  ToolbarNavigationController.ToolbarSearchCallback {

  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var themeEngine: ThemeEngine

  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView

  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor
  private val bookmarksPresenter = BookmarksPresenter(bookmarksToHighlight.toSet())
  private val controller = BookmarksEpoxyController()
  private val viewModeChanged = AtomicBoolean(false)
  private val needRestoreScrollPosition = AtomicBoolean(true)
  private var isInSearchMode = false
  private var fastScroller: FastScroller? = null

  private val onScrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      if (newState != RecyclerView.SCROLL_STATE_IDLE) {
        return
      }

      onRecyclerViewScrolled(recyclerView)
    }
  }

  override fun onCreate() {
    super.onCreate()

    inject(this)

    navigation.title = getString(R.string.controller_bookmarks)
    navigation.swipeable = false

    serializedCoroutineExecutor = SerializedCoroutineExecutor(mainScope)

    navigation.buildMenu()
      .withItem(R.drawable.ic_search_white_24dp) {
        (navigationController as ToolbarNavigationController).showSearch()
      }
      .withItem(ACTION_CHANGE_VIEW_BOOKMARK_MODE, R.drawable.ic_baseline_view_list_24) {
        PersistableChanState.viewThreadBookmarksGridMode.set(
          PersistableChanState.viewThreadBookmarksGridMode.get().not()
        )

        onViewBookmarksModeChanged()
        updateSwipingAndDragging()

        viewModeChanged.set(true)
        needRestoreScrollPosition.set(true)

        bookmarksPresenter.onViewBookmarksModeChanged()
      }
      .withOverflow(navigationController)
      .withSubItem(
        ACTION_MARK_ALL_BOOKMARKS_AS_SEEN,
        R.string.controller_bookmarks_mark_all_bookmarks_as_seen,
        ToolbarMenuSubItem.ClickCallback { bookmarksPresenter.markAllAsSeen() })
      .withSubItem(
        ACTION_PRUNE_NON_ACTIVE_BOOKMARKS,
        R.string.controller_bookmarks_prune_inactive_bookmarks,
        ToolbarMenuSubItem.ClickCallback { subItem -> onPruneNonActiveBookmarksClicked(subItem) })
      .withSubItem(
        ACTION_CLEAR_ALL_BOOKMARKS,
        R.string.controller_bookmarks_clear_all_bookmarks,
        ToolbarMenuSubItem.ClickCallback { subItem -> onClearAllBookmarksClicked(subItem) }
      )
      .build()
      .build()

    view = inflate(context, R.layout.controller_bookmarks)

    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.setController(controller)

    mainScope.launch {
      bookmarksPresenter.listenForStateChanges()
        .asFlow()
        .collect { state -> onStateChanged(state) }
    }

    updateSwipingAndDragging()
    onViewBookmarksModeChanged()
    updateLayoutManager()

    bookmarksPresenter.onCreate(this)

    setupRecycler()
  }

  override fun onDestroy() {
    super.onDestroy()

    cleanupFastScroller()

    epoxyRecyclerView.removeOnScrollListener(onScrollListener)
    bookmarksPresenter.onDestroy()
  }

  private fun cleanupFastScroller() {
    fastScroller?.let { scroller ->
      epoxyRecyclerView.removeItemDecoration(scroller)
      scroller.onCleanup()
    }

    fastScroller = null
  }

  private fun setupRecycler() {
    epoxyRecyclerView.addOnScrollListener(onScrollListener)
    epoxyRecyclerView.isVerticalScrollBarEnabled = false

    cleanupFastScroller()

    val scroller = FastScrollerHelper.create(
      navigationController!!.requireToolbar().toolbarHeight,
      globalWindowInsetsManager,
      epoxyRecyclerView,
      null,
      themeEngine.chanTheme
    )

    scroller.setThumbDragListener(object : FastScroller.ThumbDragListener {
      override fun onDragStarted() {
        // no-op
      }

      override fun onDragEnded() {
        onRecyclerViewScrolled(epoxyRecyclerView)
      }
    })

    fastScroller = scroller
  }

  private fun onRecyclerViewScrolled(recyclerView: RecyclerView) {
    val firstVisibleItemPosition = when (val layoutManager = recyclerView.layoutManager) {
      is GridLayoutManager -> layoutManager.findFirstVisibleItemPosition()
      is LinearLayoutManager -> layoutManager.findFirstVisibleItemPosition()
      else -> throw IllegalStateException(
        "Unknown layout manager: " +
          "${recyclerView.layoutManager?.javaClass?.simpleName}"
      )
    }

    if (firstVisibleItemPosition == RecyclerView.NO_POSITION) {
      return
    }

    bookmarksPresenter.serializeRecyclerScrollPosition(firstVisibleItemPosition)
  }

  private fun updateSwipingAndDragging() {
    if (PersistableChanState.viewThreadBookmarksGridMode.get()) {
      setupRecyclerSwipingAndDraggingForGridMode()
    } else {
      setupRecyclerSwipingAndDraggingForListMode()
    }
  }

  private fun onClearAllBookmarksClicked(subItem: ToolbarMenuSubItem) {
    if (!bookmarksPresenter.hasBookmarks()) {
      return
    }

    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.controller_bookmarks_clear_all_bookmarks_confirmation_message,
      positiveButtonText = getString(R.string.controller_bookmarks_clear),
      onPositiveButtonClickListener = {
        bookmarksPresenter.clearAllBookmarks()
      }
    )
  }

  private fun onPruneNonActiveBookmarksClicked(subItem: ToolbarMenuSubItem) {
    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.controller_bookmarks_prune_confirmation_message,
      positiveButtonText = getString(R.string.controller_bookmarks_prune),
      onPositiveButtonClickListener = {
        bookmarksPresenter.pruneNonActive()
      }
    )
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

  override fun onSearchVisibilityChanged(visible: Boolean) {
    isInSearchMode = visible
    bookmarksPresenter.onSearchModeChanged(visible)

    if (!visible) {
      needRestoreScrollPosition.set(true)
    }
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
            message(
              context.getString(
                R.string.controller_bookmarks_nothing_found_by_search_query,
                state.searchQuery
              )
            )
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
          addOneshotModelBuildListener {
            if (!isInSearchMode && needRestoreScrollPosition.compareAndSet(true, false)) {
              restoreScrollPosition()
            }
          }

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
                highlightBookmark(bookmark.highlight)
                bookmarkClickListener { onBookmarkClicked(bookmark.threadDescriptor) }
                bookmarkStatsClickListener {
                  bookmarksPresenter.onBookmarkStatsClicked(bookmark.threadDescriptor)
                }
              }
            } else {
              epoxyListThreadBookmarkViewHolder {
                id("thread_list_bookmark_view_${bookmark.hashCode()}")
                context(context)
                imageLoaderRequestData(requestData)
                threadDescriptor(bookmark.threadDescriptor)
                titleString(bookmark.title)
                threadBookmarkStats(bookmark.threadBookmarkStats)
                highlightBookmark(bookmark.highlight)
                bookmarkClickListener { onBookmarkClicked(bookmark.threadDescriptor) }
                bookmarkStatsClickListener {
                  bookmarksPresenter.onBookmarkStatsClicked(bookmark.threadDescriptor)
                }
              }
            }
          }
        }
      }.exhaustive
    }

    controller.requestModelBuild()
  }

  private fun restoreScrollPosition() {
    val scrollPosition = PersistableChanState.bookmarksRecyclerScrollPosition.get()
    if (scrollPosition < 0) {
      return
    }

    when (val layoutManager = epoxyRecyclerView.layoutManager) {
      is GridLayoutManager -> layoutManager.scrollToPositionWithOffset(scrollPosition, 0)
      is LinearLayoutManager -> layoutManager.scrollToPositionWithOffset(scrollPosition, 0)
    }
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
    serializedCoroutineExecutor.post {
      (context as? StartActivity)?.loadThread(threadDescriptor, true)
    }
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
        override fun onDragStarted(
          model: EpoxyListThreadBookmarkViewHolder_?,
          itemView: View?,
          adapterPosition: Int
        ) {
          itemView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        override fun onModelMoved(
          fromPosition: Int,
          toPosition: Int,
          modelBeingMoved: EpoxyListThreadBookmarkViewHolder_,
          itemView: View?
        ) {
          bookmarksPresenter.onBookmarkMoving(fromPosition, toPosition)
        }

        override fun onDragReleased(model: EpoxyListThreadBookmarkViewHolder_?, itemView: View?) {
          bookmarksPresenter.onBookmarkMoved()
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
        override fun onDragStarted(
          model: EpoxyGridThreadBookmarkViewHolder_?,
          itemView: View?,
          adapterPosition: Int
        ) {
          itemView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        override fun onModelMoved(
          fromPosition: Int,
          toPosition: Int,
          modelBeingMoved: EpoxyGridThreadBookmarkViewHolder_,
          itemView: View?
        ) {
          bookmarksPresenter.onBookmarkMoving(fromPosition, toPosition)
        }

        override fun onDragReleased(model: EpoxyGridThreadBookmarkViewHolder_?, itemView: View?) {
          bookmarksPresenter.onBookmarkMoved()
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
    private const val ACTION_CLEAR_ALL_BOOKMARKS = 2002
  }
}