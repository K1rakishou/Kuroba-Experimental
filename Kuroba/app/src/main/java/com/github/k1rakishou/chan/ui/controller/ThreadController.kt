package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.ui.unit.Dp
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.concurrency.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ApplicationVisibility
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityListener
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadFollowHistoryManager
import com.github.k1rakishou.chan.core.manager.ThreadPostSearchManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.features.album.AlbumViewController
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.features.filters.FiltersController
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerActivity
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerOptions
import com.github.k1rakishou.chan.features.media_viewer.helper.AlbumThreadControllerHelpers
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerOpenAlbumHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerScrollerHelper
import com.github.k1rakishou.chan.features.report_posts.Chan4ReportPostController
import com.github.k1rakishou.chan.features.thirdeye.ThirdEyeSettingsController
import com.github.k1rakishou.chan.features.toolbar.CloseMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuCheckableOverflowItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarOverflowMenuBuilder
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarStateKind
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController.SlideChangeListener
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleControllerType
import com.github.k1rakishou.chan.ui.controller.navigation.determineDoubleControllerType
import com.github.k1rakishou.chan.ui.helper.AppSettingsUpdateAppRefreshHelper
import com.github.k1rakishou.chan.ui.helper.OpenExternalThreadHelper
import com.github.k1rakishou.chan.ui.helper.ShowPostsInExternalThreadHelper
import com.github.k1rakishou.chan.ui.layout.ThreadLayout
import com.github.k1rakishou.chan.ui.layout.ThreadLayout.ThreadLayoutCallback
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.viewstate.ReplyLayoutVisibilityStates
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ArchiveDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.filter.FilterType
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import javax.inject.Inject

