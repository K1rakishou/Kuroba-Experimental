package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.R.string.action_reload
import com.github.k1rakishou.chan.core.concurrency.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager.BookmarkChange
import com.github.k1rakishou.chan.core.manager.BookmarksManager.BookmarkChange.BookmarksCreated
import com.github.k1rakishou.chan.core.manager.BookmarksManager.BookmarkChange.BookmarksDeleted
import com.github.k1rakishou.chan.core.manager.BookmarksManager.BookmarkChange.BookmarksInitialized
import com.github.k1rakishou.chan.core.manager.CurrentFocusedController
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloaderSettingsController
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarState
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuOverflowItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarContentState
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarStateKind
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController.ReplyAutoCloseListener
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.layout.ThreadLayout
import com.github.k1rakishou.chan.ui.layout.ThreadLayout.ThreadLayoutCallback
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.shareLink
import com.github.k1rakishou.chan.utils.SharingUtils.getUrlForSharing
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.util.ChanPostUtils
import com.github.k1rakishou.persist_state.PersistableChanState
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

open class ViewThreadController(
  context: Context,
  mainControllerCallbacks: MainControllerCallbacks,
  startingThreadDescriptor: ThreadDescriptor
) : ThreadController(context, mainControllerCallbacks),
  ThreadLayoutCallback,
  ReplyAutoCloseListener {

  @Inject
  lateinit var historyNavigationManagerLazy: Lazy<HistoryNavigationManager>
  @Inject
  lateinit var bookmarksManagerLazy: Lazy<BookmarksManager>
  @Inject
  lateinit var threadDownloadManagerLazy: Lazy<ThreadDownloadManager>

  private val historyNavigationManager: HistoryNavigationManager
    get() = historyNavigationManagerLazy.get()
  private val bookmarksManager: BookmarksManager
    get() = bookmarksManagerLazy.get()
  private val threadDownloadManager: ThreadDownloadManager
    get() = threadDownloadManagerLazy.get()

  private var pinItemPinned = false
  private var threadDescriptor: ThreadDescriptor = startingThreadDescriptor

  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(controllerScope)

  override val threadControllerType: ThreadControllerType
    get() = ThreadControllerType.Thread
  override val kurobaToolbarState: KurobaToolbarState
    get() = toolbarState
  override val controllerKey: ControllerKey
    get() = ViewThreadController.threadControllerKey

  val threadControllerToolbarState: KurobaToolbarState
    get() = kurobaToolbarStateManager.getOrCreate(ViewThreadController.threadControllerKey)

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    threadLayout.setBoardPostViewMode(ChanSettings.BoardPostViewMode.LIST)
    view.setBackgroundColor(themeEngine.chanTheme.backColor)

    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags(
        swipeable = false,
        hasDrawer = true
      )
    )

    buildToolbar()

    controllerScope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .filter { bookmarkChange: BookmarkChange? -> bookmarkChange !is BookmarksInitialized }
        .debounce(350.milliseconds)
        .collect { bookmarkChange -> updatePinIconStateIfNeeded(bookmarkChange) }
    }

    controllerScope.launch {
      combine(
        toolbarState.threadSearch.listenForSearchCreationUpdates(),
        toolbarState.threadSearch.listenForSearchVisibilityUpdates(),
        toolbarState.threadSearch.listenForSearchQueryUpdates()
      ) { created, visibility, searchQuery ->
        return@combine ThreadSearchData(
          searchToolbarCreated = created,
          searchToolbarVisible = visibility,
          searchQuery = searchQuery
        )
      }
        .collectLatest { threadSearchData ->
          val currentChanDescriptor = chanDescriptor
            ?: return@collectLatest

          delay(200)

          onThreadSearchDataUpdated(currentChanDescriptor, threadSearchData)
        }
    }

    controllerScope.launch {
      toolbarState.threadSearch.showFoundItemsAsPopupClicked
        .onEach {
          val chanDescriptor = chanDescriptor
            ?: return@onEach

          val searchQuery = threadPostSearchManager.currentSearchQuery(chanDescriptor)
            ?: return@onEach

          threadLayout.popupHelper.showSearchPopup(
            chanDescriptor = chanDescriptor,
            searchQuery = searchQuery
          )
        }
        .collect()
    }

    controllerScope.launch {
      currentChanDescriptorFlow
        .filterNotNull()
        .flatMapLatest { chanDescriptor -> threadPostSearchManager.listenForActiveSearchToolbarInfo(chanDescriptor) }
        .collectLatest { activeSearchToolbarInfo ->
          toolbarState.threadSearch.updateActiveSearchInfo(
            currentIndex = activeSearchToolbarInfo.currentIndex,
            totalFound = activeSearchToolbarInfo.totalFound
          )
        }
    }

    controllerScope.launch(Dispatchers.Main) { loadThread(threadDescriptor) }
  }

  override fun onShow() {
    super.onShow()
    setPinIconState(false)
  }

  override fun onDestroy() {
    super.onDestroy()
    updateLeftPaneHighlighting(null)
  }

  override fun onReplyViewShouldClose() {
    if (threadControllerToolbarState.isInReplyMode()) {
      threadControllerToolbarState.pop()
    }

    threadLayout.openOrCloseReply(false)
  }

  override suspend fun showThreadWithoutFocusing(descriptor: ThreadDescriptor, animated: Boolean) {
    Logger.d(TAG, "showThreadWithoutFocusing($descriptor, $animated)")
    showThread(descriptor, animated)
  }

  override suspend fun showThread(descriptor: ThreadDescriptor, animated: Boolean) {
    controllerScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "showThread($descriptor, $animated)")
      loadThread(descriptor)
    }
  }

  override suspend fun showCatalogWithoutFocusing(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean) {
    controllerScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "showCatalog($catalogDescriptor, $animated)")

      showCatalogInternal(
        catalogDescriptor = catalogDescriptor,
        showCatalogOptions = ShowCatalogOptions(
          switchToCatalogController = false,
          withAnimation = animated
        )
      )
    }
  }

  override suspend fun showCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean) {
    controllerScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "showCatalog($catalogDescriptor, $animated)")

      showCatalogInternal(
        catalogDescriptor = catalogDescriptor,
        showCatalogOptions = ShowCatalogOptions(
          switchToCatalogController = true,
          withAnimation = animated
        )
      )
    }
  }

  override suspend fun setCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean) {
    controllerScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "setCatalog($catalogDescriptor, $animated)")

      showCatalogInternal(
        catalogDescriptor = catalogDescriptor,
        showCatalogOptions = ShowCatalogOptions(
          switchToCatalogController = true,
          withAnimation = animated
        )
      )
    }
  }

  suspend fun loadThread(
    threadDescriptor: ThreadDescriptor,
    openingExternalThread: Boolean = false,
    openingPreviousThread: Boolean = false
  ) {
    Logger.d(TAG, "loadThread($threadDescriptor)")

    val presenter = threadLayout.presenter
    if (threadDescriptor != presenter.currentChanDescriptor) {
      loadThreadInternal(threadDescriptor, openingExternalThread, openingPreviousThread)
    }
  }

  override suspend fun openExternalThread(postDescriptor: PostDescriptor, scrollToPost: Boolean) {
    val descriptor = chanDescriptor
      ?: return

    openExternalThreadHelper.openExternalThread(
      currentChanDescriptor = descriptor,
      postDescriptor = postDescriptor,
      scrollToPost = scrollToPost
    ) { threadDescriptor ->
      controllerScope.launch { loadThread(threadDescriptor = threadDescriptor, openingExternalThread = true) }
    }
  }

  override suspend fun showPostsInExternalThread(
    postDescriptor: PostDescriptor,
    isPreviewingCatalogThread: Boolean
  ) {
    showPostsInExternalThreadHelper.showPostsInExternalThread(postDescriptor, isPreviewingCatalogThread)
  }

  override fun onShowLoading() {
    threadControllerToolbarState.thread.updateTitle(
      newTitle = ToolbarText.Id(com.github.k1rakishou.chan.R.string.loading),
      newSubTitle = null
    )
  }

  override fun onShowPosts(chanDescriptor: ChanDescriptor) {
    super.onShowPosts(chanDescriptor)

    setNavigationTitleFromDescriptor(chanDescriptor as ThreadDescriptor)
    setPinIconState(false)
  }

  override fun onShowError() {
    super.onShowError()

    threadControllerToolbarState.thread.updateTitle(
      newTitle = ToolbarText.Id(R.string.thread_loading_error_title),
      newSubTitle = null
    )
  }

  override fun threadBackPressed(): Boolean {
    val threadDescriptor = threadFollowHistoryManager.removeTop()
      ?: return false

    controllerScope.launch(Dispatchers.Main.immediate) {
      loadThread(threadDescriptor, openingPreviousThread = true)
    }

    return true
  }

  override fun threadBackLongPressed() {
    threadFollowHistoryManager.clearAllExcept(threadDescriptor)
    showToast(R.string.thread_follow_history_has_been_cleared)
  }

  override fun onThreadLayoutStateChanged(state: ThreadLayout.State) {
    threadControllerToolbarState.thread.updateToolbarContentState(ToolbarContentState.from(state))
  }

  override fun onLostFocus(nowFocused: ThreadControllerType) {
    super.onLostFocus(nowFocused)
    check(nowFocused == threadControllerType) { "Unexpected controllerType: $nowFocused" }

    threadControllerToolbarState.invokeAfterTransitionFinished {
      if (threadControllerToolbarState.isInReplyMode()) {
        popUntil(withAnimation = false) { topToolbar ->
          if (topToolbar.kind != ToolbarStateKind.Thread) {
            return@popUntil true
          }

          return@popUntil false
        }
      }
    }
  }

  override fun onGainedFocus(wasFocused: ThreadControllerType) {
    super.onGainedFocus(wasFocused)
    check(wasFocused == threadControllerType) { "Unexpected controllerType: $wasFocused" }

    if (historyNavigationManager.isInitialized) {
      if (threadLayout.presenter.chanThreadLoadingState == ThreadPresenter.ChanThreadLoadingState.Loaded) {
        controllerScope.launch { historyNavigationManager.moveNavElementToTop(threadDescriptor) }
      }
    }

    currentOpenedDescriptorStateManager.updateCurrentFocusedController(CurrentFocusedController.Thread)
    updateLeftPaneHighlighting(threadDescriptor)
  }

  private fun updatePinIconStateIfNeeded(bookmarkChange: BookmarkChange) {
    val currentThreadDescriptor = threadLayout.presenter.threadDescriptorOrNull()
      ?: return
    val changedBookmarkDescriptors = bookmarkChange.threadDescriptors()

    if (changedBookmarkDescriptors.isEmpty()) {
      return
    }

    var animate = false

    if (bookmarkChange is BookmarksCreated || bookmarkChange is BookmarksDeleted) {
      animate = true
    }

    for (changedBookmarkDescriptor in changedBookmarkDescriptors) {
      if (changedBookmarkDescriptor == currentThreadDescriptor) {
        setPinIconState(animate)
        return
      }
    }
  }

  private suspend fun showCatalogInternal(
    catalogDescriptor: ChanDescriptor.ICatalogDescriptor,
    showCatalogOptions: ShowCatalogOptions
  ) {
    Logger.d(TAG, "showCatalogInternal($catalogDescriptor, $showCatalogOptions)")

    if (doubleNavigationController != null && doubleNavigationController?.leftController() is BrowseController) {
      val browseController = doubleNavigationController?.leftController() as? BrowseController
      if (browseController != null) {
        browseController.setCatalog(catalogDescriptor)

        if (showCatalogOptions.switchToCatalogController) {
          // slide layout
          doubleNavigationController?.switchToLeftController(showCatalogOptions.withAnimation)
        }
      }

      return
    }

    if (doubleNavigationController?.leftController() is StyledToolbarNavigationController) {
      // split layout
      val browseController = doubleNavigationController
        ?.leftController()
        ?.childControllers
        ?.getOrNull(0) as? BrowseController

      if (browseController != null) {
        browseController.setCatalog(catalogDescriptor)
      }

      return
    }

    // phone layout
    var browseController: BrowseController? = null

    for (controller in requireNavController().childControllers) {
      if (controller is BrowseController) {
        browseController = controller
        break
      }
    }

    if (browseController != null) {
      browseController.setCatalog(catalogDescriptor)
      requireNavController().popController(showCatalogOptions.withAnimation)
    }
  }

  private suspend fun loadThreadInternal(
    newThreadDescriptor: ThreadDescriptor,
    openingExternalThread: Boolean,
    openingPreviousThread: Boolean
  ) {
    if (!openingExternalThread && !openingPreviousThread) {
      threadFollowHistoryManager.clear()
    }

    val presenter = threadLayout.presenter
    val oldThreadDescriptor = threadLayout.presenter.currentChanDescriptor as? ThreadDescriptor

    presenter.bindChanDescriptor(newThreadDescriptor)
    this.threadDescriptor = newThreadDescriptor

    updateMenuItems()
    updateNavigationTitle(oldThreadDescriptor, newThreadDescriptor)

    setPinIconState(false)
    updateLeftPaneHighlighting(newThreadDescriptor)
  }

  private fun updateNavigationTitle(
    oldThreadDescriptor: ThreadDescriptor?,
    newThreadDescriptor: ThreadDescriptor?
  ) {
    if (oldThreadDescriptor == null && newThreadDescriptor == null) {
      return
    }

    if (oldThreadDescriptor == newThreadDescriptor) {
      setNavigationTitleFromDescriptor(newThreadDescriptor)
    } else {
      toolbarState.thread.updateTitle(
        newTitle = ToolbarText.String(getString(R.string.loading)),
        newSubTitle = null
      )
    }
  }

  private fun setNavigationTitleFromDescriptor(threadDescriptor: ThreadDescriptor?) {
    rendezvousCoroutineExecutor.post {
      val (title, subtitle) = withContext(Dispatchers.Default) {
        val chanThread = chanThreadManager.getChanThread(threadDescriptor)
        val originalPost = chanThread?.getOriginalPost()

        val title = ChanPostUtils.getTitle(originalPost, threadDescriptor)
        var subtitle: String? = null

        if (chanThread != null && threadDescriptor != null && originalPost != null) {
          val chanBoard = boardManager.byBoardDescriptor(threadDescriptor.boardDescriptor)
          val boardPage = pageRequestManager.getPage(
            originalPostDescriptor = originalPost.postDescriptor,
            requestPagesIfNotCached = false
          )

          subtitle = ChanPostUtils.getThreadStatistics(
            chanThread = chanThread,
            op = originalPost,
            board = chanBoard,
            boardPage = boardPage
          )
        }

        return@withContext title to subtitle
      }

      toolbarState.thread.updateTitle(
        newTitle = ToolbarText.String(title),
        newSubTitle = subtitle?.let { ToolbarText.String(it) }
      )
    }
  }

  private suspend fun updateMenuItems() {
    toolbarState.findOverflowItem(ACTION_PREVIEW_THREAD_IN_ARCHIVE)
      ?.updateVisibility(archivesManager.supports(threadDescriptor))
    toolbarState.findOverflowItem(ACTION_OPEN_THREAD_IN_ARCHIVE)
      ?.updateVisibility(archivesManager.supports(threadDescriptor))

    updateThreadDownloadItem()
  }

  private suspend fun updateThreadDownloadItem() {
    toolbarState.findOverflowItem(ACTION_DOWNLOAD_THREAD)?.let { downloadThreadItem ->
      val status = threadDownloadManager.getStatus(threadDescriptor)
      downloadThreadItem.updateVisibility(status != ThreadDownload.Status.Completed)

      when (status) {
        null,
        ThreadDownload.Status.Stopped -> {
          downloadThreadItem.updateMenuText(getString(R.string.action_start_thread_download))
        }
        ThreadDownload.Status.Running -> {
          downloadThreadItem.updateMenuText(getString(R.string.action_stop_thread_download))
        }
        ThreadDownload.Status.Completed -> {
          downloadThreadItem.updateVisibility(false)
        }
      }
    }
  }

  private fun updateLeftPaneHighlighting(chanDescriptor: ChanDescriptor?) {
    if (doubleNavigationController == null) {
      return
    }

    var threadController: ThreadController? = null
    val leftController = doubleNavigationController?.leftController()

    if (leftController is ThreadController) {
      threadController = leftController
    } else if (leftController is NavigationController) {
      for (controller in leftController.childControllers) {
        if (controller is ThreadController) {
          threadController = controller
          break
        }
      }
    }

    if (threadController == null) {
      return
    }

    if (chanDescriptor is ThreadDescriptor) {
      threadController.highlightPost(postDescriptor = chanDescriptor.toOriginalPostDescriptor(), blink = false)
    } else if (chanDescriptor == null) {
      threadController.highlightPost(postDescriptor = null, blink = false)
    }
  }

  private fun setPinIconState(animated: Boolean) {
    val presenter = threadLayout.presenterOrNull
    if (presenter != null) {
      setPinIconStateDrawable(presenter.isPinned, animated)
    }
  }

  private fun setPinIconStateDrawable(pinned: Boolean, animated: Boolean) {
    if (pinned == pinItemPinned) {
      return
    }

    val menuItem = toolbarState.findItem(ACTION_PIN)
      ?: return

    pinItemPinned = pinned

    val drawableId = if (pinned) {
      R.drawable.ic_bookmark_white_24dp
    } else {
      R.drawable.ic_bookmark_border_white_24dp
    }

    menuItem.updateDrawableId(drawableId)
  }

  private fun buildToolbar() {
    toolbarState.enterThreadMode(
      leftItem = BackArrowMenuItem(
        onClick = {
          withLayoutMode(
            tablet = { doubleNavigationController?.updateRightController(null, true) },
            phone = { doubleNavigationController?.switchToLeftController() }
          )
        }
      ),
      scrollableTitle = ChanSettings.scrollingTextForThreadTitles.get(),
      menuBuilder = {
        withMenuItem(
          id = ACTION_SEARCH,
          drawableId = R.drawable.ic_search_white_24dp,
          onClick = { item -> searchClicked(item) }
        )

        withMenuItem(
          id = ACTION_ALBUM,
          drawableId = R.drawable.ic_image_white_24dp,
          onClick = { item -> albumClicked(item) }
        )
        withMenuItem(
          id = ACTION_PIN,
          drawableId = R.drawable.ic_bookmark_border_white_24dp,
          onClick = { item -> pinClicked(item) }
        )

        withOverflowMenu {
          if (!ChanSettings.enableReplyFab.get()) {
            withOverflowMenuItem(
              id = ACTION_REPLY,
              stringId = R.string.action_reply,
              onClick = { item -> replyClicked(item) }
            )
          }

          withOverflowMenuItem(
            id = ACTION_RELOAD,
            stringId = action_reload,
            onClick = { item -> reloadClicked(item) }
          )
          withOverflowMenuItem(
            id = ACTION_DOWNLOAD_THREAD,
            stringId = R.string.action_start_thread_download,
            visible = true,
            onClick = { item -> downloadOrStopDownloadThread(item) }
          )
          withOverflowMenuItem(
            id = ACTION_VIEW_REMOVED_POSTS,
            stringId = R.string.action_view_removed_posts,
            onClick = { item -> showRemovedPostsDialog(item) }
          )
          withOverflowMenuItem(
            id = ACTION_PREVIEW_THREAD_IN_ARCHIVE,
            stringId = R.string.action_preview_thread_in_archive,
            visible = false,
            onClick = { showAvailableArchivesList(postDescriptor = threadDescriptor.toOriginalPostDescriptor(), preview = true) }
          )
          withOverflowMenuItem(
            id = ACTION_OPEN_THREAD_IN_ARCHIVE,
            stringId = R.string.action_open_in_archive,
            visible = false,
            onClick = { showAvailableArchivesList(postDescriptor = threadDescriptor.toOriginalPostDescriptor(), preview = false) }
          )
          withOverflowMenuItem(
            id = ACTION_OPEN_BROWSER,
            stringId = R.string.action_open_browser,
            onClick = { item -> openBrowserClicked(item) }
          )
          withOverflowMenuItem(
            id = ACTION_SHARE,
            stringId = R.string.action_share,
            onClick = { item -> shareClicked(item) }
          )
          withOverflowMenuItem(
            id = ACTION_GO_TO_POST,
            stringId = R.string.action_go_to_post,
            visible = AppModuleAndroidUtils.isDevBuild,
            onClick = { item -> onGoToPostClicked(item) }
          )
          withOverflowMenuItem(
            id = ACTION_THREAD_MORE_OPTIONS,
            stringId = R.string.action_thread_options,
            builder = { withMoreThreadOptions() }
          )
          withOverflowMenuItem(
            id = ACTION_SCROLL_TO_TOP,
            stringId = R.string.action_scroll_to_top,
            onClick = { item -> upClicked(item) }
          )
          withOverflowMenuItem(
            id = ACTION_SCROLL_TO_BOTTOM,
            stringId = R.string.action_scroll_to_bottom,
            onClick = { item -> downClicked(item) }
          )
        }
      }
    )
  }

  private fun replyClicked(item: ToolbarMenuOverflowItem) {
    val presenter = threadLayout.presenter
    if (!presenter.isBoundAndCached || chanDescriptor == null) {
      return
    }

    threadLayout.openOrCloseReply(true)
  }

  private fun albumClicked(item: ToolbarMenuItem) {
    val presenter = threadLayout.presenter
    if (!presenter.isBoundAndCached || chanDescriptor == null) {
      return
    }

    threadLayout.presenter.showAlbum()
  }

  private fun pinClicked(item: ToolbarMenuItem) {
    val presenter = threadLayout.presenter
    if (!presenter.isBoundAndCached || chanDescriptor == null) {
      return
    }

    controllerScope.launch {
      if (threadLayout.presenter.pin()) {
        setPinIconState(true)
      }
    }
  }

  private fun searchClicked(item: ToolbarMenuItem) {
    val presenter = threadLayout.presenter
    if (!presenter.isBoundAndCached || chanDescriptor == null) {
      return
    }

    toolbarState.enterThreadSearchMode()
  }

  private fun reloadClicked(item: ToolbarMenuOverflowItem) {
    val presenter = threadLayout.presenter
    if (!presenter.isBoundAndCached || chanDescriptor == null) {
      return
    }

    threadLayout.presenter.resetTicker()
    threadLayout.presenter.normalLoad(
      showLoading = true,
      chanLoadOptions = ChanLoadOptions.clearMemoryCache()
    )
  }

  private fun downloadOrStopDownloadThread(item: ToolbarMenuOverflowItem) {
    val warningShown = PersistableChanState.threadDownloaderArchiveWarningShown.get()

    if (!warningShown && archivesManager.isSiteArchive(threadDescriptor.siteDescriptor())) {
      PersistableChanState.threadDownloaderArchiveWarningShown.set(true)

      dialogFactory.createSimpleInformationDialog(
        context = context,
        titleText = getString(R.string.thread_download_archive_thread_warning_title),
        descriptionText = getString(R.string.thread_download_archive_thread_warning_description),
        onPositiveButtonClickListener = { downloadOrStopDownloadThread(item) }
      )

      return
    }

    controllerScope.launch {
      when (threadDownloadManager.getStatus(threadDescriptor)) {
        ThreadDownload.Status.Running -> {
          threadDownloadManager.stopDownloading(threadDescriptor)
        }
        null,
        ThreadDownload.Status.Stopped -> {
          val threadDownloaderSettingsController = ThreadDownloaderSettingsController(
            context = context,
            downloadClicked = { downloadMedia ->
              controllerScope.launch {
                val threadThumbnailUrl = chanThreadManager.getChanThread(threadDescriptor)
                  ?.getOriginalPost()
                  ?.firstImage()
                  ?.actualThumbnailUrl
                  ?.toString()

                threadDownloadManager.startDownloading(
                  threadDescriptor = threadDescriptor,
                  threadThumbnailUrl = threadThumbnailUrl,
                  downloadMedia = downloadMedia
                )

                updateThreadDownloadItem()
              }
            }
          )

          presentController(threadDownloaderSettingsController, animated = true)
        }
        ThreadDownload.Status.Completed -> {
          return@launch
        }
      }

      updateThreadDownloadItem()
    }
  }

  private fun showRemovedPostsDialog(item: ToolbarMenuOverflowItem?) {
    threadLayout.presenter.showRemovedPostsDialog()
  }

  private fun openBrowserClicked(item: ToolbarMenuOverflowItem) {
    if (threadLayout.presenter.currentChanDescriptor == null) {
      showToast(R.string.cannot_open_in_browser_already_deleted)
      return
    }

    val url = getUrlForSharing(siteManager, threadDescriptor)
    if (url == null) {
      showToast(R.string.cannot_open_in_browser_already_deleted)
      return
    }

    AppModuleAndroidUtils.openLink(url)
  }

  private fun shareClicked(item: ToolbarMenuOverflowItem) {
    if (threadLayout.presenter.currentChanDescriptor == null) {
      showToast(R.string.cannot_shared_thread_already_deleted)
      return
    }

    val url = getUrlForSharing(siteManager, threadDescriptor)
    if (url == null) {
      showToast(R.string.cannot_shared_thread_already_deleted)
      return
    }

    shareLink(url)
  }

  private fun onGoToPostClicked(item: ToolbarMenuOverflowItem) {
    dialogFactory.createSimpleDialogWithInput(
      context = context,
      titleTextId = R.string.view_thread_controller_enter_post_id,
      onValueEntered = { input: String ->
        try {
          val postNo = input.toLong()
          threadLayout.presenter.scrollToPost(PostDescriptor.create(threadDescriptor, postNo))
        } catch (e: NumberFormatException) {
          //ignored
        }
      },
      inputType = DialogFactory.DialogInputType.Integer
    )
  }

  private fun upClicked(item: ToolbarMenuOverflowItem) {
    threadLayout.scrollTo(0)
  }

  private fun downClicked(item: ToolbarMenuOverflowItem) {
    threadLayout.scrollTo(-1)
  }

  companion object {
    private const val TAG = "ViewThreadController"
    private const val ACTION_PIN = 8001
    private const val ACTION_ALBUM = 8002
    private const val ACTION_REPLY = 9000
    private const val ACTION_SEARCH = 9001
    private const val ACTION_RELOAD = 9002
    private const val ACTION_VIEW_REMOVED_POSTS = 9004
    private const val ACTION_PREVIEW_THREAD_IN_ARCHIVE = 9005
    private const val ACTION_OPEN_THREAD_IN_ARCHIVE = 9006
    private const val ACTION_OPEN_BROWSER = 9007
    private const val ACTION_SHARE = 9008
    private const val ACTION_GO_TO_POST = 9009
    private const val ACTION_SCROLL_TO_TOP = 9011
    private const val ACTION_SCROLL_TO_BOTTOM = 9012
    private const val ACTION_DOWNLOAD_THREAD = 9013

    val threadControllerKey by lazy(LazyThreadSafetyMode.NONE) { ControllerKey(ViewThreadController::class.java.name) }
  }
}
