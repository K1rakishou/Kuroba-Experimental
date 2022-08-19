package com.github.k1rakishou.chan.features.bookmarks

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyModel
import com.airbnb.epoxy.EpoxyModelTouchCallback
import com.airbnb.epoxy.EpoxyRecyclerView
import com.airbnb.epoxy.EpoxyViewHolder
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseSelectionHelper
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.core.watcher.BookmarkForegroundWatcher
import com.github.k1rakishou.chan.core.watcher.FilterWatcherCoordinator
import com.github.k1rakishou.chan.features.bookmarks.data.BookmarksControllerState
import com.github.k1rakishou.chan.features.bookmarks.data.GroupOfThreadBookmarkItemViews
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkItemView
import com.github.k1rakishou.chan.features.bookmarks.epoxy.BaseThreadBookmarkViewHolder
import com.github.k1rakishou.chan.features.bookmarks.epoxy.EpoxyGridThreadBookmarkViewHolder_
import com.github.k1rakishou.chan.features.bookmarks.epoxy.EpoxyListThreadBookmarkViewHolder_
import com.github.k1rakishou.chan.features.bookmarks.epoxy.UnifiedBookmarkInfoAccessor
import com.github.k1rakishou.chan.features.bookmarks.epoxy.epoxyGridThreadBookmarkViewHolder
import com.github.k1rakishou.chan.features.bookmarks.epoxy.epoxyListThreadBookmarkViewHolder
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloaderSettingsController
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.navigation.TabPageController
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.controller.settings.RangeSettingUpdaterController
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyExpandableGroupView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem
import com.github.k1rakishou.chan.ui.view.FastScroller
import com.github.k1rakishou.chan.ui.view.FastScrollerHelper
import com.github.k1rakishou.chan.ui.widget.KurobaSwipeRefreshLayout
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet
import com.github.k1rakishou.chan.utils.RecyclerUtils
import com.github.k1rakishou.chan.utils.addOneshotModelBuildListener
import com.github.k1rakishou.common.AndroidUtils.getDisplaySize
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.persist_state.PersistableChanState
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class BookmarksController(
  context: Context,
  private val bookmarksToHighlight: List<ChanDescriptor.ThreadDescriptor>,
  private val mainControllerCallbacks: MainControllerCallbacks,
  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks
) : TabPageController(context),
  BookmarksView,
  ToolbarNavigationController.ToolbarSearchCallback,
  BookmarksSelectionHelper.OnBookmarkMenuItemClicked,
  WindowInsetsListener {

  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var threadBookmarkGroupManager: ThreadBookmarkGroupManager
  @Inject
  lateinit var pageRequestManager: PageRequestManager
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var bookmarkForegroundWatcher: BookmarkForegroundWatcher
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var threadDownloadManager: ThreadDownloadManager
  @Inject
  lateinit var chanThreadManager: ChanThreadManager
  @Inject
  lateinit var filterWatcherCoordinator: FilterWatcherCoordinator

  private lateinit var epoxyRecyclerView: EpoxyRecyclerView
  private lateinit var swipeRefreshLayout: KurobaSwipeRefreshLayout
  private lateinit var itemTouchHelper: ItemTouchHelper

  private val bookmarksSelectionHelper = BookmarksSelectionHelper(this)

  private val bookmarksPresenter by lazy {
    BookmarksPresenter(
      bookmarksToHighlight.toSet(),
      bookmarksManager,
      threadBookmarkGroupManager,
      pageRequestManager,
      archivesManager,
      bookmarksSelectionHelper,
      threadDownloadManager
    )
  }

  private val controller = BookmarksEpoxyController()
  private val viewModeChanged = AtomicBoolean(false)
  private val needRestoreScrollPosition = AtomicBoolean(true)
  private val needScrollToHighlightedBookmark = AtomicBoolean(bookmarksToHighlight.isNotEmpty())
  private var isInSearchMode = false
  private var fastScroller: FastScroller? = null

  private val touchHelperCallback = object : EpoxyModelTouchCallback<EpoxyModel<*>>(controller, EpoxyModel::class.java) {

    override fun isLongPressDragEnabled(): Boolean = false
    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlagsForModel(model: EpoxyModel<*>?, adapterPosition: Int): Int {
      if (model !is EpoxyGridThreadBookmarkViewHolder_ && model !is EpoxyListThreadBookmarkViewHolder_) {
        return makeMovementFlags(0, 0)
      }

      val moveFlags = if (PersistableChanState.viewThreadBookmarksGridMode.get()) {
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
      } else {
        ItemTouchHelper.UP or ItemTouchHelper.DOWN
      }

      return makeMovementFlags(moveFlags, 0)
    }

    override fun canDropOver(
      recyclerView: RecyclerView,
      current: EpoxyViewHolder,
      target: EpoxyViewHolder
    ): Boolean {
      val currentUnifiedBookmarkInfoAccessor = (current.model as? UnifiedBookmarkInfoAccessor)
        ?: return false
      val targetUnifiedBookmarkInfoAccessor = (target.model as? UnifiedBookmarkInfoAccessor)
        ?: return false

      if (currentUnifiedBookmarkInfoAccessor.getBookmarkGroupId() !=
        targetUnifiedBookmarkInfoAccessor.getBookmarkGroupId()) {
        return false
      }

      if (ChanSettings.moveNotActiveBookmarksToBottom.get()) {
        val isDeadOrNotWatching = targetUnifiedBookmarkInfoAccessor.getBookmarkStats()?.isDeadOrNotWatching()
          ?: false

        if (isDeadOrNotWatching) {
          // If moveNotActiveBookmarksToBottom setting is active and the target bookmark is
          // "isDeadOrNotWatching" don't allow dropping the current bookmark over the target.
          return false
        }
      }

      if (ChanSettings.moveBookmarksWithUnreadRepliesToTop.get()) {
        val newQuotes = targetUnifiedBookmarkInfoAccessor.getBookmarkStats()?.newQuotes ?: 0

        if (newQuotes > 0) {
          // If moveBookmarksWithUnreadRepliesToTop setting is active and the target bookmark has
          // unread quotes don't allow dropping the current bookmark over the target.
          return false
        }
      }

      return true
    }

    override fun onDragStarted(model: EpoxyModel<*>?, itemView: View?, adapterPosition: Int) {
      itemView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: EpoxyViewHolder, target: EpoxyViewHolder): Boolean {
      val fromPosition = viewHolder.adapterPosition
      val toPosition = target.adapterPosition

      val fromGroupId = (viewHolder.model as? UnifiedBookmarkInfoAccessor)?.getBookmarkGroupId()
      val toGroupId = (target.model as? UnifiedBookmarkInfoAccessor)?.getBookmarkGroupId()

      if (fromGroupId == null || toGroupId == null || fromGroupId != toGroupId) {
        return false
      }

      val fromBookmarkDescriptor = (viewHolder.model as? UnifiedBookmarkInfoAccessor)?.getBookmarkDescriptor()
      val toBookmarkDescriptor = (target.model as? UnifiedBookmarkInfoAccessor)?.getBookmarkDescriptor()

      if (fromBookmarkDescriptor == null || toBookmarkDescriptor == null) {
        return false
      }

      val result = bookmarksPresenter.onBookmarkMoving(
        fromGroupId,
        fromBookmarkDescriptor,
        toBookmarkDescriptor
      )

      if (!result) {
        return false
      }

      controller.moveModel(fromPosition, toPosition)

      return true
    }

    override fun onDragReleased(model: EpoxyModel<*>?, itemView: View?) {
      val groupId = when (PersistableChanState.viewThreadBookmarksGridMode.get()) {
        true -> (model as? EpoxyGridThreadBookmarkViewHolder_)?.groupId()
        false -> (model as? EpoxyListThreadBookmarkViewHolder_)?.groupId()
      }

      if (groupId == null) {
        return
      }

      bookmarksPresenter.onBookmarkMoved(groupId)
    }

  }

  private val onScrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      if (newState != RecyclerView.SCROLL_STATE_IDLE) {
        return
      }

      onRecyclerViewScrolled(recyclerView)
    }
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    view = inflate(context, R.layout.controller_bookmarks)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    swipeRefreshLayout = view.findViewById(R.id.boomarks_swipe_refresh_layout)

    swipeRefreshLayout.setOnChildScrollUpCallback { parent, child ->
      val isDragging = fastScroller?.isDragging ?: false
      if (isDragging) {
        // Disable SwipeRefresh layout when dragging the fast scroller
        return@setOnChildScrollUpCallback true
      }

      return@setOnChildScrollUpCallback epoxyRecyclerView.canScrollVertically(-1)
    }

    swipeRefreshLayout.setOnRefreshListener {
      // The process of reloading bookmarks may not notify us about the results when none of the
      // bookmarks were changed during the update so we need to have this timeout mechanism in
      // such case.
      mainScope.launch {
        bookmarkForegroundWatcher.restartWatching()

        delay(10_000)
        swipeRefreshLayout.isRefreshing = false
      }
    }

    epoxyRecyclerView.setController(controller)

    itemTouchHelper = ItemTouchHelper(touchHelperCallback)
    itemTouchHelper.attachToRecyclerView(epoxyRecyclerView)

    mainScope.launch {
      bookmarksPresenter.listenForStateChanges()
        .asFlow()
        .collect { state -> onStateChanged(state) }
    }

    mainScope.launch {
      bookmarksSelectionHelper.listenForSelectionChanges()
        .collect { selectionEvent -> onNewSelectionEvent(selectionEvent) }
    }

    mainControllerCallbacks.onBottomPanelStateChanged { state -> onInsetsChanged() }

    onViewBookmarksModeChanged()
    updateLayoutManager()

    bookmarksPresenter.onCreate(this)
    onInsetsChanged()
    setupRecycler()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    cleanupFastScroller()

    bookmarksPresenter.updateReorderingMode(enterReorderingMode = false)
    requireNavController().requireToolbar().exitSelectionMode()
    mainControllerCallbacks.hideBottomPanel()

    epoxyRecyclerView.clear()
    epoxyRecyclerView.removeOnScrollListener(onScrollListener)
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)

    bookmarksPresenter.onDestroy()
  }

  override fun onBack(): Boolean {
    if (bookmarksPresenter.isInReorderingMode()) {
      if (bookmarksPresenter.updateReorderingMode(enterReorderingMode = false)) {
        return true
      }
    }

    val result = mainControllerCallbacks.passOnBackToBottomPanel() ?: false
    if (result) {
      bookmarksSelectionHelper.clearSelection()
    }

    return result
  }

  override fun onInsetsChanged() {
    val bottomPaddingDp = calculateBottomPaddingForRecyclerInDp(
      globalWindowInsetsManager = globalWindowInsetsManager,
      mainControllerCallbacks = mainControllerCallbacks
    )

    epoxyRecyclerView.updatePaddings(bottom = dp(bottomPaddingDp.toFloat()))
  }

  override fun rebuildNavigationItem(navigationItem: NavigationItem) {
    navigationItem.title = getString(R.string.controller_bookmarks)
    navigationItem.swipeable = false
    navigationItem.hasDrawer = true
    navigationItem.hasBack = false

    navigationItem.buildMenu(context)
      .withMenuItemClickInterceptor {
        exitReorderingModeIfActive()
        return@withMenuItemClickInterceptor false
      }
      .withItem(R.drawable.ic_search_white_24dp) { requireToolbarNavController().showSearch() }
      .withItem(ACTION_CHANGE_VIEW_BOOKMARK_MODE, getBookmarksModeChangeToolbarButtonDrawableId()) {
        onChangeViewModeClicked()
      }
      .withItem(ACTION_OPEN_SORT_SETTINGS, R.drawable.ic_baseline_sort_24) {
        requireNavController().presentController(BookmarksSortingController(context, this))
      }
      .withOverflow(requireNavController())
      .withSubItem(
        ACTION_RESTART_FILTER_WATCHER,
        R.string.controller_bookmarks_restart_filter_watcher,
        { restartFilterWatcherClicked() }
      )
      .withSubItem(
        ACTION_MARK_ALL_BOOKMARKS_AS_SEEN,
        R.string.controller_bookmarks_mark_all_bookmarks_as_seen,
        { bookmarksPresenter.markAllAsSeen() })
      .withSubItem(
        ACTION_PRUNE_NON_ACTIVE_BOOKMARKS,
        R.string.controller_bookmarks_prune_inactive_bookmarks,
        { subItem -> onPruneNonActiveBookmarksClicked(subItem) }
      )
      .withSubItem(
        ACTION_BOOKMARK_GROUPS_SETTINGS,
        R.string.controller_bookmarks_bookmark_groups_settings,
        { bookmarkGroupsSettings() }
      )
      .withSubItem(
        ACTION_SET_GRID_BOOKMARK_VIEW_WIDTH,
        R.string.controller_bookmarks_set_grid_bookmark_view_width,
        PersistableChanState.viewThreadBookmarksGridMode.get(),
        { onSetGridBookmarkViewWidthClicked() }
      )
      .withSubItem(
        ACTION_CLEAR_ALL_BOOKMARKS,
        R.string.controller_bookmarks_clear_all_bookmarks,
        { subItem -> onClearAllBookmarksClicked(subItem) }
      )
      .build()
      .build()
  }

  override fun onTabFocused() {
    reloadBookmarks()
  }

  override fun canSwitchTabs(): Boolean {
    if (bookmarksSelectionHelper.isInSelectionMode() || bookmarksPresenter.isInReorderingMode()) {
      return false
    }

    return true
  }

  override fun onMenuItemClicked(
    bookmarksMenuItemType: BookmarksSelectionHelper.BookmarksMenuItemType,
    selectedItems: List<ChanDescriptor.ThreadDescriptor>
  ) {
    when (bookmarksMenuItemType) {
      BookmarksSelectionHelper.BookmarksMenuItemType.Delete -> {
        val bookmarksCount = selectedItems.size

        dialogFactory.createSimpleConfirmationDialog(
          context = context,
          titleText = getString(R.string.controller_bookmarks_delete_selected_bookmarks_title, bookmarksCount),
          negativeButtonText = getString(R.string.do_not),
          positiveButtonText = getString(R.string.delete),
          onPositiveButtonClickListener = {
            bookmarksPresenter.deleteBookmarks(selectedItems)
          }
        )
      }
      BookmarksSelectionHelper.BookmarksMenuItemType.Reorder -> {
        if (bookmarksPresenter.isInReorderingMode()) {
          bookmarksPresenter.updateReorderingMode(enterReorderingMode = false)
        } else {
          bookmarksPresenter.updateReorderingMode(enterReorderingMode = true)
        }
      }
      BookmarksSelectionHelper.BookmarksMenuItemType.MoveToGroup -> {
        // Forcefully exit the selection mode because otherwise the toolbar will get stuck
        // in selection mode
        onNewSelectionEvent(BaseSelectionHelper.SelectionEvent.ExitedSelectionMode)

        val controller = BookmarkGroupSettingsController(
          context = context,
          bookmarksToMove = selectedItems,
          refreshBookmarksFunc = { bookmarksPresenter.reloadBookmarks() }
        )
        requireNavController().pushController(controller)
      }
      BookmarksSelectionHelper.BookmarksMenuItemType.Download -> {
        val threadDownloaderSettingsController = ThreadDownloaderSettingsController(
          context = context,
          downloadClicked = { downloadMedia ->
            onDownloadThreadsClicked(downloadMedia, selectedItems)
          }
        )

        presentController(threadDownloaderSettingsController, animated = true)
      }
      BookmarksSelectionHelper.BookmarksMenuItemType.Read -> {
        bookmarksPresenter.markAsRead(selectedItems)
      }
    }

    bookmarksSelectionHelper.clearSelection()
  }

  private fun onDownloadThreadsClicked(
    downloadMedia: Boolean,
    threadDescriptors: List<ChanDescriptor.ThreadDescriptor>
  ) {
    if (threadDescriptors.isEmpty()) {
      return
    }

    mainScope.launch {
      coroutineScope {
        if (threadDescriptors.size > 32) {
          // So it doesn't appear "stuck"
          val loadingController = LoadingViewController(context, true)
          presentController(loadingController)

          coroutineContext[Job.Key]?.invokeOnCompletion { loadingController.stopPresenting() }
        }

        threadDescriptors.forEach { threadDescriptor ->
          var threadThumbnailUrl = chanThreadManager.getChanThread(threadDescriptor)
            ?.getOriginalPost()
            ?.firstImage()
            ?.actualThumbnailUrl

          if (threadThumbnailUrl == null) {
            threadThumbnailUrl = bookmarksManager.getBookmarkThumbnailByThreadId(threadDescriptor)
          }

          threadDownloadManager.startDownloading(
            threadDescriptor = threadDescriptor,
            threadThumbnailUrl = threadThumbnailUrl?.toString(),
            downloadMedia = downloadMedia
          )
        }
      }
    }
  }

  override fun reloadBookmarks() {
    Logger.d(TAG, "Calling reloadBookmarks() because bookmark sorting order was changed")

    needRestoreScrollPosition.set(true)
    bookmarksPresenter.reloadBookmarks()
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

  private fun reloadBookmarksAndUpdateViewMode() {
    Logger.d(TAG, "Calling reloadBookmarks() because grid bookmark view width was changed")

    updateLayoutManager(forced = true)
    needRestoreScrollPosition.set(true)

    bookmarksPresenter.reloadBookmarks()
  }

  private fun onNewSelectionEvent(selectionEvent: BaseSelectionHelper.SelectionEvent) {
    when (selectionEvent) {
      BaseSelectionHelper.SelectionEvent.EnteredSelectionMode,
      BaseSelectionHelper.SelectionEvent.ItemSelectionToggled -> {
        if (selectionEvent is BaseSelectionHelper.SelectionEvent.EnteredSelectionMode) {
          mainControllerCallbacks.showBottomPanel(bookmarksSelectionHelper.getBottomPanelMenus())
        }

        enterSelectionModeOrUpdate()
      }
      BaseSelectionHelper.SelectionEvent.ExitedSelectionMode -> {
        mainControllerCallbacks.hideBottomPanel()
        requireNavController().requireToolbar().exitSelectionMode()
      }
    }
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

  private fun cleanupFastScroller() {
    fastScroller?.let { scroller ->
      epoxyRecyclerView.removeItemDecoration(scroller)
      scroller.onCleanup()
    }

    fastScroller = null
  }

  private fun setupRecycler() {
    epoxyRecyclerView.addOnScrollListener(onScrollListener)

    if (ChanSettings.draggableScrollbars.get().isEnabled) {
      epoxyRecyclerView.isVerticalScrollBarEnabled = false
      cleanupFastScroller()

      val scroller = FastScrollerHelper.create(
        FastScroller.FastScrollerControllerType.Bookmarks,
        epoxyRecyclerView,
        null
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
    } else {
      epoxyRecyclerView.isVerticalScrollBarEnabled = true
      cleanupFastScroller()
    }
  }

  private fun bookmarkGroupsSettings() {
    val controller = BookmarkGroupSettingsController(
      context = context,
      refreshBookmarksFunc = { bookmarksPresenter.reloadBookmarks() }
    )

    requireNavController().pushController(controller)
  }

  private fun restartFilterWatcherClicked() {
    filterWatcherCoordinator.restartFilterWatcherWithTinyDelay(isCalledBySwipeToRefresh = true)
    showToast(R.string.controller_bookmarks_filter_watcher_restarted)
  }

  private fun onSetGridBookmarkViewWidthClicked() {
    val rangeSettingUpdaterController = RangeSettingUpdaterController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      title = getString(R.string.controller_bookmarks_set_grid_view_width_text),
      minValue = context.resources.getDimension(R.dimen.thread_grid_bookmark_view_min_width).toInt(),
      maxValue = context.resources.getDimension(R.dimen.thread_grid_bookmark_view_max_width).toInt(),
      currentValue = ChanSettings.bookmarkGridViewWidth.get(),
      resetClickedFunc = {
        ChanSettings.bookmarkGridViewWidth.set(ChanSettings.bookmarkGridViewWidth.default)
      },
      applyClickedFunc = { newValue ->
        val currentValue = ChanSettings.bookmarkGridViewWidth.get()
        if (currentValue != newValue) {
          ChanSettings.bookmarkGridViewWidth.set(newValue)
          reloadBookmarksAndUpdateViewMode()
        }
      }
    )

    requireNavController().presentController(rangeSettingUpdaterController)
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

      val bookmarkWidth = ChanSettings.bookmarkGridViewWidth.get()
      val screenWidth = getDisplaySize(context).x
      val spanCount = (screenWidth / bookmarkWidth).coerceIn(MIN_SPAN_COUNT, MAX_SPAN_COUNT)

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

  private fun onStateChanged(state: BookmarksControllerState) {
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
          swipeRefreshLayout.isRefreshing = false

          epoxyTextView {
            id("bookmarks_are_empty_text_view")
            message(context.getString(R.string.controller_bookmarks_bookmarks_are_empty))
          }
        }
        is BookmarksControllerState.NothingFound -> {
          updateTitleWithoutStats()
          swipeRefreshLayout.isRefreshing = false

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
          swipeRefreshLayout.isRefreshing = false
          updateTitleWithoutStats()

          epoxyErrorView {
            id("bookmarks_error_view")
            errorMessage(state.errorText)
          }
        }
        is BookmarksControllerState.Data -> {
          swipeRefreshLayout.isRefreshing = false

          addOneshotModelBuildListener {
            if (viewModeChanged.compareAndSet(true, false)) {
              updateLayoutManager()
            }

            if (!isInSearchMode) {
              val restoreScrollPosition = needRestoreScrollPosition.compareAndSet(true, false)
              val scrollToHighlighted = needScrollToHighlightedBookmark.compareAndSet(true, false)

              when {
                restoreScrollPosition && !scrollToHighlighted -> restoreScrollPosition()
                scrollToHighlighted -> scrollToHighlighted()
              }
            }
          }

          val isTablet = isTablet()
          updateTitleWithStats(state)

          state.groupedBookmarks.forEach { bookmarkGroup ->
            if (bookmarkGroup.threadBookmarkItemViews.isEmpty()) {
              return@forEach
            }

            epoxyExpandableGroupView {
              id("bookmark_group_toggle_${bookmarkGroup.groupId}")
              isExpanded(bookmarkGroup.isExpanded)
              groupTitle(bookmarkGroup.groupInfoText)
              clickListener { onGroupViewClicked(bookmarkGroup) }
            }

            if (!bookmarkGroup.isExpanded) {
              return@forEach
            }

            bookmarkGroup.threadBookmarkItemViews.forEach { bookmark ->
              val requestData =
                BaseThreadBookmarkViewHolder.ImageLoaderRequestData(bookmark.thumbnailUrl)

              if (isGridMode) {
                epoxyGridThreadBookmarkViewHolder {
                  id("thread_grid_bookmark_view_${bookmark.threadDescriptor.serializeToString()}")
                  context(context)
                  imageLoaderRequestData(requestData)
                  threadDescriptor(bookmark.threadDescriptor)
                  titleString(bookmark.title)
                  threadBookmarkStats(bookmark.threadBookmarkStats)
                  threadBookmarkSelection(bookmark.selection)
                  highlightBookmark(bookmark.highlight)
                  isTablet(isTablet)
                  groupId(bookmarkGroup.groupId)
                  reorderingMode(state.isReorderingMode)
                  bookmarkClickListener { onBookmarkClicked(bookmark.threadDescriptor) }
                  bookmarkLongClickListener { onBookmarkLongClicked(bookmark) }
                  bookmarkStatsClickListener { onBookmarkStatsClicked(bookmark) }
                }
              } else {
                epoxyListThreadBookmarkViewHolder {
                  id("thread_list_bookmark_view_${bookmark.threadDescriptor.serializeToString()}")
                  context(context)
                  imageLoaderRequestData(requestData)
                  threadDescriptor(bookmark.threadDescriptor)
                  titleString(bookmark.title)
                  threadBookmarkStats(bookmark.threadBookmarkStats)
                  threadBookmarkSelection(bookmark.selection)
                  highlightBookmark(bookmark.highlight)
                  isTablet(isTablet)
                  groupId(bookmarkGroup.groupId)
                  reorderingMode(state.isReorderingMode)
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

  private fun exitReorderingModeIfActive() {
    if (bookmarksPresenter.isInReorderingMode()) {
      bookmarksPresenter.updateReorderingMode(enterReorderingMode = false)
    }
  }

  private fun onBookmarkStatsClicked(bookmark: ThreadBookmarkItemView) {
    if (bookmarksPresenter.isInReorderingMode()) {
      exitReorderingModeIfActive()
      return
    }

    if (bookmarksSelectionHelper.isInSelectionMode()) {
      onBookmarkClicked(bookmark.threadDescriptor)
      return
    }

    bookmarksPresenter.onBookmarkStatsClicked(bookmark.threadDescriptor)
  }

  private fun onGroupViewClicked(bookmarkGroup: GroupOfThreadBookmarkItemViews) {
    if (bookmarksPresenter.isInReorderingMode()) {
      exitReorderingModeIfActive()
      return
    }

    if (!bookmarksSelectionHelper.isInSelectionMode()) {
      bookmarksPresenter.toggleBookmarkExpandState(bookmarkGroup.groupId)
      return
    }

    if (bookmarksPresenter.isInSearchMode()) {
      return
    }

    if (!bookmarkGroup.isExpanded) {
      bookmarksPresenter.toggleBookmarkExpandState(bookmarkGroup.groupId)
      return
    }

    val bookmarkDescriptors = bookmarkGroup.threadBookmarkItemViews
      .map { threadBookmarkItemView -> threadBookmarkItemView.threadDescriptor }

    bookmarksSelectionHelper.toggleSelection(bookmarkDescriptors)
  }

  private fun onBookmarkLongClicked(bookmark: ThreadBookmarkItemView) {
    if (bookmarksPresenter.isInReorderingMode()) {
      exitReorderingModeIfActive()
      return
    }

    if (bookmarksPresenter.isInSearchMode()) {
      return
    }

    bookmarksSelectionHelper.toggleSelection(bookmark.threadDescriptor)
  }

  private fun onBookmarkClicked(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (bookmarksPresenter.isInReorderingMode()) {
      exitReorderingModeIfActive()
      return
    }

    if (bookmarksSelectionHelper.isInSelectionMode()) {
      if (bookmarksPresenter.isInSearchMode()) {
        return
      }

      bookmarksSelectionHelper.toggleSelection(threadDescriptor)
      return
    }

    startActivityCallback.loadThread(threadDescriptor, animated = true)
  }

  private fun onRecyclerViewScrolled(recyclerView: RecyclerView) {
    val isGridLayoutManager = when (recyclerView.layoutManager) {
      is GridLayoutManager -> true
      is LinearLayoutManager -> false
      else -> throw IllegalStateException("Unknown layout manager: " +
        "${recyclerView.layoutManager?.javaClass?.simpleName}"
      )
    }

    PersistableChanState.storeRecyclerIndexAndTopInfo(
      PersistableChanState.bookmarksRecyclerIndexAndTop,
      isGridLayoutManager,
      RecyclerUtils.getIndexAndTop(recyclerView)
    )
  }

  private fun scrollToHighlighted() {
    if (bookmarksToHighlight.isEmpty()) {
      return
    }

    val firstBookmark = bookmarksToHighlight.first()

    val positionToScrollTo = controller.adapter.copyOfModels.indexOfFirst { epoxyModel ->
      if (epoxyModel !is UnifiedBookmarkInfoAccessor) {
        return@indexOfFirst false
      }

      val threadDescriptor = (epoxyModel as UnifiedBookmarkInfoAccessor).getBookmarkDescriptor()
        ?: return@indexOfFirst false

      return@indexOfFirst threadDescriptor == firstBookmark
    }

    if (positionToScrollTo < 0) {
      return
    }

    when (val layoutManager = epoxyRecyclerView.layoutManager) {
      is GridLayoutManager -> layoutManager.scrollToPositionWithOffset(positionToScrollTo, 0)
      is LinearLayoutManager -> layoutManager.scrollToPositionWithOffset(positionToScrollTo, 0)
    }
  }

  private fun restoreScrollPosition() {
    val isForGridLayoutManager = when (epoxyRecyclerView.layoutManager) {
      is GridLayoutManager -> true
      is LinearLayoutManager -> false
      else -> throw IllegalStateException("Unknown layout manager: " +
        "${epoxyRecyclerView.layoutManager?.javaClass?.simpleName}"
      )
    }

    val indexAndTop = PersistableChanState.getRecyclerIndexAndTopInfo(
      PersistableChanState.bookmarksRecyclerIndexAndTop,
      isForGridLayoutManager
    )

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
    navigation.findItem(ACTION_CHANGE_VIEW_BOOKMARK_MODE)
      ?.setImage(getBookmarksModeChangeToolbarButtonDrawableId())

    navigation.findSubItem(ACTION_SET_GRID_BOOKMARK_VIEW_WIDTH)?.let { menuSubItem ->
      menuSubItem.visible = PersistableChanState.viewThreadBookmarksGridMode.get()
    }
  }

  private fun getBookmarksModeChangeToolbarButtonDrawableId(): Int {
    return when (PersistableChanState.viewThreadBookmarksGridMode.get()) {
      // Should be a reverse of whatever viewThreadBookmarksGridMode currently is because the
      // button's meaning is to switch into that mode, not show the current mode
      false -> R.drawable.ic_baseline_view_comfy_24
      true -> R.drawable.ic_baseline_view_list_24
    }
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

    return getString(
      R.string.controller_selected_n_bookmarks,
      bookmarksSelectionHelper.selectedItemsCount()
    )
  }

  private inner class BookmarksEpoxyController : EpoxyController() {
    var callback: EpoxyController.() -> Unit = {}

    override fun buildModels() {
      callback(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onModelBound(
      holder: EpoxyViewHolder,
      boundModel: EpoxyModel<*>,
      position: Int,
      previouslyBoundModel: EpoxyModel<*>?
    ) {
      val dragIndicator = when (boundModel) {
        is EpoxyGridThreadBookmarkViewHolder_ -> boundModel.dragIndicator
        is EpoxyListThreadBookmarkViewHolder_ -> boundModel.dragIndicator
        else -> null
      }

      if (dragIndicator == null) {
        return
      }

      dragIndicator.setOnTouchListener { _, event ->
        if (bookmarksSelectionHelper.isInSelectionMode()) {
          return@setOnTouchListener false
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
          itemTouchHelper.startDrag(holder)
        }

        return@setOnTouchListener false
      }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onModelUnbound(holder: EpoxyViewHolder, model: EpoxyModel<*>) {
      val dragIndicator = when (model) {
        is EpoxyGridThreadBookmarkViewHolder_ -> model.dragIndicator
        is EpoxyListThreadBookmarkViewHolder_ -> model.dragIndicator
        else -> null
      }

      if (dragIndicator == null) {
        return
      }

      dragIndicator.setOnTouchListener(null)
    }
  }

  companion object {
    private const val TAG = "BookmarksController"

    const val MIN_SPAN_COUNT = 2
    const val MAX_SPAN_COUNT = 20

    private const val ACTION_CHANGE_VIEW_BOOKMARK_MODE = 1000
    private const val ACTION_OPEN_SORT_SETTINGS = 1001

    private const val ACTION_PRUNE_NON_ACTIVE_BOOKMARKS = 2000
    private const val ACTION_MARK_ALL_BOOKMARKS_AS_SEEN = 2001
    private const val ACTION_CLEAR_ALL_BOOKMARKS = 2002
    private const val ACTION_SET_GRID_BOOKMARK_VIEW_WIDTH = 2003
    private const val ACTION_BOOKMARK_GROUPS_SETTINGS = 2004
    private const val ACTION_RESTART_FILTER_WATCHER = 2005
  }
}