abstract class ThreadController(
  context: Context,
  val mainControllerCallbacks: MainControllerCallbacks
) : Controller(context),
  ThreadLayoutCallback,
  OnRefreshListener,
  SlideChangeListener,
  ApplicationVisibilityListener,
  ThemeEngine.ThemeChangesListener,
  ThreadControllerCallbacks {

  @Inject
  lateinit var siteManagerLazy: Lazy<SiteManager>
  @Inject
  lateinit var boardManagerLazy: Lazy<BoardManager>
  @Inject
  lateinit var themeEngineLazy: Lazy<ThemeEngine>
  @Inject
  lateinit var applicationVisibilityManagerLazy: Lazy<ApplicationVisibilityManager>
  @Inject
  lateinit var chanThreadManagerLazy: Lazy<ChanThreadManager>
  @Inject
  lateinit var threadFollowHistoryManagerLazy: Lazy<ThreadFollowHistoryManager>
  @Inject
  lateinit var archivesManagerLazy: Lazy<ArchivesManager>
  @Inject
  lateinit var globalWindowInsetsManagerLazy: Lazy<GlobalWindowInsetsManager>
  @Inject
  lateinit var chanThreadViewableInfoManagerLazy: Lazy<ChanThreadViewableInfoManager>
  @Inject
  lateinit var mediaViewerScrollerHelperLazy: Lazy<MediaViewerScrollerHelper>
  @Inject
  lateinit var mediaViewerOpenAlbumHelperLazy: Lazy<MediaViewerOpenAlbumHelper>
  @Inject
  lateinit var appSettingsUpdateAppRefreshHelperLazy: Lazy<AppSettingsUpdateAppRefreshHelper>
  @Inject
  lateinit var currentOpenedDescriptorStateManagerLazy: Lazy<CurrentOpenedDescriptorStateManager>
  @Inject
  lateinit var pageRequestManagerLazy: Lazy<PageRequestManager>
  @Inject
  lateinit var albumThreadControllerHelpers: AlbumThreadControllerHelpers
  @Inject
  lateinit var threadPostSearchManagerLazy: Lazy<ThreadPostSearchManager>

  protected val siteManager: SiteManager
    get() = siteManagerLazy.get()
  protected val boardManager: BoardManager
    get() = boardManagerLazy.get()
  protected val themeEngine: ThemeEngine
    get() = themeEngineLazy.get()
  protected val chanThreadViewableInfoManager: ChanThreadViewableInfoManager
    get() = chanThreadViewableInfoManagerLazy.get()
  protected val archivesManager: ArchivesManager
    get() = archivesManagerLazy.get()
  protected val chanThreadManager: ChanThreadManager
    get() = chanThreadManagerLazy.get()
  protected val threadFollowHistoryManager: ThreadFollowHistoryManager
    get() = threadFollowHistoryManagerLazy.get()
  protected val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
    get() = currentOpenedDescriptorStateManagerLazy.get()
  protected val pageRequestManager: PageRequestManager
    get() = pageRequestManagerLazy.get()
  protected val threadPostSearchManager: ThreadPostSearchManager
    get() = threadPostSearchManagerLazy.get()

  private val applicationVisibilityManager: ApplicationVisibilityManager
    get() = applicationVisibilityManagerLazy.get()
  protected val globalWindowInsetsManager: GlobalWindowInsetsManager
    get() = globalWindowInsetsManagerLazy.get()
  private val mediaViewerScrollerHelper: MediaViewerScrollerHelper
    get() = mediaViewerScrollerHelperLazy.get()
  private val mediaViewerOpenAlbumHelper: MediaViewerOpenAlbumHelper
    get() = mediaViewerOpenAlbumHelperLazy.get()
  private val appSettingsUpdateAppRefreshHelper: AppSettingsUpdateAppRefreshHelper
    get() = appSettingsUpdateAppRefreshHelperLazy.get()

  protected lateinit var threadLayout: ThreadLayout
  protected lateinit var showPostsInExternalThreadHelper: ShowPostsInExternalThreadHelper
  protected lateinit var openExternalThreadHelper: OpenExternalThreadHelper

  private lateinit var swipeRefreshLayout: SwipeRefreshLayout
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor
  private var _prevThreadSearchData: ThreadSearchData? = null

  val chanDescriptor: ChanDescriptor?
    get() = threadLayout.presenter.currentChanDescriptor
  val currentChanDescriptorFlow: StateFlow<ChanDescriptor?>
    get() = threadLayout.presenter.currentChanDescriptorFlow

  abstract override val threadControllerType: ThreadControllerType

  override val snackbarScope: SnackbarScope
    get() {
      return when (threadControllerType) {
        ThreadControllerType.Catalog -> SnackbarScope.PostList(mainLayoutAnchor = SnackbarScope.MainLayoutAnchor.Catalog)
        ThreadControllerType.Thread -> SnackbarScope.PostList(mainLayoutAnchor = SnackbarScope.MainLayoutAnchor.Thread)
      }
    }

  override fun onCreate() {
    super.onCreate()

    threadLayout = inflate(context, R.layout.layout_thread, null) as ThreadLayout
    threadLayout.create(this, threadControllerType, controllerKey)

    swipeRefreshLayout = SwipeRefreshLayout(context)

    swipeRefreshLayout.setOnChildScrollUpCallback { parent, child ->
      return@setOnChildScrollUpCallback threadLayout.canChildScrollUp()
    }

    swipeRefreshLayout.id = R.id.swipe_refresh_layout
    swipeRefreshLayout.addView(threadLayout)
    swipeRefreshLayout.setOnRefreshListener(this)

    view = swipeRefreshLayout

    serializedCoroutineExecutor = SerializedCoroutineExecutor(controllerScope)
    applicationVisibilityManager.addListener(this)

    showPostsInExternalThreadHelper = ShowPostsInExternalThreadHelper(
      context = context,
      scope = controllerScope,
      postPopupHelper = threadLayout.popupHelper,
      chanThreadManagerLazy = chanThreadManagerLazy,
      presentControllerFunc = { controller -> presentController(controller) },
      showAvailableArchivesListFunc = { postDescriptor, canAutoSelectArchive ->
        showAvailableArchivesList(
          postDescriptor = postDescriptor,
          preview = true,
          canAutoSelectArchive = canAutoSelectArchive
        )
      },
      showToastFunc = { message -> showToast(message) }
    )

    openExternalThreadHelper = OpenExternalThreadHelper(
      postPopupHelper = threadLayout.popupHelper,
      chanThreadViewableInfoManagerLazy = chanThreadViewableInfoManagerLazy,
      threadFollowHistoryManagerLazy = threadFollowHistoryManagerLazy
    )

    controllerScope.launch {
      mediaViewerScrollerHelper.mediaViewerScrollEventsFlow
        .collect { scrollToImageEvent ->
          val descriptor = scrollToImageEvent.chanDescriptor
          if (descriptor != chanDescriptor) {
            return@collect
          }

          threadLayout.presenter.scrollToImage(scrollToImageEvent.chanPostImage)
        }
    }

    controllerScope.launch {
      mediaViewerOpenAlbumHelper.mediaViewerOpenAlbumEventsFlow
        .collect { openAlbumEvent ->
          val descriptor = openAlbumEvent.chanDescriptor
          if (descriptor != chanDescriptor) {
            return@collect
          }

          showAlbum(openAlbumEvent.chanPostImage.imageUrl)
        }
    }

    controllerScope.launch {
      appSettingsUpdateAppRefreshHelper.settingsUpdatedEvent.collect {
        Logger.d(TAG, "Reloading thread because app settings were updated")
        threadLayout.presenter.quickReloadFromMemoryCache()
      }
    }

    controllerScope.launch {
      globalUiStateHolder.replyLayout.replyLayoutVisibilityEventsFlow
        .onEach { replyLayoutVisibilityEvents ->
          onReplyLayoutVisibilityEvent(replyLayoutVisibilityEvents)
        }
        .collect()
    }

    controllerScope.launch {
      toolbarState.toolbarHeightState
        .onEach { toolbarHeight -> onToolbarHeightChanged(toolbarHeight) }
        .collect()
    }

    controllerScope.launch {
      albumThreadControllerHelpers.highlightPostWithImageEventsFlow
        .filter { event -> event.chanDescriptor == chanDescriptor }
        .onEach { event -> threadLayout.presenter.highlightPostWithImage(event.chanPostImage) }
        .collect()
    }

    controllerScope.launch {
      threadLayout.presenter.currentChanDescriptorFlow
        .filterNotNull()
        .flatMapLatest { chanDescriptor -> threadPostSearchManager.listenForScrollAnchorPostDescriptor(chanDescriptor) }
        .filterNotNull()
        .onEach { scrollAnchorPostDescriptor -> threadLayout.scrollToPost(scrollAnchorPostDescriptor) }
        .collect()
    }

    controllerScope.launch {
      kurobaToolbarState.toolbarSubStateChangesFlow
        .onEach {
          if (!globalUiStateHolder.replyLayout.state(threadControllerType).isOpenedOrExpanded()) {
            return@onEach
          }

          val toolbarHasReplyState = kurobaToolbarState.toolbarStateList.value
            .any { kurobaToolbarSubState -> kurobaToolbarSubState.kind == ToolbarStateKind.Reply }

          if (toolbarHasReplyState) {
            return@onEach
          }

          threadLayout.openOrCloseReply(open = false)
        }
        .collect()
    }

    onThemeChanged()
    themeEngine.addListener(this)
  }

  override fun onShow() {
    super.onShow()

    threadLayout.onShown(threadControllerType)
  }

  override fun onHide() {
    super.onHide()

    threadLayout.onHidden(threadControllerType)
  }

  override fun onDestroy() {
    super.onDestroy()

    threadLayout.destroy()
    applicationVisibilityManager.removeListener(this)
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
  }

  fun passMotionEventIntoDrawer(event: MotionEvent): Boolean {
    return mainControllerCallbacks.passMotionEventIntoDrawer(event)
  }

  fun passMotionEventIntoSlidingPaneLayout(event: MotionEvent): Boolean {
    val threadSlideController = (this.parentController as? ThreadSlideController)
      ?: return false

    return threadSlideController.passMotionEventIntoSlidingPaneLayout(event)
  }

  fun showLoading(animateTransition: Boolean = false) {
    threadLayout.showLoading(animateTransition = animateTransition)
  }

  open fun showSitesNotSetup() {
    threadLayout.presenter.showNoContent()
  }

  fun highlightPost(postDescriptor: PostDescriptor?, blink: Boolean) {
    threadLayout.presenter.highlightPost(postDescriptor, blink)
  }

  override fun onBack(): Boolean {
    return threadLayout.onBack()
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    return threadLayout.sendKeyEvent(event) || super.dispatchKeyEvent(event)
  }

  override fun onApplicationVisibilityChanged(applicationVisibility: ApplicationVisibility) {
    threadLayout.presenter.onForegroundChanged(applicationVisibility.isInForeground())
  }

  override fun onRefresh() {
    threadLayout.refreshFromSwipe()
  }

  override fun openReportController(post: ChanPost) {
    val site = siteManager.bySiteDescriptorAndActive(post.boardDescriptor.siteDescriptor)
    if (site == null || navigationController == null) {
      return
    }

    if (site.siteDescriptor().is4chan()) {
      val chan4ReportPostController = Chan4ReportPostController(
        context = context,
        postDescriptor = post.postDescriptor,
        onCaptchaRequired = {
          val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(post.boardDescriptor, 1L)

          threadLayout.showCaptchaController(
            chanDescriptor = threadDescriptor,
            replyMode = ReplyMode.ReplyModeSendWithoutCaptcha,
            autoReply = false,
            afterPostingAttempt = true
          )
        },
        onOpenInWebView = {
          openWebViewReportController(post, site)
        }
      )

      requireNavController().presentController(chan4ReportPostController)
      return
    } else if (site.siteDescriptor().isDvach()) {
      dialogFactory.createSimpleDialogWithInput(
        context = context,
        titleText = getString(R.string.dvach_report_post_title, post.postDescriptor.userReadableString()),
        descriptionText = getString(R.string.dvach_report_post_description),
        inputType = DialogFactory.DialogInputType.String,
        onValueEntered = { reason -> threadLayout.presenter.processDvachPostReport(reason, post, site) }
      )
      return
    }

    openWebViewReportController(post, site)
  }

  override fun openMediaLinkInMediaViewer(link: String) {
    Logger.d(TAG, "openMediaLinkInMediaViewer($link)")

    MediaViewerActivity.mixedMedia(
      context = context,
      mixedMedia = listOf(MediaLocation.Remote(link))
    )
  }

  override fun showImages(
    chanDescriptor: ChanDescriptor,
    initialImageUrl: String?,
    transitionThumbnailUrl: String
  ) {
    when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        MediaViewerActivity.catalogMedia(
          context = context,
          catalogDescriptor = chanDescriptor,
          initialImageUrl = initialImageUrl,
          transitionThumbnailUrl = transitionThumbnailUrl,
          lastTouchCoordinates = globalWindowInsetsManager.lastTouchCoordinates(),
          mediaViewerOptions = MediaViewerOptions(
            mediaViewerOpenedFromAlbum = false
          )
        )
      }
      is ChanDescriptor.ThreadDescriptor -> {
        MediaViewerActivity.threadMedia(
          context = context,
          threadDescriptor = chanDescriptor,
          postDescriptorList = threadLayout.displayingPostDescriptors,
          initialImageUrl = initialImageUrl,
          transitionThumbnailUrl = transitionThumbnailUrl,
          lastTouchCoordinates = globalWindowInsetsManager.lastTouchCoordinates(),
          mediaViewerOptions = MediaViewerOptions(
            mediaViewerOpenedFromAlbum = false
          )
        )
      }
    }
  }

  override fun showAlbum(initialImageUrl: HttpUrl?) {
    val listenMode = when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> AlbumViewController.ListenMode.Catalog
      is ChanDescriptor.ThreadDescriptor -> AlbumViewController.ListenMode.Thread
      null -> return
    }

    val albumViewController = AlbumViewController(
      context = context,
      listenMode = listenMode,
      initialImageFullUrl = initialImageUrl?.toString()
    )

    pushController(albumViewController)
  }

  override fun pushController(controller: Controller) {
    pushChildController(controller)
  }

  override fun onShowLoading() {
    // no-op
  }

  override fun onShowError() {
    // no-op
  }

  override fun onShowPosts(chanDescriptor: ChanDescriptor) {
    // no-op
  }

  override fun unpresentController(predicate: (Controller) -> Boolean) {
    getControllerOrNull { controller ->
      if (predicate(controller)) {
        controller.stopPresenting()
        return@getControllerOrNull true
      }

      return@getControllerOrNull false
    }
  }

  override fun isAlreadyPresentingController(predicate: (Controller) -> Boolean): Boolean {
    return super.isAlreadyPresenting(predicate)
  }

  override fun hideSwipeRefreshLayout() {
    if (!::swipeRefreshLayout.isInitialized) {
      return
    }

    swipeRefreshLayout.isRefreshing = false
  }

  override fun openFilterForType(type: FilterType, filterText: String, caseSensitive: Boolean) {
    val caseInsensitiveFlag = if (caseSensitive) {
      ""
    } else {
      "i"
    }

    val filter = ChanFilterMutable()
    filter.type = type.flag
    filter.pattern = "/${filterText.trim()}/$caseInsensitiveFlag"
    openFiltersController(filter)
  }

  override fun openFiltersController(chanFilterMutable: ChanFilterMutable) {
    chanDescriptor?.let { chanDescriptor ->
      chanFilterMutable.boards.add(chanDescriptor.boardDescriptor())
    }

    val filtersController = FiltersController(
      context = context,
      chanFilterMutable = chanFilterMutable
    )

    pushChildController(filtersController)
  }

  override fun onLostFocus(nowFocused: ThreadControllerType) {
    if (AppModuleAndroidUtils.isDevBuild) {
      check(nowFocused == threadControllerType) {
        "ThreadControllerTypes do not match! wasFocused=$nowFocused, current=$threadControllerType"
      }
    }

    threadLayout.lostFocus(nowFocused)
  }

  override fun onGainedFocus(wasFocused: ThreadControllerType) {
    if (AppModuleAndroidUtils.isDevBuild) {
      check(wasFocused == threadControllerType) {
        "ThreadControllerTypes do not match! nowFocused: $wasFocused, current: $threadControllerType"
      }
    }

    threadLayout.gainedFocus(wasFocused)
    globalUiStateHolder.updateThreadLayoutState { updateFocusedController(wasFocused) }
  }

  override fun threadBackPressed(): Boolean {
    return false
  }

  override fun threadBackLongPressed() {
    // no-op
  }

  override fun showAvailableArchivesList(
    postDescriptor: PostDescriptor,
    preview: Boolean,
    canAutoSelectArchive: Boolean
  ) {
    Logger.d(TAG, "showAvailableArchivesList($postDescriptor, $preview, $canAutoSelectArchive)")

    val descriptor = postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    val supportedArchiveDescriptors = archivesManager.getSupportedArchiveDescriptors(descriptor)
      .filter { ad -> siteManager.bySiteDescriptor(ad.siteDescriptor)?.enabled() ?: false }

    if (supportedArchiveDescriptors.isEmpty()) {
      Logger.d(TAG, "showAvailableArchives($descriptor) supportedThreadDescriptors is empty")

      val message = AppModuleAndroidUtils.getString(
        R.string.thread_presenter_no_archives_found_to_open_thread,
        descriptor.toString()
      )
      showToast(message, Toast.LENGTH_LONG)
      return
    }

    if (canAutoSelectArchive && supportedArchiveDescriptors.size == 1) {
      controllerScope.launch { onArchiveSelected(supportedArchiveDescriptors.first(), postDescriptor, preview) }
      return
    }

    val items = mutableListOf<FloatingListMenuItem>()

    supportedArchiveDescriptors.forEach { archiveDescriptor ->
      items += FloatingListMenuItem(
        archiveDescriptor,
        archiveDescriptor.name
      )
    }

    if (items.isEmpty()) {
      Logger.d(TAG, "showAvailableArchives($descriptor) items is empty")
      return
    }

    val floatingListMenuController = FloatingListMenuController(
      context,
      globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items,
      itemClickListener = { clickedItem ->
        controllerScope.launch {
          val archiveDescriptor = (clickedItem.key as? ArchiveDescriptor)
            ?: return@launch

          onArchiveSelected(archiveDescriptor, postDescriptor, preview)
        }
      }
    )

    presentController(floatingListMenuController)
  }

  private fun onReplyLayoutVisibilityEvent(replyLayoutVisibilityEvents: ReplyLayoutVisibilityStates) {
    val currentDescriptor = chanDescriptor
      ?: return

    val isInReplyLayoutMode = kurobaToolbarState.isInReplyMode()

    val currentReplyLayoutIsOpened = if (ChanSettings.isSplitLayoutMode()) {
      replyLayoutVisibilityEvents.isOpenedOrExpandedForDescriptor(currentDescriptor)
    } else {
      if (currentOpenedDescriptorStateManager.isDescriptorNotFocused(currentDescriptor)) {
        return
      }

      replyLayoutVisibilityEvents.isOpenedOrExpandedForDescriptor(currentDescriptor)
    }

    if (isInReplyLayoutMode == currentReplyLayoutIsOpened) {
      return
    }

    if (currentReplyLayoutIsOpened) {
      enterReplyLayoutToolbarMode(currentDescriptor)
      return
    }

    if (toolbarState.isInReplyMode()) {
      toolbarState.pop()
    }
  }

  private fun enterReplyLayoutToolbarMode(descriptor: ChanDescriptor) {
    kurobaToolbarState.enterReplyMode(
      chanDescriptor = descriptor,
      leftItem = CloseMenuItem(
        onClick = {
          if (toolbarState.isInReplyMode()) {
            toolbarState.pop()
          }

          threadLayout.openOrCloseReply(open = false)
        }
      ),
      menuBuilder = {
        withMenuItem(
          drawableId = R.drawable.ic_baseline_attach_file_24,
          onClick = { threadLayout.onPickLocalMediaButtonClicked() }
        )

        withMenuItem(
          drawableId = R.drawable.ic_baseline_cloud_download_24,
          onClick = { threadLayout.onPickRemoteMediaButtonClicked() }
        )

        withMenuItem(
          drawableId = R.drawable.ic_search_white_24dp,
          onClick = { threadLayout.onSearchRemoteMediaButtonClicked(descriptor) }
        )
      }
    )
  }

  private fun onToolbarHeightChanged(toolbarHeightDp: Dp?) {
    var toolbarHeight = with(appResources.composeDensity) { toolbarHeightDp?.toPx()?.toInt() }
    if (toolbarHeight == null) {
      toolbarHeight = appResources.dimension(com.github.k1rakishou.chan.R.dimen.toolbar_height).toInt()
    }

    swipeRefreshLayout.setProgressViewOffset(
      false,
      toolbarHeight - dp(40f),
      toolbarHeight + dp(64 - 40.toFloat())
    )
  }

  private fun openWebViewReportController(post: ChanPost, site: Site) {
    if (!site.siteFeature(Site.SiteFeature.POST_REPORT)) {
      return
    }

    if (site.endpoints().report(post) == null) {
      return
    }

    val reportController = WebViewReportController(context, post, site)
    requireNavController().pushController(reportController)
  }

  private suspend fun onArchiveSelected(
    archiveDescriptor: ArchiveDescriptor,
    postDescriptor: PostDescriptor,
    preview: Boolean
  ) {
    val externalArchivePostDescriptor = PostDescriptor.create(
      archiveDescriptor.domain,
      postDescriptor.descriptor.boardCode(),
      postDescriptor.getThreadNo(),
      postDescriptor.postNo
    )

    if (!siteManager.isSiteActive(externalArchivePostDescriptor.siteDescriptor())) {
      siteManager.activateOrDeactivateSite(externalArchivePostDescriptor.siteDescriptor(), true)
    }

    if (preview) {
      showPostsInExternalThread(
        postDescriptor = externalArchivePostDescriptor,
        isPreviewingCatalogThread = false
      )
    } else {
      openExternalThread(
        postDescriptor = externalArchivePostDescriptor,
        scrollToPost = true
      )
    }
  }

  protected suspend fun onThreadSearchDataUpdated(
    chanDescriptor: ChanDescriptor,
    threadSearchData: ThreadSearchData
  ) {
    val prevThreadSearchData = _prevThreadSearchData
    _prevThreadSearchData = threadSearchData

    val wasCreated = prevThreadSearchData?.searchToolbarCreated == true
    val isCreated = threadSearchData.searchToolbarCreated
    val searchQueryChanged = prevThreadSearchData?.searchQuery != threadSearchData.searchQuery

    val wasDestroyedNowDestroyed = !wasCreated && !isCreated
    if (wasDestroyedNowDestroyed) {
      return
    }

    val filterOutPostsNotMatchingSearchQueryEnabled = when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        ChanSettings.catalogSearchMode.get() == ChanSettings.CatalogOrThreadSearchMode.Filter
      }
      is ChanDescriptor.ThreadDescriptor -> {
        ChanSettings.threadSearchMode.get() == ChanSettings.CatalogOrThreadSearchMode.Filter
      }
    }

    val hasMatchedPostDescriptors = threadPostSearchManager.updateSearchQuery(
      chanDescriptor = chanDescriptor,
      postDescriptors = threadLayout.displayingPostDescriptorsInThread,
      searchQuery = threadSearchData.searchQuery
    )

    if (searchQueryChanged) {
      // Need to reload to update posts (mark them + filter out when the setting is enabled)
      threadLayout.presenter.quickReloadFromMemoryCache()
    }

    if (!threadSearchData.searchToolbarVisible
      || filterOutPostsNotMatchingSearchQueryEnabled
      || !hasMatchedPostDescriptors) {
      threadLayout.hideThreadSearchNavigationButtonsView()
    } else {
      threadLayout.showThreadSearchNavigationButtonsView()
    }
  }

  protected fun pushChildController(controller: Controller) {
    when (doubleNavigationController?.determineDoubleControllerType(this)) {
      DoubleControllerType.Left -> doubleNavigationController!!.pushToLeftController(controller)
      DoubleControllerType.Right -> doubleNavigationController!!.pushToRightController(controller)
      null -> requireNavController().pushController(controller)
    }
  }

  protected fun ToolbarOverflowMenuBuilder.withMoreThreadOptions() {
    withCheckableOverflowMenuItem(
      id = ACTION_USE_SCROLLING_TEXT_FOR_THREAD_TITLE,
      stringId = R.string.action_use_scrolling_text_for_thread_title,
      visible = true,
      checked = ChanSettings.scrollingTextForThreadTitles.get(),
      onClick = { item -> onThreadViewOptionClicked(item) }
    )
    withCheckableOverflowMenuItem(
      id = ACTION_MARK_YOUR_POSTS_ON_SCROLLBAR,
      stringId = R.string.action_mark_your_posts_on_scrollbar,
      visible = true,
      checked = ChanSettings.markYourPostsOnScrollbar.get(),
      onClick = { item -> onScrollbarLabelingOptionClicked(item) }
    )
    withCheckableOverflowMenuItem(
      id = ACTION_MARK_REPLIES_TO_YOU_ON_SCROLLBAR,
      stringId = R.string.action_mark_replies_to_your_posts_on_scrollbar,
      visible = true,
      checked = ChanSettings.markRepliesToYourPostOnScrollbar.get(),
      onClick = { item -> onScrollbarLabelingOptionClicked(item) }
    )
    withCheckableOverflowMenuItem(
      id = ACTION_MARK_CROSS_THREAD_REPLIES_ON_SCROLLBAR,
      stringId = R.string.action_mark_cross_thread_quotes_on_scrollbar,
      visible = true,
      checked = ChanSettings.markCrossThreadQuotesOnScrollbar.get(),
      onClick = { item -> onScrollbarLabelingOptionClicked(item) }
    )
    withCheckableOverflowMenuItem(
      id = ACTION_MARK_DELETED_POSTS_ON_SCROLLBAR,
      stringId = R.string.action_mark_deleted_posts_on_scrollbar,
      visible = true,
      checked = ChanSettings.markDeletedPostsOnScrollbar.get(),
      onClick = { item -> onScrollbarLabelingOptionClicked(item) }
    )
    withCheckableOverflowMenuItem(
      id = ACTION_MARK_HOT_POSTS_ON_SCROLLBAR,
      stringId = R.string.action_mark_hot_posts_on_scrollbar,
      visible = true,
      checked = ChanSettings.markHotPostsOnScrollbar.get(),
      onClick = { item -> onScrollbarLabelingOptionClicked(item) }
    )
    withCheckableOverflowMenuItem(
      id = ACTION_GLOBAL_NSFW_MODE,
      stringId = R.string.action_catalog_thread_nsfw_mode,
      visible = true,
      checked = ChanSettings.globalNsfwMode.get(),
      onClick = { item -> onScrollbarLabelingOptionClicked(item) }
    )
    withOverflowMenuItem(
      id = ACTION_THIRD_EYE_SETTINGS,
      stringId = R.string.action_third_eye_settings,
      visible = true,
      onClick = { presentController(ThirdEyeSettingsController(context)) }
    )
  }

  private fun onThreadViewOptionClicked(item: ToolbarMenuCheckableOverflowItem) {
    val clickedItemId = item.id
    if (clickedItemId == ACTION_USE_SCROLLING_TEXT_FOR_THREAD_TITLE) {
      toolbarState.findCheckableOverflowItem(ACTION_USE_SCROLLING_TEXT_FOR_THREAD_TITLE)
        ?.updateChecked(ChanSettings.scrollingTextForThreadTitles.toggle())

      showToast(R.string.restart_the_app)
    }
  }

  private fun onScrollbarLabelingOptionClicked(item: ToolbarMenuCheckableOverflowItem) {
    when (item.id) {
      ACTION_MARK_REPLIES_TO_YOU_ON_SCROLLBAR -> {
        toolbarState.findCheckableOverflowItem(ACTION_MARK_REPLIES_TO_YOU_ON_SCROLLBAR)
          ?.updateChecked(ChanSettings.markRepliesToYourPostOnScrollbar.toggle())
      }
      ACTION_MARK_CROSS_THREAD_REPLIES_ON_SCROLLBAR -> {
        toolbarState.findCheckableOverflowItem(ACTION_MARK_CROSS_THREAD_REPLIES_ON_SCROLLBAR)
          ?.updateChecked(ChanSettings.markCrossThreadQuotesOnScrollbar.toggle())
      }
      ACTION_MARK_YOUR_POSTS_ON_SCROLLBAR -> {
        toolbarState.findCheckableOverflowItem(ACTION_MARK_YOUR_POSTS_ON_SCROLLBAR)
          ?.updateChecked(ChanSettings.markYourPostsOnScrollbar.toggle())
      }
      ACTION_MARK_DELETED_POSTS_ON_SCROLLBAR -> {
        toolbarState.findCheckableOverflowItem(ACTION_MARK_DELETED_POSTS_ON_SCROLLBAR)
          ?.updateChecked(ChanSettings.markDeletedPostsOnScrollbar.toggle())
      }
      ACTION_MARK_HOT_POSTS_ON_SCROLLBAR -> {
        toolbarState.findCheckableOverflowItem(ACTION_MARK_HOT_POSTS_ON_SCROLLBAR)
          ?.updateChecked(ChanSettings.markHotPostsOnScrollbar.toggle())
      }
      ACTION_GLOBAL_NSFW_MODE -> {
        toolbarState.findCheckableOverflowItem(ACTION_GLOBAL_NSFW_MODE)
          ?.updateChecked(ChanSettings.globalNsfwMode.toggle())
      }
    }

    threadLayout.presenter.quickReloadFromMemoryCache()
  }

  data class ShowThreadOptions(
    val switchToThreadController: Boolean,
    val pushControllerWithAnimation: Boolean
  )

  data class ShowCatalogOptions(
    val switchToCatalogController: Boolean,
    val withAnimation: Boolean
  )

  protected data class ThreadSearchData(
    val searchToolbarCreated: Boolean,
    val searchToolbarVisible: Boolean,
    val searchQuery: String?
  )

  companion object {
    private const val TAG = "ThreadController"

    const val ACTION_THREAD_MORE_OPTIONS = 9010
    private const val ACTION_USE_SCROLLING_TEXT_FOR_THREAD_TITLE = 9100
    private const val ACTION_MARK_YOUR_POSTS_ON_SCROLLBAR = 9101
    private const val ACTION_MARK_REPLIES_TO_YOU_ON_SCROLLBAR = 9102
    private const val ACTION_MARK_CROSS_THREAD_REPLIES_ON_SCROLLBAR = 9103
    private const val ACTION_MARK_DELETED_POSTS_ON_SCROLLBAR = 9104
    private const val ACTION_MARK_HOT_POSTS_ON_SCROLLBAR = 9105
    private const val ACTION_GLOBAL_NSFW_MODE = 9106
    private const val ACTION_THIRD_EYE_SETTINGS = 9107
  }
}

interface ThreadControllerCallbacks {
  fun openFiltersController(chanFilterMutable: ChanFilterMutable)
}