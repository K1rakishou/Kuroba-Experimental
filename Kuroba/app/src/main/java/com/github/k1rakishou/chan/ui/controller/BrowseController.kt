package com.github.k1rakishou.chan.ui.controller

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.concurrency.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.CurrentFocusedController
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.WebViewTaskManager
import com.github.k1rakishou.chan.core.presenter.BrowsePresenter
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.features.KurobaWebUrlRouter
import com.github.k1rakishou.chan.features.archive.BoardArchiveController
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.features.settings.AppSettingsController
import com.github.k1rakishou.chan.features.settings.SettingsScreenKey
import com.github.k1rakishou.chan.features.setup.boards.selection.BoardSelectionController
import com.github.k1rakishou.chan.features.setup.site.setup.SitesSetupController
import com.github.k1rakishou.chan.features.toolbar.HamburgMenuItem
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarState
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuCheckableOverflowItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuOverflowItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarOverflowMenuBuilder
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarContentState
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarInlineContent
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarStateKind
import com.github.k1rakishou.chan.features.view.media.MediaLocation
import com.github.k1rakishou.chan.features.view.media.MediaViewerActivity
import com.github.k1rakishou.chan.features.webview.WebViewTaskController
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.task.AbstractWebViewTask
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController.ReplyAutoCloseListener
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController.SlideChangeListener
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.ui.controller.base.ui.NavigationControllerContainerLayout
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaComposeDialogController
import com.github.k1rakishou.chan.ui.controller.navigation.SplitNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.chan.ui.layout.ThreadLayout
import com.github.k1rakishou.chan.ui.layout.ThreadLayout.ThreadLayoutCallback
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.hasPostNotificationsPermission
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.parameters.BoardPostViewMode
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

