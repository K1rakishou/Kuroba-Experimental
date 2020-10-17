package com.github.k1rakishou.chan.features.bookmarks

import android.content.Context
import android.content.res.Configuration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.chan.Chan.Companion.inject
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.StartActivity
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.DialogFactory
import com.github.k1rakishou.chan.core.settings.state.PersistableChanState
import com.github.k1rakishou.chan.features.bookmarks.data.BookmarksControllerState
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkItemView
import com.github.k1rakishou.chan.features.bookmarks.epoxy.BaseThreadBookmarkViewHolder
import com.github.k1rakishou.chan.features.bookmarks.epoxy.epoxyGridThreadBookmarkViewHolder
import com.github.k1rakishou.chan.features.bookmarks.epoxy.epoxyListThreadBookmarkViewHolder
import com.github.k1rakishou.chan.features.drawer.DrawerCallbacks
import com.github.k1rakishou.chan.ui.controller.floating_menu.FloatingListMenuGravity
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyExpandableGroupView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem
import com.github.k1rakishou.chan.ui.view.FastScroller
import com.github.k1rakishou.chan.ui.view.FastScrollerHelper
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.AndroidUtils.*
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.chan.utils.RecyclerUtils
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
  bookmarksToHighlight: List<ChanDescriptor.ThreadDescriptor>,
  private var drawerCallbacks: DrawerCallbacks?
) : Controller(context),
  BookmarksView,
  ToolbarNavigationController.ToolbarSearchCallback,
  BookmarksSelectionHelper.OnBookmarkMenuItemClicked,
  BookmarksSortingController.SortingOrderChangeListener {

  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var themeEngine: ThemeEngine

  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  private val bookmarksSelectionHelper = BookmarksSelectionHelper(this)
  private val bookmarksPresenter = BookmarksPresenter(bookmarksToHighlight.toSet(), bookmarksSelectionHelper)
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

    navigation.buildMenu(FloatingListMenuGravity.TopRight)
      .withItem(R.drawable.ic_search_white_24dp) {
        (navigationController as ToolbarNavigationController).showSearch()
      }
      .withItem(ACTION_CHANGE_VIEW_BOOKMARK_MODE, R.drawable.ic_baseline_view_list_24) {
        onChangeViewModeClicked()
      }
      .withItem(ACTION_OPEN_SORT_SETTINGS, R.drawable.ic_baseline_sort_24) {
        requireNavController().presentController(BookmarksSortingController(context, this))
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

    onViewBookmarksModeChanged()
    updateLayoutManager()

    bookmarksPresenter.onCreate(this)

    setupRecycler()
  }

  private fun onChangeViewModeClicked() {
    PersistableChanState.viewThreadBookmarksGridMode.set(
      PersistableChanState.viewThreadBookmarksGridMode.get().not()
    )

    onViewBookmarksModeChanged()

    viewModeChanged.set(true)
    needRestoreScrollPosition.set(true)

    bookmarksPresenter.onViewBookmarksModeChanged()
  }

  override fun onDestroy() {
    super.onDestroy()

    cleanupFastScroller()

    requireNavController().requireToolbar().exitSelectionMode()
    drawerCallbacks?.hideBottomPanel()
    drawerCallbacks = null

    epoxyRecyclerView.removeOnScrollListener(onScrollListener)
    bookmarksPresenter.onDestroy()
  }

  override fun onBack(): Boolean {
    val result = drawerCallbacks?.passOnBackToBottomPanel() ?: false
    if (result) {
      bookmarksSelectionHelper.clearSelection()
      requireNavController().requireToolbar().exitSelectionMode()
    }

    return result
  }

  override fun onMenuItemClicked(
    bookmarksMenuItemType: BookmarksSelectionHelper.BookmarksMenuItemType,
    selectedItems: List<ChanDescriptor.ThreadDescriptor>
  ) {
    when (bookmarksMenuItemType) {
      BookmarksSelectionHelper.BookmarksMenuItemType.Delete -> {
        mainScope.launch {
          val deleted = bookmarksPresenter.deleteBookmarks(selectedItems)

          // If deleted == true that means we will use the BookmarkManager's notification mechanisms
          // to redraw bookmarks, otherwise if we couldn't delete bookmarks for some reason we need
          // to cleat the selection checkboxes so we need to use BookmarksSelectionHelper's notification
          // mechanisms
          bookmarksSelectionHelper.clearSelection(!deleted)
          requireNavController().requireToolbar().exitSelectionMode()
        }
      }
    }
  }

  override fun onSortingOrderChanged() {
    Logger.d(TAG, "calling reloadBookmarks() because bookmark sorting order was changed")

    bookmarksPresenter.reloadBookmarks()
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
      epoxyRecyclerView,
      null,
      themeEngine.chanTheme,
      0,
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

  override fun onSearchEntered(entered: String) {
    bookmarksPresenter.onSearchEntered(entered)
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

          val isTablet = AndroidUtils.isTablet()
          updateTitleWithStats(state)

          state.groupedBookmarks.forEach { bookmarkGroup ->
            val hasBookmarksInGroup = bookmarkGroup.threadBookmarkItemViews.isNotEmpty()

            if (!hasBookmarksInGroup) {
              return@forEach
            }

            epoxyExpandableGroupView {
              id("bookmark_group_toggle_${bookmarkGroup.groupId}")
              isExpanded(bookmarkGroup.isExpanded)
              groupTitle(bookmarkGroup.groupInfoText)
              clickListener { bookmarksPresenter.toggleBookmarkExpandState(bookmarkGroup.groupId) }
            }

            if (!bookmarkGroup.isExpanded) {
              return@forEach
            }

            bookmarkGroup.threadBookmarkItemViews.forEach { bookmark ->
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
                  threadBookmarkSelection(bookmark.selection)
                  highlightBookmark(bookmark.highlight)
                  isTablet(isTablet)
                  bookmarkClickListener { onBookmarkClicked(bookmark.threadDescriptor) }
                  bookmarkLongClickListener { onBookmarkLongClicked(bookmark) }
                  bookmarkStatsClickListener { onBookmarkStatsClicked(bookmark) }
                }
              } else {
                epoxyListThreadBookmarkViewHolder {
                  id("thread_list_bookmark_view_${bookmark.hashCode()}")
                  context(context)
                  imageLoaderRequestData(requestData)
                  threadDescriptor(bookmark.threadDescriptor)
                  titleString(bookmark.title)
                  threadBookmarkStats(bookmark.threadBookmarkStats)
                  threadBookmarkSelection(bookmark.selection)
                  highlightBookmark(bookmark.highlight)
                  isTablet(isTablet)
                  bookmarkClickListener { onBookmarkClicked(bookmark.threadDescriptor) }
                  bookmarkLongClickListener { onBookmarkLongClicked(bookmark) }
                  bookmarkStatsClickListener { onBookmarkStatsClicked(bookmark) }
                }
              }
            }
          }
        }
      }.exhaustive
    }

    controller.requestModelBuild()
  }

  private fun onBookmarkStatsClicked(bookmark: ThreadBookmarkItemView) {
    if (bookmarksSelectionHelper.isInSelectionMode()) {
      onBookmarkClicked(bookmark.threadDescriptor)
      return
    }

    bookmarksPresenter.onBookmarkStatsClicked(bookmark.threadDescriptor)
  }

  private fun onBookmarkLongClicked(bookmark: ThreadBookmarkItemView) {
    if (bookmarksPresenter.isInSearchMode()) {
      return
    }

    bookmarksSelectionHelper.toggleSelection(bookmark.threadDescriptor)

    if (bookmarksSelectionHelper.isInSelectionMode()) {
      drawerCallbacks?.showBottomPanel(bookmarksSelectionHelper.getBottomPanelMenus())
      enterSelectionModeOrUpdate()
    } else {
      drawerCallbacks?.hideBottomPanel()
      requireNavController().requireToolbar().exitSelectionMode()
    }
  }

  private fun onBookmarkClicked(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (bookmarksSelectionHelper.isInSelectionMode()) {
      if (bookmarksPresenter.isInSearchMode()) {
        return
      }

      bookmarksSelectionHelper.toggleSelection(threadDescriptor)

      if (bookmarksSelectionHelper.isInSelectionMode()) {
        // If still in selection mode after toggling this one item, update the bottom panel
        drawerCallbacks?.showBottomPanel(bookmarksSelectionHelper.getBottomPanelMenus())
        enterSelectionModeOrUpdate()
      } else {
        drawerCallbacks?.hideBottomPanel()
        requireNavController().requireToolbar().exitSelectionMode()
      }

      return
    }

    serializedCoroutineExecutor.post {
      (context as? StartActivity)?.loadThread(threadDescriptor, true)
    }
  }

  private fun onRecyclerViewScrolled(recyclerView: RecyclerView) {
    val isGridLayoutManager = when (recyclerView.layoutManager) {
      is GridLayoutManager -> true
      is LinearLayoutManager -> false
      else -> throw IllegalStateException("Unknown layout manager: " +
        "${recyclerView.layoutManager?.javaClass?.simpleName}"
      )
    }

    PersistableChanState.storeBookmarksRecyclerIndexAndTopInfo(
      isGridLayoutManager,
      RecyclerUtils.getIndexAndTop(recyclerView)
    )
  }

  private fun restoreScrollPosition() {
    val isForGridLayoutManager = when (epoxyRecyclerView.layoutManager) {
      is GridLayoutManager -> true
      is LinearLayoutManager -> false
      else -> throw IllegalStateException("Unknown layout manager: " +
        "${epoxyRecyclerView.layoutManager?.javaClass?.simpleName}"
      )
    }

    val indexAndTop = PersistableChanState.getBookmarksRecyclerIndexAndTopInfo(isForGridLayoutManager)

    when (val layoutManager = epoxyRecyclerView.layoutManager) {
      is GridLayoutManager -> layoutManager.scrollToPositionWithOffset(indexAndTop.index, indexAndTop.top)
      is LinearLayoutManager -> layoutManager.scrollToPositionWithOffset(indexAndTop.index, indexAndTop.top)
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
    val groupedBookmarks = state.groupedBookmarks

    val totalBookmarksCount = groupedBookmarks.sumBy { group -> group.threadBookmarkItemViews.size }
    if (totalBookmarksCount <= 0) {
      return context.getString(R.string.controller_bookmarks)
    }

    val watchingBookmarksCount = groupedBookmarks.sumBy { group ->
      group.threadBookmarkItemViews.count { threadBookmarkItemView ->
        threadBookmarkItemView.threadBookmarkStats.watching
      }
    }

    return context.getString(
      R.string.controller_bookmarks_with_stats,
      watchingBookmarksCount, totalBookmarksCount
    )
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

  private fun enterSelectionModeOrUpdate() {
    val navController = requireNavController()
    val toolbar = navController.requireToolbar()

    if (!toolbar.isInSelectionMode) {
      toolbar.enterSelectionMode(formatSelectionText())
      return
    }

    navigation.selectionStateText = formatSelectionText()
    toolbar.updateSelectionTitle(navController.navigation)
  }

  private fun formatSelectionText(): String {
    require(bookmarksSelectionHelper.isInSelectionMode()) { "Not in selection mode" }

    return "Selected ${bookmarksSelectionHelper.selectedItemsCount()} bookmarks"
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
    private const val ACTION_OPEN_SORT_SETTINGS = 1001

    private const val ACTION_PRUNE_NON_ACTIVE_BOOKMARKS = 2000
    private const val ACTION_MARK_ALL_BOOKMARKS_AS_SEEN = 2001
    private const val ACTION_CLEAR_ALL_BOOKMARKS = 2002
  }
}