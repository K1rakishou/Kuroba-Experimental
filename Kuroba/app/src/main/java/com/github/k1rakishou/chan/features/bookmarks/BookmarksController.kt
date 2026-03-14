package com.github.k1rakishou.chan.features.bookmarks

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.unit.Dp
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyModel
import com.airbnb.epoxy.EpoxyModelTouchCallback
import com.airbnb.epoxy.EpoxyViewHolder
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseSelectionHelper
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
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
import com.github.k1rakishou.chan.features.download.thread.ThreadDownloaderSettingsController
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.CloseMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuOverflowItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.compose.lazylist.ScrollbarView
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.ui.controller.settings.RangeSettingUpdaterController
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyExpandableGroupView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.view.insets.InsetAwareEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.RecyclerUtils
import com.github.k1rakishou.chan.utils.addOneshotModelBuildListener
import com.github.k1rakishou.common.AndroidUtils.getDisplaySize
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class BookmarksController(
  context: Context,
  private val bookmarksToHighlight: List<ChanDescriptor.ThreadDescriptor>,
  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks
) : Controller(context),
  BookmarksView,
  BookmarksSelectionHelper.OnBookmarkMenuItemClicked {

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

  private lateinit var epoxyRecyclerView: InsetAwareEpoxyRecyclerView
  private lateinit var swipeRefreshLayout: SwipeRefreshLayout
  private lateinit var itemTouchHelper: ItemTouchHelper
  private lateinit var scrollbarView: ScrollbarView

  private val bookmarksSelectionHelper = BookmarksSelectionHelper(this)

  private val bookmarksPresenter by lazy {
    BookmarksPresenter(
      kurobaSettings,
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

  private var viewThreadBookmarksGridMode = false
  private var moveNotActiveBookmarksToBottom = false
  private var moveBookmarksWithUnreadRepliesToTop = false

  private val touchHelperCallback = object : EpoxyModelTouchCallback<EpoxyModel<*>>(controller, EpoxyModel::class.java) {

    override fun isLongPressDragEnabled(): Boolean = false
    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlagsForModel(model: EpoxyModel<*>?, adapterPosition: Int): Int {
      if (model !is EpoxyGridThreadBookmarkViewHolder_ && model !is EpoxyListThreadBookmarkViewHolder_) {
        return makeMovementFlags(0, 0)
      }

      val moveFlags = if (viewThreadBookmarksGridMode) {
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

      if (moveNotActiveBookmarksToBottom) {
        val isDeadOrNotWatching = targetUnifiedBookmarkInfoAccessor.getBookmarkStats()?.isDeadOrNotWatching()
          ?: false

        if (isDeadOrNotWatching) {
          // If moveNotActiveBookmarksToBottom setting is active and the target bookmark is
          // "isDeadOrNotWatching" don't allow dropping the current bookmark over the target.
          return false
        }
      }

      if (moveBookmarksWithUnreadRepliesToTop) {
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
      val groupId = when (viewThreadBookmarksGridMode) {
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

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    view = inflate(context, R.layout.controller_bookmarks)
    swipeRefreshLayout = view.findViewById(R.id.boomarks_swipe_refresh_layout)

    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.addOnScrollListener(onScrollListener)

    scrollbarView = view.findViewById(R.id.bookmarks_controller_scrollbar)
    scrollbarView.attachRecyclerView(epoxyRecyclerView)
    scrollbarView.isScrollbarDraggable(true)
    scrollbarView.thumbDragListener(object : ScrollbarView.ThumbDragListener {
      override fun onDragStarted() {
        // no-op
      }

      override fun onDragEnded() {
        onRecyclerViewScrolled(epoxyRecyclerView)
      }
    })

    swipeRefreshLayout.setOnChildScrollUpCallback { parent, child ->
      val isDragging = scrollbarView.isDragging
      if (isDragging) {
        // Disable SwipeRefresh layout when dragging the fast scroller
        return@setOnChildScrollUpCallback true
      }

      if (toolbarState.topToolbar?.kind?.isDefaultToolbar() != true) {
        // Disable SwipeRefresh layout when in search/selection mode
        return@setOnChildScrollUpCallback true
      }

      return@setOnChildScrollUpCallback epoxyRecyclerView.canScrollVertically(-1)
    }

    swipeRefreshLayout.setOnRefreshListener {
      // The process of reloading bookmarks may not notify us about the results when none of the
      // bookmarks were changed during the update so we need to have this timeout mechanism in
      // such case.
      controllerScope.launch {
        bookmarkForegroundWatcher.restartWatching()

        delay(10_000)
        swipeRefreshLayout.isRefreshing = false
      }
    }

    epoxyRecyclerView.setController(controller)

    itemTouchHelper = ItemTouchHelper(touchHelperCallback)
    itemTouchHelper.attachToRecyclerView(epoxyRecyclerView)

    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags(
        hasDrawer = true,
        hasBack = false
      )
    )

    initToolbar()

    controllerScope.launch {
      bookmarksPresenter.bookmarksControllerState
        .collect { state -> onStateChanged(state) }
    }

    controllerScope.launch {
      bookmarksSelectionHelper.listenForSelectionChanges()
        .collect { selectionEvent -> onNewSelectionEvent(selectionEvent) }
    }

    controllerScope.launch {
      toolbarState.search.listenForSearchVisibilityUpdates()
        .onEach { searchVisible ->
          isInSearchMode = searchVisible
          bookmarksPresenter.onSearchModeChanged(searchVisible)
          if (!searchVisible) {
            needRestoreScrollPosition.set(true)
          }
        }
        .collect()
    }

    viewThreadBookmarksGridMode = kurobaSettings.internal.viewThreadBookmarksGridMode.readBlocking()
    moveNotActiveBookmarksToBottom = kurobaSettings.application.moveNotActiveBookmarksToBottom.readBlocking()
    moveBookmarksWithUnreadRepliesToTop = kurobaSettings.application.moveBookmarksWithUnreadRepliesToTop.readBlocking()

    controllerScope.launch {
      kurobaSettings.internal.viewThreadBookmarksGridMode.listen()
        .collect { gridMode -> viewThreadBookmarksGridMode = gridMode }
    }
    controllerScope.launch {
      kurobaSettings.application.moveNotActiveBookmarksToBottom.listen()
        .collect { move -> moveNotActiveBookmarksToBottom = move }
    }
    controllerScope.launch {
      kurobaSettings.application.moveBookmarksWithUnreadRepliesToTop.listen()
        .collect { move -> moveBookmarksWithUnreadRepliesToTop = move }
    }

    controllerScope.launch {
      toolbarState.search.listenForSearchState()
        .onEach { (_, query) -> bookmarksPresenter.onSearchEntered(query) }
        .collect()
    }

    controllerScope.launch {
      toolbarState.toolbarHeightState
        .onEach { toolbarHeight -> onToolbarHeightChanged(toolbarHeight) }
        .collect()
    }

    onViewBookmarksModeChanged()
    updateLayoutManager()

    bookmarksPresenter.onCreate(this)
    reloadBookmarks()
  }

  override fun onDestroy() {
    super.onDestroy()

    scrollbarView.cleanup()

    bookmarksPresenter.updateReorderingMode(enterReorderingMode = false)
    requireBottomPanelContract().hideBottomPanel(controllerKey)

    epoxyRecyclerView.clear()
    epoxyRecyclerView.removeOnScrollListener(onScrollListener)

    bookmarksPresenter.onDestroy()
  }

  override fun onBack(): Boolean {
    if (bookmarksPresenter.isInReorderingMode()) {
      if (bookmarksPresenter.updateReorderingMode(enterReorderingMode = false)) {
        return true
      }
    }

    val result = requireBottomPanelContract().passOnBackToBottomPanel(controllerKey)
    if (result) {
      bookmarksSelectionHelper.unselectAll()
    }

    return result
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

    bookmarksSelectionHelper.unselectAll()
  }

  private fun onToolbarHeightChanged(toolbarHeightDp: Dp?) {
    var toolbarHeight = with(appResources.composeDensity) { toolbarHeightDp?.toPx()?.toInt() }
    if (toolbarHeight == null) {
      toolbarHeight = appResources.dimension(R.dimen.toolbar_height).toInt()
    }

    swipeRefreshLayout.setProgressViewOffset(
      false,
      toolbarHeight - AppModuleAndroidUtils.dp(40f),
      toolbarHeight + AppModuleAndroidUtils.dp(64 - 40.toFloat())
    )
  }

  private fun onDownloadThreadsClicked(
    downloadMedia: Boolean,
    threadDescriptors: List<ChanDescriptor.ThreadDescriptor>
  ) {
    if (threadDescriptors.isEmpty()) {
      return
    }

    controllerScope.launch {
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
      is BaseSelectionHelper.SelectionEvent.EnteredSelectionMode,
      is BaseSelectionHelper.SelectionEvent.ItemSelectionToggled -> {
        if (selectionEvent is BaseSelectionHelper.SelectionEvent.EnteredSelectionMode) {
          requireBottomPanelContract().showBottomPanel(controllerKey, bookmarksSelectionHelper.getBottomPanelMenus())
        }

        enterSelectionModeOrUpdate()
      }
      BaseSelectionHelper.SelectionEvent.ExitedSelectionMode -> {
        requireBottomPanelContract().hideBottomPanel(controllerKey)

        if (toolbarState.isInSelectionMode()) {
          toolbarState.pop()
        }
      }
    }
  }

  private fun onChangeViewModeClicked() {
    kurobaSettings.internal.viewThreadBookmarksGridMode.toggleBlocking()

    onViewBookmarksModeChanged()

    viewModeChanged.set(true)
    needRestoreScrollPosition.set(true)

    bookmarksPresenter.onViewBookmarksModeChanged()
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
      defaultValue =  kurobaSettings.application.bookmarkGridViewWidth.default,
      currentValue = kurobaSettings.application.bookmarkGridViewWidth.readBlocking(),
      resetClickedFunc = {
        kurobaSettings.application.bookmarkGridViewWidth.writeAsync(
          kurobaSettings.application.bookmarkGridViewWidth.default
        )
      },
      applyClickedFunc = { newValue ->
        val currentValue = kurobaSettings.application.bookmarkGridViewWidth.readBlocking()
        if (currentValue != newValue) {
          kurobaSettings.application.bookmarkGridViewWidth.writeAsync(newValue)
          reloadBookmarksAndUpdateViewMode()
        }
      }
    )

    requireNavController().presentController(rangeSettingUpdaterController)
  }

  private fun onClearAllBookmarksClicked(subItem: ToolbarMenuOverflowItem) {
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

  private fun onPruneNonActiveBookmarksClicked(subItem: ToolbarMenuOverflowItem) {
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
    if (viewThreadBookmarksGridMode) {
      if (!forced && epoxyRecyclerView.layoutManager is GridLayoutManager) {
        return
      }

      val bookmarkWidth = kurobaSettings.application.bookmarkGridViewWidth.readBlocking()
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
    val isGridMode = viewThreadBookmarksGridMode

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

          val isTablet = AppModuleAndroidUtils.isTablet
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

    withLayoutMode(
      phone = {
        requireNavController().popController {
          startActivityCallback.loadThread(threadDescriptor, true)
        }
      },
      tablet = {
        startActivityCallback.loadThread(threadDescriptor, true)
      }
    )
  }

  private fun onRecyclerViewScrolled(recyclerView: RecyclerView) {
    val isGridLayoutManager = when (recyclerView.layoutManager) {
      is GridLayoutManager -> true
      is LinearLayoutManager -> false
      else -> throw IllegalStateException(
        "Unknown layout manager: " +
          "${recyclerView.layoutManager?.javaClass?.simpleName}"
      )
    }

    kurobaSettings.internal.storeRecyclerIndexAndTopInfo(
      setting = kurobaSettings.internal.bookmarksRecyclerIndexAndTop,
      isForGridLayoutManager = isGridLayoutManager,
      indexAndTop = RecyclerUtils.getIndexAndTop(recyclerView)
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

    val indexAndTop = kurobaSettings.internal.getRecyclerIndexAndTopInfo(
      setting = kurobaSettings.internal.bookmarksRecyclerIndexAndTop,
      isForGridLayoutManager = isForGridLayoutManager
    )

    when (val layoutManager = epoxyRecyclerView.layoutManager) {
      is GridLayoutManager -> layoutManager.scrollToPositionWithOffset(indexAndTop.index, indexAndTop.top)
      is LinearLayoutManager -> layoutManager.scrollToPositionWithOffset(indexAndTop.index, indexAndTop.top)
    }
  }

  private fun updateTitleWithoutStats() {
    toolbarState.default.updateTitle(
      newTitle = ToolbarText.String(getString(R.string.controller_bookmarks_title))
    )
  }

  private fun updateTitleWithStats(state: BookmarksControllerState.Data) {
    toolbarState.default.updateTitle(
      newTitle = ToolbarText.String(formatTitleWithStats(state))
    )
  }

  private fun formatTitleWithStats(state: BookmarksControllerState.Data): String {
    val groupedBookmarks = state.groupedBookmarks

    val totalBookmarksCount = groupedBookmarks.sumOf { group -> group.threadBookmarkItemViews.size }
    if (totalBookmarksCount <= 0) {
      return context.getString(R.string.controller_bookmarks_title)
    }

    val watchingBookmarksCount = groupedBookmarks.sumOf { group ->
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
    toolbarState.findItem(ACTION_CHANGE_VIEW_BOOKMARK_MODE)
      ?.updateDrawableId(getBookmarksModeChangeToolbarButtonDrawableId())
    toolbarState.findOverflowItem(ACTION_SET_GRID_BOOKMARK_VIEW_WIDTH)
      ?.updateVisibility(visible = viewThreadBookmarksGridMode)
  }

  private fun getBookmarksModeChangeToolbarButtonDrawableId(): Int {
    return when (viewThreadBookmarksGridMode) {
      // Should be a reverse of whatever viewThreadBookmarksGridMode currently is because the
      // button's meaning is to switch into that mode, not show the current mode
      false -> R.drawable.ic_baseline_view_comfy_24
      true -> R.drawable.ic_baseline_view_list_24
    }
  }

  private fun enterSelectionModeOrUpdate() {
    val selectedItemsCount = bookmarksSelectionHelper.selectedItemsCount()
    val totalItemsCount = (bookmarksPresenter.bookmarksControllerState.value as? BookmarksControllerState.Data)
      ?.groupedBookmarks
      ?.sumOf { groupOfThreadBookmarkItemViews -> groupOfThreadBookmarkItemViews.threadBookmarkItemViews.size }
      ?: 0

    if (!toolbarState.isInSelectionMode()) {
      toolbarState.enterSelectionMode(
        leftItem = CloseMenuItem(
          onClick = {
            if (toolbarState.isInSelectionMode()) {
              toolbarState.pop()
              bookmarksSelectionHelper.unselectAll()
            }
          }
        ),
        selectedItemsCount = selectedItemsCount,
        totalItemsCount = totalItemsCount
      )
    }

    toolbarState.selection.updateCounters(
      selectedItemsCount = selectedItemsCount,
      totalItemsCount = totalItemsCount
    )
  }

  private fun initToolbar() {
    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.Id(R.string.controller_bookmarks_title)
      ),
      iconClickInterceptor = {
        exitReorderingModeIfActive()
        return@enterDefaultMode false
      },
      menuBuilder = {
        withMenuItem(
          id = null,
          drawableId = R.drawable.ic_search_white_24dp,
          onClick = { toolbarState.enterSearchMode() }
        )
        withMenuItem(
          id = ACTION_CHANGE_VIEW_BOOKMARK_MODE,
          drawableId = getBookmarksModeChangeToolbarButtonDrawableId(),
          onClick = { onChangeViewModeClicked() }
        )
        withMenuItem(
          id = ACTION_OPEN_SORT_SETTINGS,
          drawableId = R.drawable.ic_baseline_sort_24,
          onClick = {
            val controller = BookmarksSortingController(
              context = context,
              constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
              bookmarksView = this@BookmarksController
            )

            presentController(controller)
          }
        )

        withOverflowMenu {
          withOverflowMenuItem(
            id = ACTION_RESTART_FILTER_WATCHER,
            stringId = R.string.controller_bookmarks_restart_filter_watcher,
            visible = true,
            onClick = { restartFilterWatcherClicked() }
          )
          withOverflowMenuItem(
            id = ACTION_MARK_ALL_BOOKMARKS_AS_SEEN,
            stringId = R.string.controller_bookmarks_mark_all_bookmarks_as_seen,
            visible = true,
            onClick = { bookmarksPresenter.markAllAsSeen() })
          withOverflowMenuItem(
            id = ACTION_PRUNE_NON_ACTIVE_BOOKMARKS,
            stringId = R.string.controller_bookmarks_prune_inactive_bookmarks,
            visible = true,
            onClick = { subItem -> onPruneNonActiveBookmarksClicked(subItem) }
          )
          withOverflowMenuItem(
            id = ACTION_BOOKMARK_GROUPS_SETTINGS,
            stringId = R.string.controller_bookmarks_bookmark_groups_settings,
            visible = true,
            onClick = { bookmarkGroupsSettings() }
          )
          withOverflowMenuItem(
            id = ACTION_SET_GRID_BOOKMARK_VIEW_WIDTH,
            stringId = R.string.controller_bookmarks_set_grid_bookmark_view_width,
            visible = viewThreadBookmarksGridMode,
            onClick = { onSetGridBookmarkViewWidthClicked() }
          )
          withOverflowMenuItem(
            id = ACTION_CLEAR_ALL_BOOKMARKS,
            stringId = R.string.controller_bookmarks_clear_all_bookmarks,
            visible = true,
            onClick = { subItem -> onClearAllBookmarksClicked(subItem) }
          )
        }
      }
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