class BrowseController(
  context: Context,
  mainControllerCallbacks: MainControllerCallbacks
) : ThreadController(context, mainControllerCallbacks),
  ThreadLayoutCallback,
  BrowsePresenter.Callback,
  SlideChangeListener,
  ReplyAutoCloseListener {

  @Inject
  lateinit var presenter: BrowsePresenter
  @Inject
  lateinit var historyNavigationManagerLazy: Lazy<HistoryNavigationManager>
  @Inject
  lateinit var siteResolverLazy: Lazy<SiteResolver>
  @Inject
  lateinit var webViewTaskManagerLazy: Lazy<WebViewTaskManager>
  @Inject
  lateinit var runtimePermissionsHelper: RuntimePermissionsHelper
  @Inject
  lateinit var kurobaWebUrlRouter: KurobaWebUrlRouter

  private val historyNavigationManager: HistoryNavigationManager
    get() = historyNavigationManagerLazy.get()
  private val siteResolver: SiteResolver
    get() = siteResolverLazy.get()
  private val webViewTaskManager: WebViewTaskManager
    get() = webViewTaskManagerLazy.get()

  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  private var updateToolbarTitleJob: Job? = null

  override val threadControllerType: ThreadControllerType
    get() = ThreadControllerType.Catalog
  override val kurobaToolbarState: KurobaToolbarState
    get() = toolbarState

  val catalogControllerToolbarState: KurobaToolbarState
    get() = kurobaToolbarStateManager.getOrCreate(this, BrowseController.catalogControllerKey)

  override val controllerKey: ControllerKey
    get() = BrowseController.catalogControllerKey

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    val containerLayout = inflate(context, R.layout.controller_browse)
    val container = containerLayout.findViewById<NavigationControllerContainerLayout>(R.id.browse_controller_container)
    container.initBrowseControllerTracker(this, requireNavController())
    container.addView(view)
    view = container

    // Navigation
    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags(
        swipeable = false,
        hasBack = false,
        hasDrawer = true
      )
    )

    buildMenu()

    // Presenter
    presenter.create(controllerScope, this)

    // Initialization
    serializedCoroutineExecutor = SerializedCoroutineExecutor(controllerScope)

    threadLayout.setBoardPostViewMode(kurobaSettings.application.boardPostViewMode.readBlocking())

    serializedCoroutineExecutor.post {
      val catalogSortingOrder = PostsFilter.CatalogSortingOrder.current(kurobaSettings)

      threadLayout.presenter.setOrder(
        catalogSortingOrder = catalogSortingOrder,
        isManuallyChangedOrder = false
      )
    }

    controllerScope.launch {
      kurobaWebUrlRouter.routerEventFlow
        .collect { routerEvent -> handleRouterEvent(routerEvent) }
    }

    controllerScope.launch {
      webViewTaskManager.taskQueue.collect { webViewTask ->
        presentWebViewTaskAndHandleResult(
          webViewTask = webViewTask,
          webViewTaskName = webViewTask::class.java.simpleName
        )
      }
    }

    controllerScope.launch {
      combine(
        toolbarState.catalogSearch.listenForSearchCreationUpdates(),
        toolbarState.catalogSearch.listenForSearchVisibilityUpdates(),
        toolbarState.catalogSearch.listenForSearchQueryUpdates()
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
      toolbarState.catalogSearch.showFoundItemsAsPopupClicked
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
          toolbarState.catalogSearch.updateActiveSearchInfo(
            currentIndex = activeSearchToolbarInfo.currentIndex,
            totalFound = activeSearchToolbarInfo.totalFound
          )
        }
    }

    controllerScope.launch {
      requestApi33NotificationsPermissionOnce()
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    updateToolbarTitleJob?.cancel()
    updateToolbarTitleJob = null

    presenter.destroy()
  }

  override fun showSitesNotSetup() {
    super.showSitesNotSetup()

    catalogControllerToolbarState.catalog.updateTitle(
      newTitle = ToolbarText.Id(R.string.browse_controller_title_app_setup),
      newSubTitle = ToolbarText.Id(R.string.browse_controller_subtitle)
    )
  }

  suspend fun setCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor) {
    Logger.d(TAG, "setCatalog($catalogDescriptor)")

    presenter.loadCatalog(catalogDescriptor)
  }

  suspend fun loadWithDefaultBoard() {
    Logger.d(TAG, "loadWithDefaultBoard()")

    presenter.loadWithDefaultBoard()
  }

  override suspend fun loadCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor) {
    controllerScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "loadCatalog($catalogDescriptor)")

      threadLayout.presenter.bindChanDescriptor(catalogDescriptor as ChanDescriptor)

      updateMenuItems()
    }
  }

  override fun updateToolbarTitle(catalogDescriptor: ChanDescriptor.ICatalogDescriptor) {
    updateToolbarTitleJob?.cancel()
    updateToolbarTitleJob = controllerScope.launch(Dispatchers.Main.immediate) {
      coroutineContext[Job.Key]?.invokeOnCompletion { updateToolbarTitleJob = null }

      boardManager.awaitUntilInitialized()

      when (catalogDescriptor) {
        is CatalogDescriptor -> {
          val boardDescriptor = catalogDescriptor.boardDescriptor

          val board = boardManager.byBoardDescriptor(boardDescriptor)

          catalogControllerToolbarState.catalog.updateTitle(
            newTitle = ToolbarText.String("/" + boardDescriptor.boardCode + "/"),
            newSubTitle = board?.let { chanBoard -> ToolbarText.String(chanBoard.name ?: "") }
          )
        }

        is ChanDescriptor.CompositeCatalogDescriptor -> {
          catalogControllerToolbarState.catalog.updateTitle(
            newTitle = ToolbarText.Id(R.string.composite_catalog),
            newSubTitle = ToolbarText.Id(R.string.browse_controller_composite_catalog_subtitle_loading)
          )

          val newTitle = presenter.getCompositeCatalogNavigationTitle(catalogDescriptor)

          val compositeCatalogSubTitle = ToolbarInlineContent.getCompositeCatalogNavigationSubtitle(
            compositeCatalogDescriptor = catalogDescriptor
          )

          catalogControllerToolbarState.catalog.updateTitle(
            newTitle = ToolbarText.String(newTitle ?: ""),
            newSubTitle = ToolbarText.Spanned(compositeCatalogSubTitle)
          )
        }
      }
    }
  }

  override suspend fun showCatalogWithoutFocusing(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean) {
    controllerScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "showCatalogWithoutFocusing($catalogDescriptor, $animated)")

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
      Logger.d(TAG, "showBoard($catalogDescriptor, $animated)")

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

      setCatalog(catalogDescriptor)
    }
  }

  override suspend fun showPostsInExternalThread(
    postDescriptor: PostDescriptor,
    isPreviewingCatalogThread: Boolean
  ) {
    showPostsInExternalThreadHelper.showPostsInExternalThread(postDescriptor, isPreviewingCatalogThread)
  }

  override suspend fun openExternalThread(postDescriptor: PostDescriptor, scrollToPost: Boolean) {
    val descriptor = chanDescriptor
      ?: return

    openExternalThreadHelper.openExternalThread(
      currentChanDescriptor = descriptor,
      postDescriptor = postDescriptor,
      scrollToPost = scrollToPost
    ) { threadDescriptor ->
      controllerScope.launch { showThread(descriptor = threadDescriptor, animated = true) }
    }
  }

  fun getViewThreadController(): ViewThreadController? {
    var splitNav: SplitNavigationController? = null
    var slideNav: ThreadSlideController? = null

    if (doubleNavigationController is SplitNavigationController) {
      splitNav = doubleNavigationController as SplitNavigationController?
    }

    if (doubleNavigationController is ThreadSlideController) {
      slideNav = doubleNavigationController as ThreadSlideController?
    }

    return when {
      splitNav != null -> {
        val navigationController = splitNav.rightController() as StyledToolbarNavigationController?
        navigationController?.topController as? ViewThreadController
      }
      slideNav != null -> {
        slideNav.rightController()
      }
      else -> null
    }
  }

  // Creates or updates the target ThreadViewController
  // This controller can be in various places depending on the layout
  // We dynamically search for it
  override suspend fun showThread(descriptor: ThreadDescriptor, animated: Boolean) {
    showThreadInternal(
      descriptor = descriptor,
      showThreadOptions = ShowThreadOptions(
        switchToThreadController = true,
        pushControllerWithAnimation = animated
      )
    )
  }

  override fun onShowLoading() {
    catalogControllerToolbarState.catalog.updateTitle(
      newTitle = ToolbarText.Id(com.github.k1rakishou.chan.R.string.loading),
      newSubTitle = null
    )
  }

  override fun onShowError() {
    super.onShowError()

    catalogControllerToolbarState.catalog.updateTitle(
      newTitle = ToolbarText.Id(R.string.catalog_loading_error_title),
      newSubTitle = null
    )
  }

  override fun onShowPosts(chanDescriptor: ChanDescriptor) {
    super.onShowPosts(chanDescriptor)

    updateToolbarTitle(chanDescriptor as ChanDescriptor.ICatalogDescriptor)
  }

  override suspend fun showThreadWithoutFocusing(descriptor: ThreadDescriptor, animated: Boolean) {
    showThreadInternal(
      descriptor = descriptor,
      showThreadOptions = ShowThreadOptions(
        switchToThreadController = false,
        pushControllerWithAnimation = animated
      )
    )
  }

  override fun onThreadLayoutStateChanged(state: ThreadLayout.State) {
    catalogControllerToolbarState.catalog.updateToolbarContentState(ToolbarContentState.from(state))
  }

  override fun onLostFocus(nowFocused: ThreadControllerType) {
    super.onLostFocus(nowFocused)
    check(nowFocused == threadControllerType) { "Unexpected controllerType: $nowFocused" }

    catalogControllerToolbarState.invokeAfterTransitionFinished {
      if (catalogControllerToolbarState.isInReplyMode()) {
        popUntil(withAnimation = false) { topToolbar ->
          if (topToolbar.kind != ToolbarStateKind.Catalog) {
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

    if (chanDescriptor != null && historyNavigationManager.isInitialized) {
      if (threadLayout.presenter.chanThreadLoadingState == ThreadPresenter.ChanThreadLoadingState.Loaded) {
        controllerScope.launch { historyNavigationManager.moveNavElementToTop(chanDescriptor!!) }
      }
    }

    currentOpenedDescriptorStateManager.updateCurrentFocusedController(CurrentFocusedController.Catalog)
  }

  private suspend fun showThreadInternal(descriptor: ThreadDescriptor, showThreadOptions: ShowThreadOptions) {
    Logger.d(TAG, "showThread($descriptor, $showThreadOptions)")

    // The target ThreadViewController is in a split nav
    // (BrowseController -> ToolbarNavigationController -> SplitNavigationController)
    var splitNav: SplitNavigationController? = null

    // The target ThreadViewController is in a slide nav
    // (BrowseController -> SlideController -> ToolbarNavigationController)
    var slideNav: ThreadSlideController? = null
    if (doubleNavigationController is SplitNavigationController) {
      splitNav = doubleNavigationController as SplitNavigationController?
    }

    if (doubleNavigationController is ThreadSlideController) {
      slideNav = doubleNavigationController as ThreadSlideController?
    }

    when {
      splitNav != null -> {
        // Create a threadview inside a toolbarnav in the right part of the split layout
        if (splitNav.rightController() is StyledToolbarNavigationController) {
          val navigationController = splitNav.rightController() as StyledToolbarNavigationController
          val viewThreadController = navigationController
            .lastChildControllerOrNull<ViewThreadController> { controller -> controller is ViewThreadController }

          if (viewThreadController != null) {
            viewThreadController.loadThread(descriptor)
            viewThreadController.onShow()
            viewThreadController.onGainedFocus(ThreadControllerType.Thread)
          }
        } else {
          val navigationController = StyledToolbarNavigationController(context)
          splitNav.updateRightController(navigationController, showThreadOptions.pushControllerWithAnimation)
          val viewThreadController = ViewThreadController(context, mainControllerCallbacks, descriptor)
          navigationController.pushController(viewThreadController, false)
          viewThreadController.onGainedFocus(ThreadControllerType.Thread)
        }

        splitNav.switchToRightController(
          showThreadOptions.pushControllerWithAnimation
        )
      }
      slideNav != null -> {
        // Create a threadview in the right part of the slide nav *without* a toolbar
        if (slideNav.rightController() is ViewThreadController) {
          (slideNav.rightController() as ViewThreadController).loadThread(descriptor)
          (slideNav.rightController() as ViewThreadController).onShow()
        } else {
          val viewThreadController = ViewThreadController(
            context,
            mainControllerCallbacks,
            descriptor
          )

          slideNav.updateRightController(
            viewThreadController,
            showThreadOptions.pushControllerWithAnimation
          )
        }

        if (showThreadOptions.switchToThreadController) {
          slideNav.switchToRightController(
            showThreadOptions.pushControllerWithAnimation
          )
        }
      }
      else -> {
        // the target ThreadNav must be pushed to the parent nav controller
        // (BrowseController -> ToolbarNavigationController)
        val viewThreadController = ViewThreadController(
          context,
          mainControllerCallbacks,
          descriptor
        )

        navigationController!!.pushController(
          viewThreadController,
          showThreadOptions.pushControllerWithAnimation
        )
      }
    }
  }

  private suspend fun showCatalogInternal(
    catalogDescriptor: ChanDescriptor.ICatalogDescriptor,
    showCatalogOptions: ShowCatalogOptions
  ) {
    Logger.d(TAG, "showCatalogInternal($catalogDescriptor, $showCatalogOptions)")

    // The target ThreadViewController is in a split nav
    // (BrowseController -> ToolbarNavigationController -> SplitNavigationController)
    val splitNav = if (doubleNavigationController is SplitNavigationController) {
      doubleNavigationController as SplitNavigationController?
    } else {
      null
    }

    // The target ThreadViewController is in a slide nav
    // (BrowseController -> SlideController -> ToolbarNavigationController)
    val slideNav = if (doubleNavigationController is ThreadSlideController) {
      doubleNavigationController as ThreadSlideController?
    } else {
      null
    }

    // Do nothing when split navigation is enabled because both controllers are always visible
    // so we don't need to switch between left and right controllers
    if (splitNav == null) {
      if (slideNav != null) {
        if (showCatalogOptions.switchToCatalogController) {
          slideNav.switchToLeftController(showCatalogOptions.withAnimation)
        }
      } else {
        if (navigationController != null) {
          // We wouldn't want to pop BrowseController when opening a board
          if (requireNavController().topController !is BrowseController) {
            requireNavController().popController(showCatalogOptions.withAnimation)
          }
        }
      }
    }

    setCatalog(catalogDescriptor)
  }

  private fun updateMenuItems() {
    toolbarState.findOverflowItem(ACTION_BOARD_ARCHIVE)?.let { menuItem ->
      val supportsArchive = siteSupportsBuiltInBoardArchive()
      menuItem.updateVisibility(supportsArchive)
    }

    toolbarState.findOverflowItem(ACTION_LOAD_WHOLE_COMPOSITE_CATALOG)?.let { menuItem ->
      val isCompositeCatalog =
        threadLayout.presenter.currentChanDescriptor is ChanDescriptor.CompositeCatalogDescriptor

      menuItem.updateVisibility(isCompositeCatalog)
    }

    toolbarState.findOverflowItem(ACTION_OPEN_BROWSER)?.let { menuItem ->
      val isNotCompositeCatalog =
        threadLayout.presenter.currentChanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor

      menuItem.updateVisibility(isNotCompositeCatalog)
    }

    toolbarState.findOverflowItem(ACTION_OPEN_UNLIMITED_CATALOG_PAGE)?.let { menuItem ->
      val isUnlimitedCatalog = threadLayout.presenter.isUnlimitedOrCompositeCatalog
        && threadLayout.presenter.currentChanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor

      menuItem.updateVisibility(isUnlimitedCatalog)
    }

    toolbarState.findOverflowItem(ACTION_SHARE)?.let { menuItem ->
      val isNotCompositeCatalog =
        threadLayout.presenter.currentChanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor

      menuItem.updateVisibility(isNotCompositeCatalog)
    }
  }

  private fun siteSupportsBuiltInBoardArchive(): Boolean {
    val chanDescriptor = threadLayout.presenter.currentChanDescriptor
    if (chanDescriptor == null) {
      return false
    }

    if (chanDescriptor !is CatalogDescriptor) {
      return false
    }

    return chanDescriptor.siteDescriptor().is4chan() || chanDescriptor.siteDescriptor().isDvach()
  }

  private suspend fun presentWebViewTaskAndHandleResult(
    webViewTask: AbstractWebViewTask,
    webViewTaskName: String
  ): Boolean {
    presentController(
      WebViewTaskController(
        context = context,
        webViewTask = webViewTask
      )
    )

    val cookieResult = webViewTask.invokerWaiter.await()
    when (cookieResult) {
      is WebViewTaskResult.Result -> {
        val message = getString(R.string.firewall_check_success, webViewTaskName)
        AppModuleAndroidUtils.showToast(context, message, Toast.LENGTH_LONG)
      }

      WebViewTaskResult.Canceled -> {
        val message = getString(R.string.firewall_check_canceled, webViewTaskName)
        AppModuleAndroidUtils.showToast(context, message, Toast.LENGTH_LONG)
      }

      is WebViewTaskResult.Error -> {
        val errorMsg = cookieResult.exception.errorMessageOrClassName()
        val message = getString(R.string.firewall_check_failure, webViewTaskName, errorMsg)
        AppModuleAndroidUtils.showToast(context, message, Toast.LENGTH_LONG)
      }
    }

    return cookieResult.isSuccess
  }

  @SuppressLint("InlinedApi")
  private fun requestApi33NotificationsPermissionOnce() {
    if (hasPostNotificationsPermission(context)) {
      return
    }

    runtimePermissionsHelper.requestPermission(
      Manifest.permission.POST_NOTIFICATIONS
    ) { granted ->
      if (granted) {
        return@requestPermission
      }

      dialogFactory.createSimpleInformationDialog(
        context = context,
        titleText = context.getString(R.string.api_33_android_13_notifications_permission_title),
        descriptionText = context.getString(R.string.api_33_android_13_notifications_permission_descriptor),
      )
    }
  }

  private fun openBoardSelectionController() {
     val boardSelectionController = BoardSelectionController(
      context = context,
      callback = object : BoardSelectionController.UserSelectionListener {
        override fun onOpenSitesSettingsClicked() {
          pushChildController(SitesSetupController(context))
        }

        override fun onSiteSelected(siteDescriptor: SiteDescriptor) {
          pushChildController(
            AppSettingsController(
              context = context,
              params = AppSettingsController.Params.createForInitialScreen(
                screenKey = SettingsScreenKey.Site(siteDescriptor)
              )
            )
          )
        }

        override fun onCatalogSelected(catalogDescriptor: ChanDescriptor.ICatalogDescriptor) {
          if (currentOpenedDescriptorStateManager.currentCatalogDescriptor == catalogDescriptor) {
            return
          }

          controllerScope.launch(Dispatchers.Main.immediate) { loadCatalog(catalogDescriptor) }
        }
      })

    requireNavController().pushController(boardSelectionController)
  }

  @Suppress("MoveLambdaOutsideParentheses")
  private fun buildMenu() {
    val modeStringId = when (kurobaSettings.application.boardPostViewMode.readBlocking()) {
      BoardPostViewMode.List -> R.string.action_switch_catalog_grid
      BoardPostViewMode.Grid -> R.string.action_switch_catalog_stagger
      BoardPostViewMode.Stagger -> R.string.action_switch_board
    }

    val supportsArchive = siteSupportsBuiltInBoardArchive()
    val isCompositeCatalog = threadLayout.presenter.currentChanDescriptor is ChanDescriptor.CompositeCatalogDescriptor
    val isUnlimitedCatalog = threadLayout.presenter.isUnlimitedCatalog && !isCompositeCatalog

    toolbarState.enterCatalogMode(
      leftItem = HamburgMenuItem(
        onClick = {
          globalUiStateHolder.updateDrawerState {
            openDrawer()
          }
        }
      ),
      onMainContentClick = {
        if (!siteManager.areSitesSetup()) {
          pushChildController(SitesSetupController(context))
        } else {
          openBoardSelectionController()
        }
      },
      menuBuilder = {
        withMenuItem(drawableId = R.drawable.ic_search_white_24dp, onClick = { item -> searchClicked(item) })
        withMenuItem(
          forceEnabled = true,
          drawableId = R.drawable.ic_refresh_white_24dp,
          onClick = { item -> reloadClicked(item) }
        )

        withOverflowMenu {
          withOverflowMenuItem(id = ACTION_REPLY, stringId = R.string.action_reply, onClick = { item -> replyClicked(item) })
          withOverflowMenuItem(id = ACTION_CHANGE_VIEW_MODE, stringId = modeStringId, onClick = { item -> viewModeClicked(item) })

          addSortMenu()
          addDevMenu()

          withOverflowMenuItem(
            id = ACTION_LOAD_WHOLE_COMPOSITE_CATALOG,
            stringId = R.string.action_rest_composite_catalog,
            visible = isCompositeCatalog,
            onClick = { controllerScope.launch { threadLayout.presenter.loadWholeCompositeCatalog() } }
          )
          withOverflowMenuItem(
            id = ACTION_CATALOG_ALBUM,
            stringId = R.string.action_catalog_album,
            onClick = { threadLayout.presenter.showAlbum() }
          )
          withOverflowMenuItem(
            id = ACTION_OPEN_BROWSER,
            stringId = R.string.action_open_browser,
            visible = !isCompositeCatalog,
            onClick = { item -> openBrowserClicked(item) }
          )
          withOverflowMenuItem(
            id = ACTION_OPEN_CATALOG_OR_THREAD_BY_IDENTIFIER,
            stringId = R.string.action_open_catalog_or_thread_by_identifier,
            onClick = { item -> openCatalogOrThreadByIdentifier(item) }
          )
          withOverflowMenuItem(
            id = ACTION_OPEN_MEDIA_BY_URL,
            stringId = R.string.action_open_media_by_url,
            onClick = { item -> openMediaByUrl(item) }
          )
          withOverflowMenuItem(
            id = ACTION_OPEN_UNLIMITED_CATALOG_PAGE,
            stringId = R.string.action_open_catalog_page,
            visible = isUnlimitedCatalog,
            onClick = { openCatalogPageClicked() }
          )
          withOverflowMenuItem(
            id = ACTION_SHARE,
            stringId = R.string.action_share,
            onClick = { item -> shareClicked(item) }
          )
          withOverflowMenuItem(
            id = ACTION_BOARD_ARCHIVE,
            stringId = R.string.action_board_archive,
            visible = supportsArchive,
            onClick = { viewBoardArchiveClicked() }
          )
          withOverflowMenuItem(
            id = ACTION_VIEW_REMOVED_THREADS,
            stringId = R.string.action_view_removed_threads,
            onClick = { threadLayout.presenter.showRemovedPostsDialog() }
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

  @Suppress("MoveLambdaOutsideParentheses")
  private fun ToolbarOverflowMenuBuilder.addDevMenu() {
    withOverflowMenuItem(
      id = ACTION_DEV_MENU,
      stringId = R.string.action_browse_dev_menu,
      visible = AppModuleAndroidUtils.isDevBuild,
      builder = {
        withOverflowMenuItem(
          id = DEV_BOOKMARK_EVERY_THREAD,
          stringId = R.string.dev_bookmark_every_thread,
          onClick = {
            controllerScope.launch {
              presenter.bookmarkEveryThread(threadLayout.presenter.currentChanDescriptor)
            }
          }
        )
        withOverflowMenuItem(
          id = DEV_CACHE_EVERY_THREAD,
          stringId = R.string.dev_cache_every_thread,
          onClick = { presenter.cacheEveryThreadClicked(threadLayout.presenter.currentChanDescriptor) }
        )
      }
    )
  }

  @Suppress("MoveLambdaOutsideParentheses")
  private fun ToolbarOverflowMenuBuilder.addSortMenu() {
    val currentSortingOrder = PostsFilter.CatalogSortingOrder.current(kurobaSettings)
    val groupId = "catalog_sort"

    withOverflowMenuItem(
      id = ACTION_SORT,
      stringId = R.string.action_sort,
      groupId = "catalog_sort",
      builder = {
        withCheckableOverflowMenuItem(
          id = SORT_MODE_BUMP,
          stringId = R.string.order_bump,
          visible = true,
          checked = currentSortingOrder == PostsFilter.CatalogSortingOrder.BUMP,
          groupId = groupId,
          value = PostsFilter.CatalogSortingOrder.BUMP,
          onClick = { subItem -> onSortItemClicked(subItem) }
        )
        withCheckableOverflowMenuItem(
          id = SORT_MODE_REPLY,
          stringId = R.string.order_reply,
          visible = true,
          checked = currentSortingOrder == PostsFilter.CatalogSortingOrder.REPLY,
          groupId = groupId,
          value = PostsFilter.CatalogSortingOrder.REPLY,
          onClick = { subItem -> onSortItemClicked(subItem) }
        )
        withCheckableOverflowMenuItem(
          id = SORT_MODE_IMAGE,
          stringId = R.string.order_image,
          visible = true,
          checked = currentSortingOrder == PostsFilter.CatalogSortingOrder.IMAGE,
          groupId = groupId,
          value = PostsFilter.CatalogSortingOrder.IMAGE,
          onClick = { subItem -> onSortItemClicked(subItem) }
        )
        withCheckableOverflowMenuItem(
          id = SORT_MODE_NEWEST,
          stringId = R.string.order_newest,
          visible = true,
          checked = currentSortingOrder == PostsFilter.CatalogSortingOrder.NEWEST,
          groupId = groupId,
          value = PostsFilter.CatalogSortingOrder.NEWEST,
          onClick = { subItem -> onSortItemClicked(subItem) }
        )
        withCheckableOverflowMenuItem(
          id = SORT_MODE_OLDEST,
          stringId = R.string.order_oldest,
          visible = true,
          checked = currentSortingOrder == PostsFilter.CatalogSortingOrder.OLDEST,
          groupId = groupId,
          value = PostsFilter.CatalogSortingOrder.OLDEST,
          onClick = { subItem -> onSortItemClicked(subItem) }
        )
        withCheckableOverflowMenuItem(
          id = SORT_MODE_MODIFIED,
          stringId = R.string.order_modified,
          visible = true,
          checked = currentSortingOrder == PostsFilter.CatalogSortingOrder.MODIFIED,
          groupId = groupId,
          value = PostsFilter.CatalogSortingOrder.MODIFIED,
          onClick = { subItem -> onSortItemClicked(subItem) }
        )
        withCheckableOverflowMenuItem(
          id = SORT_MODE_ACTIVITY,
          stringId = R.string.order_activity,
          visible = true,
          checked = currentSortingOrder == PostsFilter.CatalogSortingOrder.ACTIVITY,
          groupId = groupId,
          value = PostsFilter.CatalogSortingOrder.ACTIVITY,
          onClick = { subItem -> onSortItemClicked(subItem) }
        )
      }
    )
  }

  private fun onSortItemClicked(subItem: ToolbarMenuCheckableOverflowItem) {
    serializedCoroutineExecutor.post {
      val catalogSortingOrder = subItem.value as? PostsFilter.CatalogSortingOrder
        ?: return@post

      kurobaSettings.application.boardOrder.write(catalogSortingOrder.orderName)
      toolbarState.checkOrUncheckItem(subItem, true)

      val presenter = threadLayout.presenter

      val currentChanDescriptor = chanDescriptor
      if (
        currentChanDescriptor is ChanDescriptor.CompositeCatalogDescriptor &&
        !PostsFilter.CatalogSortingOrder.current(kurobaSettings).isBump &&
        !presenter.isCompositeCatalogFullyLoaded(currentChanDescriptor)
      ) {
        presenter.loadWholeCompositeCatalog()
      } else {
        presenter.setOrder(catalogSortingOrder, isManuallyChangedOrder = true)
      }
    }
  }

  private fun searchClicked(item: ToolbarMenuItem) {
    val presenter = threadLayout.presenter
    if (!presenter.isBoundAndCached || chanDescriptor == null) {
      return
    }

    toolbarState.enterCatalogSearchMode()
  }

  private fun reloadClicked(item: ToolbarMenuItem) {
    val presenter = threadLayout.presenter
    if (!presenter.isBound) {
      return
    }

    presenter.normalLoad(
      showLoading = true,
      chanCacheUpdateOptions = ChanCacheUpdateOptions.UpdateCache
    )

    item.spinItemOnce()
  }

  private fun replyClicked(item: ToolbarMenuOverflowItem) {
    val presenter = threadLayout.presenter
    if (!presenter.isBoundAndCached || chanDescriptor == null) {
      return
    }

    threadLayout.openOrCloseReply(true)
  }

  private fun viewModeClicked(item: ToolbarMenuOverflowItem) {
    val presenter = threadLayout.presenter
    if (!presenter.isBoundAndCached || chanDescriptor == null) {
      return
    }

    var postViewMode = kurobaSettings.application.boardPostViewMode.readBlocking()

    postViewMode = when (postViewMode) {
      BoardPostViewMode.List -> BoardPostViewMode.Grid
      BoardPostViewMode.Grid -> BoardPostViewMode.Stagger
      BoardPostViewMode.Stagger -> BoardPostViewMode.List
    }

    kurobaSettings.application.boardPostViewMode.writeAsync(postViewMode)

    val viewModeText = when (postViewMode) {
      BoardPostViewMode.List -> R.string.action_switch_catalog_grid
      BoardPostViewMode.Grid -> R.string.action_switch_catalog_stagger
      BoardPostViewMode.Stagger -> R.string.action_switch_board
    }

    item.updateMenuText(getString(viewModeText))
    threadLayout.setBoardPostViewMode(postViewMode)
  }

  private fun openBrowserClicked(item: ToolbarMenuOverflowItem) {
    handleShareOrOpenInBrowser(false)
  }

  private fun openCatalogOrThreadByIdentifier(item: ToolbarMenuOverflowItem) {
    if (chanDescriptor == null) {
      return
    }

    dialogFactory.createSimpleDialogWithInput(
      context = context,
      titleTextId = R.string.browse_controller_enter_identifier,
      descriptionTextId = R.string.browse_controller_enter_identifier_description,
      onValueEntered = { input: String -> openCatalogOrThreadByIdentifierInternal(input) },
      inputType = DialogFactory.DialogInputType.String
    )
  }

  private fun openMediaByUrl(item: ToolbarMenuOverflowItem) {
    if (chanDescriptor == null) {
      return
    }

    dialogFactory.createSimpleDialogWithInput(
      context = context,
      titleTextId = R.string.browse_controller_enter_media_url,
      onValueEntered = { input: String ->
        val mediaUrl = input.toHttpUrlOrNull()
        if (mediaUrl == null) {
          showToast(getString(R.string.browse_controller_enter_media_url_error, input))
          return@createSimpleDialogWithInput
        }

        MediaViewerActivity.mixedMedia(context, listOf(MediaLocation.Remote(input)))
      },
      inputType = DialogFactory.DialogInputType.String
    )
  }

  private fun openCatalogOrThreadByIdentifierInternal(input: String) {
    controllerScope.launch {
      val chanDescriptorResult = siteResolver.resolveChanDescriptorForUrl(input)
      if (chanDescriptorResult == null) {
        if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
          showToast(
            getString(R.string.open_by_board_code_or_thread_no_composite_catalog_error, input),
            Toast.LENGTH_LONG
          )

          return@launch
        }

        val currentCatalogDescriptor = chanDescriptor as? CatalogDescriptor
        if (currentCatalogDescriptor == null) {
          return@launch
        }

        val inputTrimmed = input.trim()

        val asThreadNo = inputTrimmed.toLongOrNull()
        if (asThreadNo != null && asThreadNo > 0) {
          val threadDescriptor = currentCatalogDescriptor.toThreadDescriptor(threadNo = asThreadNo)
          showThread(threadDescriptor, false)
          return@launch
        }

        val asBoardCode = inputTrimmed.replace("/", "")
        if (asBoardCode.all { ch -> ch.isLetter() }) {
          val boardDescriptor = BoardDescriptor.create(
            siteDescriptor = currentCatalogDescriptor.siteDescriptor(),
            boardCode = asBoardCode
          )

          showCatalog(CatalogDescriptor.create(boardDescriptor), false)
          return@launch
        }

        // fallthrough
      } else {
        val resolvedChanDescriptor = chanDescriptorResult.chanDescriptor
        if (resolvedChanDescriptor is ChanDescriptor.ICatalogDescriptor) {
          showCatalog(resolvedChanDescriptor.catalogDescriptor(), false)
          return@launch
        }

        if (resolvedChanDescriptor is ThreadDescriptor) {
          if (chanDescriptorResult.markedPostNo > 0L) {
            chanThreadViewableInfoManager.update(
              chanDescriptor = resolvedChanDescriptor,
              createEmptyWhenNull = true
            ) { ctvi -> ctvi.markedPostNo = chanDescriptorResult.markedPostNo }
          }

          showThread(resolvedChanDescriptor, false)
          return@launch
        }

        // fallthrough
      }

      showToast(
        getString(R.string.open_link_not_matched, input),
        Toast.LENGTH_LONG
      )
    }
  }

  private fun openThreadByIdInternal(input: String) {
    controllerScope.launch(Dispatchers.Main.immediate) {
      val threadNo = input.toLong()
      if (threadNo <= 0) {
        showToast(getString(R.string.browse_controller_error_thread_id_negative_or_zero))
        return@launch
      }

      try {
        val threadDescriptor = ThreadDescriptor.create(
          siteName = chanDescriptor!!.siteName(),
          boardCode = chanDescriptor!!.boardCode(),
          threadNo = input.toLong()
        )

        showThread(threadDescriptor, true)
      } catch (e: NumberFormatException) {
        showToast(context.getString(R.string.browse_controller_error_parsing_thread_id))
      }
    }
  }

  private fun shareClicked(item: ToolbarMenuOverflowItem) {
    handleShareOrOpenInBrowser(true)
  }

  private fun viewBoardArchiveClicked() {
    val boardArchiveController = BoardArchiveController(
      context = context,
      catalogDescriptor = chanDescriptor!! as CatalogDescriptor,
      onThreadClicked = { threadDescriptor ->
        controllerScope.launch { showThread(threadDescriptor, animated = true) }
      }
    )

    threadLayout.pushController(boardArchiveController)
  }

  private fun openCatalogPageClicked() {
    dialogFactory.createSimpleDialogWithInput(
      context = context,
      inputType = DialogFactory.DialogInputType.Integer,
      titleText = getString(R.string.browse_controller_enter_page_number),
      onValueEntered = { pageString ->
        val page = pageString.toIntOrNull()
        if (page == null) {
          showToast(getString(R.string.browse_controller_failed_to_parse_page_number, pageString))
          return@createSimpleDialogWithInput
        }

        threadLayout.presenter.loadCatalogPage(overridePage = page)
      }
    )
  }

  private fun upClicked(item: ToolbarMenuOverflowItem) {
    threadLayout.presenter.scrollTo(0)
  }

  private fun downClicked(item: ToolbarMenuOverflowItem) {
    threadLayout.presenter.scrollTo(-1)
  }

  override fun onReplyViewShouldClose() {
    if (catalogControllerToolbarState.isInReplyMode()) {
      catalogControllerToolbarState.pop()
    }

    threadLayout.openOrCloseReply(false)
  }

  private fun handleShareOrOpenInBrowser(share: Boolean) {
    val presenter = threadLayout.presenter
    if (!presenter.isBound) {
      return
    }

    if (presenter.currentChanDescriptor == null) {
      Logger.e(TAG, "handleShareOrOpenInBrowser() chanThread == null")
      showToast(R.string.cannot_open_in_browser_already_deleted)
      return
    }

    val chanDescriptor = presenter.currentChanDescriptor
    if (chanDescriptor == null) {
      Logger.e(TAG, "handleShareOrOpenInBrowser() chanDescriptor == null")
      showToast(R.string.cannot_open_in_browser_already_deleted)
      return
    }

    val site = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
    if (site == null) {
      Logger.e(TAG, "handleShareOrOpenInBrowser() site == null " +
        "(siteDescriptor = ${chanDescriptor.siteDescriptor()})")
      showToast(R.string.cannot_open_in_browser_already_deleted)
      return
    }

    val link = site.urlHandler.desktopUrl(chanDescriptor, null, null)
    if (share) {
      AppModuleAndroidUtils.shareLink(link)
    } else {
      AppModuleAndroidUtils.openLink(link)
    }
  }

  private suspend fun handleRouterEvent(routerEvent: KurobaWebUrlRouter.Event) {
    val result = when (routerEvent) {
      KurobaWebUrlRouter.Event.OpenEmailVerificationController -> {
        RouterEventResult.PushController(
          AppSettingsController(
            context = context,
            params = AppSettingsController.Params.createForInitialScreen(
              screenKey = SettingsScreenKey.Site(Chan4.SITE_DESCRIPTOR),
              scrollToSettingKeyRaw = KurobaSettingKey.Site.Chan4.EmailVerification(
                siteName = Chan4.SITE_DESCRIPTOR.siteName
              ).raw
            )
          )
        )
      }
      is KurobaWebUrlRouter.Event.OpenUrlInWebViewController -> {
        RouterEventResult.PresentController(
          OpenUrlInWebViewController(context, routerEvent.url)
        )
      }
      is KurobaWebUrlRouter.Event.OpenInExternalBrowser -> {
        // Issues #673 / #945: Flash content (.swf and the /f/ board)
        // is unsupported by the in-app WebView. Hand the URL to the
        // system browser. For .swf links, show a confirmation dialog
        // first because Flash content can play loud audio at full
        // volume and the app cannot control its volume.
        if (isSwfUrl(routerEvent.url)) {
          val confirmed = confirmOpenSwfInExternalBrowser()
          if (!confirmed) {
            return
          }
        }
        AppModuleAndroidUtils.openLink(routerEvent.url.toString())
        return
      }
    }

    when (result) {
      is RouterEventResult.PresentController -> presentController(result.controller)
      is RouterEventResult.PushController -> requireNavController().pushController(result.controller)
    }

    result.controller.awaitUntilClosed()
  }

  private fun isSwfUrl(url: HttpUrl): Boolean {
    return url.encodedPath.endsWith(".swf", ignoreCase = true)
  }

  private suspend fun confirmOpenSwfInExternalBrowser(): Boolean {
    val params = KurobaComposeDialogController.confirmationDialog(
      title = KurobaComposeDialogController.Text.Id(R.string.open_swf_link_confirmation_title),
      description = KurobaComposeDialogController.Text.Id(R.string.open_swf_link_confirmation_descriptor),
      negativeButton = KurobaComposeDialogController.cancelButton(),
      positionButton = KurobaComposeDialogController.okButton()
    )

    dialogFactory.showDialog(
      context = context,
      params = params
    ) ?: return false

    val clicked = params.awaitButtonClick()
    return clicked?.isPositive() == true
  }

  private sealed interface RouterEventResult {
    val controller: Controller

    data class PushController(override val controller: Controller) : RouterEventResult
    data class PresentController(override val controller: Controller) : RouterEventResult
  }

  companion object {
    private const val TAG = "BrowseController"

    private const val ACTION_CHANGE_VIEW_MODE = 901
    private const val ACTION_SORT = 902
    private const val ACTION_DEV_MENU = 903
    private const val ACTION_REPLY = 904
    private const val ACTION_OPEN_BROWSER = 905
    private const val ACTION_SHARE = 906
    private const val ACTION_SCROLL_TO_TOP = 907
    private const val ACTION_SCROLL_TO_BOTTOM = 908
    private const val ACTION_OPEN_CATALOG_OR_THREAD_BY_IDENTIFIER = 910
    private const val ACTION_OPEN_MEDIA_BY_URL = 911
    private const val ACTION_CATALOG_ALBUM = 912
    private const val ACTION_BOARD_ARCHIVE = 913
    private const val ACTION_OPEN_UNLIMITED_CATALOG_PAGE = 914
    private const val ACTION_LOAD_WHOLE_COMPOSITE_CATALOG = 915
    private const val ACTION_VIEW_REMOVED_THREADS = 916

    private const val SORT_MODE_BUMP = 1000
    private const val SORT_MODE_REPLY = 1001
    private const val SORT_MODE_IMAGE = 1002
    private const val SORT_MODE_NEWEST = 1003
    private const val SORT_MODE_OLDEST = 1004
    private const val SORT_MODE_MODIFIED = 1005
    private const val SORT_MODE_ACTIVITY = 1006

    private const val DEV_BOOKMARK_EVERY_THREAD = 2000
    private const val DEV_CACHE_EVERY_THREAD = 2001

    val catalogControllerKey by lazy(LazyThreadSafetyMode.NONE) { ControllerKey(BrowseController::class.java.name) }
  }
}
