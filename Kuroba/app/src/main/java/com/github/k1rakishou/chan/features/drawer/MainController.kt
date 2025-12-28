package com.github.k1rakishou.chan.features.drawer

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.github.k1rakishou.BottomNavViewButton
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.helper.migration.ApplicationMigrationHelper
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.features.bookmarks.BookmarksController
import com.github.k1rakishou.chan.features.drawer.data.NavigationHistoryEntry
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2OptionsController
import com.github.k1rakishou.chan.features.image_saver.ResolveDuplicateImagesController
import com.github.k1rakishou.chan.features.my_posts.SavedPostsController
import com.github.k1rakishou.chan.features.search.GlobalSearchController
import com.github.k1rakishou.chan.features.settings.MainSettingsControllerV2
import com.github.k1rakishou.chan.features.thread_downloading.LocalArchiveController
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarStateKind
import com.github.k1rakishou.chan.ui.compose.bottom_panel.KurobaComposeIconPanel
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarContainerView
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.ThreadController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.controller.ViewThreadController
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.controller.navigation.SplitNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.globalstate.toolbar.ToolbarBadgeGlobalState
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayoutNoBackground
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingLinearLayoutNoBackground
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.viewstate.DrawerEnableState
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.TimeUtils
import com.github.k1rakishou.chan.utils.findControllerOrNull
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.persist_state.PersistableChanState
import dagger.Lazy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject


class MainController(
  context: Context
) : Controller(context),
  MainControllerCallbacks,
  View.OnClickListener,
  ThemeEngine.ThemeChangesListener,
  DrawerLayout.DrawerListener {

  @Inject
  lateinit var themeEngineLazy: Lazy<ThemeEngine>
  @Inject
  lateinit var globalWindowInsetsManagerLazy: Lazy<GlobalWindowInsetsManager>
  @Inject
  lateinit var settingsNotificationManagerLazy: Lazy<SettingsNotificationManager>
  @Inject
  lateinit var historyNavigationManagerLazy: Lazy<HistoryNavigationManager>
  @Inject
  lateinit var bookmarksManagerLazy: Lazy<BookmarksManager>
  @Inject
  lateinit var dialogFactoryLazy: Lazy<DialogFactory>
  @Inject
  lateinit var imageSaverV2Lazy: Lazy<ImageSaverV2>
  @Inject
  lateinit var threadDownloadManagerLazy: Lazy<ThreadDownloadManager>
  @Inject
  lateinit var applicationMigrationHelper: ApplicationMigrationHelper
  @Inject
  lateinit var appRestarter: AppRestarter

  private val themeEngine: ThemeEngine
    get() = themeEngineLazy.get()
  private val globalWindowInsetsManager: GlobalWindowInsetsManager
    get() = globalWindowInsetsManagerLazy.get()
  private val settingsNotificationManager: SettingsNotificationManager
    get() = settingsNotificationManagerLazy.get()
  private val historyNavigationManager: HistoryNavigationManager
    get() = historyNavigationManagerLazy.get()
  private val bookmarksManager: BookmarksManager
    get() = bookmarksManagerLazy.get()
  private val dialogFactory: DialogFactory
    get() = dialogFactoryLazy.get()
  private val imageSaverV2: ImageSaverV2
    get() = imageSaverV2Lazy.get()
  private val threadDownloadManager: ThreadDownloadManager
    get() = threadDownloadManagerLazy.get()

  override val controllerKey: ControllerKey
    get() = MainController.ControllerKey

  private lateinit var rootLayout: TouchBlockingFrameLayout
  private lateinit var container: TouchBlockingFrameLayoutNoBackground
  private lateinit var drawerLayout: DrawerLayout
  private lateinit var drawer: TouchBlockingLinearLayoutNoBackground
  private lateinit var snackbarContainerView: SnackbarContainerView

  private val _latestDrawerEnableState = MutableStateFlow<DrawerEnableState?>(null)

  private val kurobaDrawerState: KurobaDrawerState
    get() = mainControllerViewModel.kurobaDrawerState

  private val kurobaComposeBottomPanel by lazy(LazyThreadSafetyMode.NONE) {
    KurobaComposeIconPanel(
      context = context,
      orientation = KurobaComposeIconPanel.Orientation.Horizontal,
      menuItems = bottomNavViewButtons()
    )
  }

  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks
    get() = (context as StartActivityStartupHandlerHelper.StartActivityCallbacks)

  private val mainControllerViewModel by viewModelByKey<MainControllerViewModel>()

  private val topThreadController: ThreadController?
    get() {
      val toolbarNavigationController = mainToolbarNavigationController
        ?: return null

      for (topController in toolbarNavigationController.childControllers.asReversed()) {
        if (topController is ThreadController) {
          return topController
        }

        if (topController is ThreadSlideController) {
          val slideNav = topController as ThreadSlideController?

          if (slideNav?.leftController() is ThreadController) {
            return slideNav.leftController() as ThreadController
          }
        }
      }

      return null
    }

  private val mainToolbarNavigationController: ToolbarNavigationController?
    get() {
      var navigationController: ToolbarNavigationController? = null
      val topController = topController
        ?: return null

      if (topController is StyledToolbarNavigationController) {
        navigationController = topController
      } else if (topController is SplitNavigationController) {
        if (topController.leftController() is StyledToolbarNavigationController) {
          navigationController = topController.leftController() as StyledToolbarNavigationController
        }
      }

      if (navigationController == null) {
        Logger.e(TAG, "topController is an unexpected controller " +
          "type: ${topController::class.java.simpleName}")
      }

      return navigationController
    }

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onCreate() {
    super.onCreate()

    view = inflate(context, R.layout.controller_main)

    rootLayout = view.findViewById(R.id.main_root_layout)
    container = view.findViewById(R.id.main_controller_container)
    drawerLayout = view.findViewById(R.id.drawer_layout)
    drawerLayout.setDrawerShadow(R.drawable.panel_shadow, GravityCompat.START)
    drawer = view.findViewById(R.id.drawer_part)

    snackbarContainerView = view.findViewById(R.id.snackbar_container_view)
    snackbarContainerView.init(SnackbarScope.Global())

    drawerLayout.addDrawerListener(this)

    val drawerComposeView = view.findViewById<ComposeView>(R.id.drawer_compose_view)
    drawerComposeView.setContent {
      ComposeEntrypoint {
        BoxWithConstraints(
          modifier = Modifier.fillMaxSize()
        ) {
          // A hack to fix crash
          // "java.lang.IllegalArgumentException LazyVerticalGrid's width should be bound by parent."
          // I dunno why or when this happens but this happens.
          if (constraints.maxWidth != Constraints.Infinity) {
            KurobaDrawer(
              mainControllerViewModel = mainControllerViewModel,
              onSwitchDayNightThemeIconClick = {
                onSwitchDayNightThemeIconClick()
              },
              onShowDrawerOptionIconClick = {
                showDrawerOptions()
              },
              onHistoryEntryViewClicked = { navigationHistoryEntry ->
                onHistoryEntryViewClicked(navigationHistoryEntry)
              },
              onHistoryEntryViewLongClicked = { navigationHistoryEntry ->
                onHistoryEntryViewLongClicked(navigationHistoryEntry)
              },
              onHistoryDeleteClicked = { navHistoryEntry ->
                controllerScope.launch { onNavHistoryDeleteClicked(navHistoryEntry) }
              },
              onHistoryEntrySelectionChanged = { currentlySelected, navHistoryEntry ->
                kurobaDrawerState.selectUnselect(navHistoryEntry, currentlySelected.not())
              },
              kurobaComposeBottomPanel = {
                KurobaComposeBottomPanelContent()
              }
            )
          }
        }
      }
    }

    compositeDisposable.add(
      settingsNotificationManager.listenForNotificationUpdates()
        .subscribe { onSettingsNotificationChanged() }
    )

    controllerScope.launch {
      restartTheAppAfterMigration()
    }

    controllerScope.launch {
      mainControllerViewModel.bookmarksBadgeState
        .onEach { bookmarksBadgeState -> onBookmarksBadgeStateChanged(bookmarksBadgeState) }
        .collect()
    }

    controllerScope.launch {
      threadDownloadManager.threadDownloadUpdateFlow
        .debounce(500L)
        .collect { event -> onNewThreadDownloadEvent(event) }
    }

    controllerScope.launch {
      combine(
        flow = globalUiStateHolder.replyLayout.replyLayoutVisibilityEventsFlow,
        flow2 = globalUiStateHolder.replyLayout.replyLayoutsBoundsFlow,
        flow3 = globalUiStateHolder.mainUi.touchPosition,
        transform = { replyLayoutVisibilityEvents, replyLayoutsBounds, touchPosition ->
          return@combine DrawerEnableState(
            replyLayoutVisibilityStates = replyLayoutVisibilityEvents,
            replyLayoutsBounds = replyLayoutsBounds,
            touchPosition = touchPosition
          )
        }
      )
        .onEach { drawerEnableState ->
          _latestDrawerEnableState.value = drawerEnableState

          if (drawerLayout.isDrawerOpen(drawer)) {
            setDrawerEnabled(true)
            return@onEach
          }

          setDrawerEnabled(drawerEnableState.isDrawerEnabled())
        }
        .collect()
    }

    controllerScope.launch {
      globalUiStateHolder.drawer.drawerOpenCloseEventFlow
        .onEach { openDrawer ->
          val latestDrawerEnableState = _latestDrawerEnableState.value
          if (latestDrawerEnableState != null && !latestDrawerEnableState.isDrawerEnabled()) {
            return@onEach
          }

          if (openDrawer && !drawerLayout.isOpen) {
            drawerLayout.openDrawer(drawer)
          } else if (!openDrawer && drawerLayout.isOpen) {
            drawerLayout.closeDrawer(drawer)
          }
        }
        .collect()
    }

    themeEngine.addListener(this)
    onThemeChanged()
  }

  override fun onShow() {
    super.onShow()

    mainControllerViewModel.updateBadge()
  }

  override fun onThemeChanged() {
    mainControllerViewModel.onThemeChanged()
    settingsNotificationManager.onThemeChanged()
  }

  override fun onDestroy() {
    super.onDestroy()

    drawerLayout.removeDrawerListener(this)
    themeEngine.removeListener(this)
    compositeDisposable.clear()
  }

  override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
  }

  override fun onDrawerOpened(drawerView: View) {
    kurobaDrawerState.updateDrawerOpened(opened = true)

    globalUiStateHolder.updateDrawerState {
      onDrawerAppearanceChanged(opened = true)
    }
  }

  override fun onDrawerClosed(drawerView: View) {
    kurobaDrawerState.clearSelection()
    kurobaDrawerState.updateDrawerOpened(opened = false)

    globalUiStateHolder.updateDrawerState {
      onDrawerAppearanceChanged(opened = false)
    }
  }

  override fun onDrawerStateChanged(newState: Int) {
  }

  @Composable
  private fun KurobaComposeBottomPanelContent() {
    kurobaComposeBottomPanel.BuildPanel(
      onMenuItemClicked = { clickedMenuItemId ->
        onNavigationItemSelectedListener(clickedMenuItemId)

        if (drawerLayout.isDrawerOpen(drawer)) {
          drawerLayout.closeDrawer(drawer)
        }
      }
    )
  }

  fun loadMainControllerDrawerData() {
    mainControllerViewModel.firstLoadDrawerData()
  }

  fun pushChildController(childController: Controller) {
    setCurrentChildController(childController)
  }

  private fun setCurrentChildController(childController: Controller) {
    addChildController(childController)
    childController.attachToParentView(container)
    childController.onShow()
  }

  fun openGlobalSearchController() {
    val globalSearchController = GlobalSearchController(context, startActivityCallback)
    mainToolbarNavigationController?.pushController(globalSearchController)
  }

  fun openPostsController() {
    val savedPostsController = SavedPostsController(context, startActivityCallback)
    mainToolbarNavigationController?.pushController(savedPostsController)
  }

  fun openArchiveController() {
    val localArchiveController = LocalArchiveController(context, startActivityCallback)
    mainToolbarNavigationController?.pushController(localArchiveController)
  }

  fun openBookmarksController(threadDescriptors: List<ChanDescriptor.ThreadDescriptor>) {
    val bookmarksController = BookmarksController(context, threadDescriptors, startActivityCallback)
    mainToolbarNavigationController?.pushController(bookmarksController)
  }

  fun openSettingsController() {
    val mainSettingsControllerV2 = MainSettingsControllerV2(context)
    mainToolbarNavigationController?.pushController(mainSettingsControllerV2)
  }

  fun getViewThreadController(): ViewThreadController? {
    val topController = topController
      ?: return null

    if (topController is SplitNavigationController) {
      return topController
        .findControllerOrNull { controller -> controller is ViewThreadController }
        as? ViewThreadController
    }

    if (topController is StyledToolbarNavigationController) {
      val threadSlideController = topController.topController as? ThreadSlideController
      if (threadSlideController != null) {
        return threadSlideController.rightController()
      }
    }

    return null
  }

  suspend fun loadThreadWithoutFocusing(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    animated: Boolean
  ) {
    controllerScope.launch {
      topThreadController?.showThreadWithoutFocusing(threadDescriptor, animated)
    }
  }

  suspend fun loadThread(
    descriptor: ChanDescriptor.ThreadDescriptor,
    animated: Boolean
  ) {
    controllerScope.launch {
      topThreadController?.showThread(descriptor, animated)
    }
  }

  override fun onClick(v: View) {
    // no-op
  }

  fun onMenuClicked() {
    val topController = mainToolbarNavigationController?.topController
      ?: return

    if (topController.hasDrawer) {
      drawerLayout.openDrawer(drawer)
    }
  }

  override fun onBack(): Boolean {
    if (kurobaDrawerState.selectedHistoryEntries.isNotEmpty()) {
      kurobaDrawerState.clearSelection()
      return true
    }

    if (drawerLayout.isDrawerOpen(drawer)) {
      drawerLayout.closeDrawer(drawer)
      return true
    }

    return super.onBack()
  }

  override fun passMotionEventIntoDrawer(event: MotionEvent): Boolean {
    return drawerLayout.onTouchEvent(event)
  }

  fun onNavigationItemDrawerInfoUpdated(hasDrawer: Boolean) {
    mainControllerViewModel.onNavigationItemDrawerInfoUpdated(hasDrawer)
  }

  fun showResolveDuplicateImagesController(uniqueId: String, imageSaverOptionsJson: String) {
    val alreadyPresenting = isAlreadyPresenting { controller -> controller is ResolveDuplicateImagesController }
    if (alreadyPresenting) {
      return
    }

    val resolveDuplicateImagesController = ResolveDuplicateImagesController(
      context,
      uniqueId,
      imageSaverOptionsJson
    )

    presentController(resolveDuplicateImagesController)
  }

  fun showImageSaverV2OptionsController(uniqueId: String) {
    val alreadyPresenting = isAlreadyPresenting { controller -> controller is ImageSaverV2OptionsController }
    if (alreadyPresenting) {
      return
    }

    val options = ImageSaverV2OptionsController.Options.ResultDirAccessProblems(
      uniqueId,
      onRetryClicked = { imageSaverV2Options -> imageSaverV2.restartUnfinished(uniqueId, imageSaverV2Options) },
      onCanceled = { imageSaverV2.deleteDownload(uniqueId) }
    )

    val imageSaverV2OptionsController = ImageSaverV2OptionsController(context, options)
    presentController(imageSaverV2OptionsController)
  }

  private fun onBookmarksBadgeStateChanged(state: MainControllerViewModel.BookmarksBadgeState) {
    if (state.totalUnseenPostsCount <= 0) {
      kurobaComposeBottomPanel.updateBadge(
        menuItemId = R.id.action_bookmarks,
        menuItemBadgeInfo = null
      )
    } else {
      kurobaComposeBottomPanel.updateBadge(
        menuItemId = R.id.action_bookmarks,
        menuItemBadgeInfo = KurobaComposeIconPanel.MenuItemBadgeInfo.Counter(
          counter = state.totalUnseenPostsCount,
          highlight = state.hasUnreadReplies
        )
      )
    }

    globalUiStateHolder.updateToolbarState {
      val toolbarBadgeGlobalState = if (state.totalUnseenPostsCount <= 0) {
        ToolbarBadgeGlobalState(0, false)
      } else {
        ToolbarBadgeGlobalState(state.totalUnseenPostsCount, state.hasUnreadReplies)
      }

      updateToolbarBadge(ToolbarStateKind.Catalog, toolbarBadgeGlobalState)
      updateToolbarBadge(ToolbarStateKind.Thread, toolbarBadgeGlobalState)
    }
  }

  private suspend fun onNewThreadDownloadEvent(event: ThreadDownloadManager.Event) {
    val activeThreadDownloadsCount = threadDownloadManager.getAllActiveThreadDownloads().size

    if (activeThreadDownloadsCount <= 0) {
      kurobaComposeBottomPanel.updateBadge(
        menuItemId = R.id.action_archive,
        menuItemBadgeInfo = null
      )
    } else {
      kurobaComposeBottomPanel.updateBadge(
        menuItemId = R.id.action_archive,
        menuItemBadgeInfo = KurobaComposeIconPanel.MenuItemBadgeInfo.Counter(
          counter = activeThreadDownloadsCount,
          highlight = false
        )
      )
    }
  }

  private fun onSettingsNotificationChanged() {
    val notificationsCount = settingsNotificationManager.count()

    if (notificationsCount <= 0) {
      kurobaComposeBottomPanel.updateBadge(
        menuItemId = R.id.action_settings,
        menuItemBadgeInfo = null
      )
    } else {
      kurobaComposeBottomPanel.updateBadge(
        menuItemId = R.id.action_settings,
        menuItemBadgeInfo = KurobaComposeIconPanel.MenuItemBadgeInfo.Dot
      )
    }
  }

  private fun setDrawerEnabled(enabled: Boolean) {
    val lockMode = if (enabled) {
      DrawerLayout.LOCK_MODE_UNLOCKED
    } else {
      DrawerLayout.LOCK_MODE_LOCKED_CLOSED
    }

    val prevLockMode = drawerLayout.getDrawerLockMode(GravityCompat.START)
    if (prevLockMode == lockMode) {
      if (lockMode == DrawerLayout.LOCK_MODE_LOCKED_CLOSED && drawerLayout.isDrawerOpen(drawer)) {
        drawerLayout.closeDrawer(drawer)
      }

      return
    }

    drawerLayout.setDrawerLockMode(lockMode, GravityCompat.START)

    if (!enabled) {
      drawerLayout.closeDrawer(drawer)
    }
  }

  private fun onNavigationItemSelectedListener(menuItemId: Int) {
    when (menuItemId) {
      com.github.k1rakishou.chan.R.id.action_search -> openGlobalSearchController()
      com.github.k1rakishou.chan.R.id.action_archive -> openArchiveController()
      com.github.k1rakishou.chan.R.id.action_posts -> openPostsController()
      com.github.k1rakishou.chan.R.id.action_bookmarks -> openBookmarksController(emptyList())
      com.github.k1rakishou.chan.R.id.action_settings -> openSettingsController()
    }
  }

  private fun bottomNavViewButtons(): List<KurobaComposeIconPanel.MenuItem> {
    val bottomNavViewButtons = PersistableChanState.reorderableBottomNavViewButtons.get()

    return bottomNavViewButtons.bottomNavViewButtons().map { bottomNavViewButton ->
      return@map when (bottomNavViewButton) {
        BottomNavViewButton.Search -> {
          KurobaComposeIconPanel.MenuItem(
            id = com.github.k1rakishou.chan.R.id.action_search,
            iconId = com.github.k1rakishou.chan.R.drawable.ic_search_white_24dp
          )
        }
        BottomNavViewButton.Archive -> {
          KurobaComposeIconPanel.MenuItem(
            id = com.github.k1rakishou.chan.R.id.action_archive,
            iconId = com.github.k1rakishou.chan.R.drawable.ic_baseline_archive_24
          )
        }
        BottomNavViewButton.MyPosts -> {
          KurobaComposeIconPanel.MenuItem(
            id = com.github.k1rakishou.chan.R.id.action_posts,
            iconId = com.github.k1rakishou.chan.R.drawable.ic_baseline_posts
          )
        }
        BottomNavViewButton.Bookmarks -> {
          KurobaComposeIconPanel.MenuItem(
            id = com.github.k1rakishou.chan.R.id.action_bookmarks,
            iconId = com.github.k1rakishou.chan.R.drawable.ic_bookmark_white_24dp
          )
        }
        BottomNavViewButton.Settings -> {
          KurobaComposeIconPanel.MenuItem(
            id = com.github.k1rakishou.chan.R.id.action_settings,
            iconId = com.github.k1rakishou.chan.R.drawable.ic_baseline_settings
          )
        }
      }
    }
  }

  private fun onSwitchDayNightThemeIconClick() {
    if (TimeUtils.isHalloweenToday()) {
      showToast(R.string.not_allowed_during_halloween)
      return
    }

    controllerScope.launch {
      delay(125)

      themeEngine.toggleTheme()
      showToast(appResources.string(R.string.drawer_controller_switched_to_theme, themeEngine.chanTheme.name))
    }
  }

  private fun onHistoryEntryViewClicked(navHistoryEntry: NavigationHistoryEntry) {
    controllerScope.launch {
      val currentTopThreadController = topThreadController
        ?: return@launch

      when (val descriptor = navHistoryEntry.descriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          currentTopThreadController.showThread(descriptor, true)
        }

        is ChanDescriptor.CompositeCatalogDescriptor,
        is ChanDescriptor.CatalogDescriptor -> {
          currentTopThreadController.showCatalog(
            catalogDescriptor = descriptor as ChanDescriptor.ICatalogDescriptor,
            animated = true
          )
        }
      }

      if (drawerLayout.isDrawerOpen(drawer)) {
        drawerLayout.closeDrawer(drawer)
      }
    }
  }

  private fun onHistoryEntryViewLongClicked(navHistoryEntry: NavigationHistoryEntry) {
    val drawerOptions = mutableListOf<FloatingListMenuItem>()

    if (kurobaDrawerState.selectedHistoryEntries.isEmpty()) {
      drawerOptions += FloatingListMenuItem(
        key = MainController.ACTION_START_SELECTION,
        name = AppModuleAndroidUtils.getString(R.string.drawer_controller_start_navigation_history_selection)
      )

      drawerOptions += FloatingListMenuItem(
        key = MainController.ACTION_SELECT_ALL,
        name = AppModuleAndroidUtils.getString(R.string.drawer_controller_navigation_history_select_all)
      )
    }

    if (kurobaDrawerState.selectedHistoryEntries.isNotEmpty()) {
      drawerOptions += FloatingListMenuItem(
        key = MainController.ACTION_PIN_UNPIN_SELECTED,
        name = AppModuleAndroidUtils.getString(R.string.drawer_controller_pin_unpin_selected)
      )

      drawerOptions += FloatingListMenuItem(
        key = MainController.ACTION_DELETE_SELECTED,
        name = AppModuleAndroidUtils.getString(R.string.drawer_controller_delete_selected)
      )
    }

    if (kurobaDrawerState.selectedHistoryEntries.isEmpty()) {
      drawerOptions += FloatingListMenuItem(
        key = MainController.ACTION_PIN_UNPIN,
        name = AppModuleAndroidUtils.getString(R.string.drawer_controller_pin_unpin)
      )

      if (navHistoryEntry.descriptor is ChanDescriptor.ThreadDescriptor) {
        drawerOptions += FloatingListMenuItem(
          key = MainController.ACTION_BOOKMARK_UNBOOKMARK,
          name = AppModuleAndroidUtils.getString(R.string.drawer_controller_bookmark_unbookmark)
        )
      }

      if (navHistoryEntry.descriptor.isThreadDescriptor() && navHistoryEntry.additionalInfo != null) {
        drawerOptions += FloatingListMenuItem(
          key = MainController.ACTION_SHOW_IN_BOOKMARKS,
          name = AppModuleAndroidUtils.getString(R.string.drawer_controller_show_in_bookmarks)
        )
      }

      drawerOptions += FloatingListMenuItem(
        key = MainController.ACTION_DELETE,
        name = AppModuleAndroidUtils.getString(R.string.drawer_controller_delete_one)
      )
    }

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = drawerOptions,
      itemClickListener = { item ->
        controllerScope.launch {
          when (item.key) {
            MainController.ACTION_START_SELECTION -> {
              kurobaDrawerState.toggleSelection(navHistoryEntry)
            }

            MainController.ACTION_SELECT_ALL -> {
              kurobaDrawerState.selectAll()
            }

            MainController.ACTION_PIN_UNPIN -> {
              pinUnpin(listOf(navHistoryEntry.descriptor))
            }

            MainController.ACTION_BOOKMARK_UNBOOKMARK -> {
              val threadDescriptor = navHistoryEntry.descriptor.threadDescriptorOrNull()
                ?: return@launch

              if (bookmarksManager.contains(threadDescriptor)) {
                bookmarksManager.deleteBookmark(threadDescriptor)
              } else {
                bookmarksManager.createBookmark(
                  threadDescriptor = threadDescriptor,
                  thumbnailUrl = navHistoryEntry.threadThumbnailUrl,
                  title = navHistoryEntry.title
                )
              }
            }

            MainController.ACTION_PIN_UNPIN_SELECTED -> {
              pinUnpin(kurobaDrawerState.getSelectedDescriptors())
              kurobaDrawerState.clearSelection()
            }

            MainController.ACTION_DELETE_SELECTED -> {
              onNavHistoryDeleteSelectedClicked(kurobaDrawerState.getSelectedDescriptors())
              kurobaDrawerState.clearSelection()
            }

            MainController.ACTION_DELETE -> {
              onNavHistoryDeleteClicked(navHistoryEntry)
            }

            MainController.ACTION_SHOW_IN_BOOKMARKS -> {
              navHistoryEntry.descriptor.threadDescriptorOrNull()?.let { threadDescriptor ->
                openBookmarksController(listOf(threadDescriptor))

                if (drawerLayout.isDrawerOpen(drawer)) {
                  drawerLayout.closeDrawer(drawer)
                }
              }
            }
          }
        }
      }
    )

    presentController(floatingListMenuController)
  }

  private suspend fun pinUnpin(
    descriptors: Collection<ChanDescriptor>
  ) {
    if (descriptors.isEmpty()) {
      return
    }

    when (mainControllerViewModel.pinOrUnpin(descriptors)) {
      HistoryNavigationManager.PinResult.Pinned -> {
        val text = if (descriptors.size == 1) {
          AppModuleAndroidUtils.getString(
            R.string.drawer_controller_navigation_entry_pinned_one,
            descriptors.first().userReadableString()
          )
        } else {
          AppModuleAndroidUtils.getString(R.string.drawer_controller_navigation_entry_pinned_many, descriptors.size)
        }

        showToast(text)
      }

      HistoryNavigationManager.PinResult.Unpinned -> {
        val text = if (descriptors.size == 1) {
          AppModuleAndroidUtils.getString(
            R.string.drawer_controller_navigation_entry_unpinned_one,
            descriptors.first().userReadableString()
          )
        } else {
          AppModuleAndroidUtils.getString(R.string.drawer_controller_navigation_entry_unpinned_many, descriptors.size)
        }

        showToast(text)
      }

      HistoryNavigationManager.PinResult.Failure -> {
        showToast(AppModuleAndroidUtils.getString(R.string.drawer_controller_navigation_entry_failed_to_pin_unpin))
      }
    }
  }

  private suspend fun onNavHistoryDeleteClicked(navHistoryEntry: NavigationHistoryEntry) {
    mainControllerViewModel.deleteNavElement(navHistoryEntry)

    val text = AppModuleAndroidUtils.getString(
      R.string.drawer_controller_navigation_entry_deleted_one,
      navHistoryEntry.descriptor.userReadableString()
    )

    showToast(text)
  }

  private fun onNavHistoryDeleteSelectedClicked(selected: List<ChanDescriptor>) {
    if (selected.isEmpty()) {
      return
    }

    controllerScope.launch {
      mainControllerViewModel.deleteNavElementsByDescriptors(selected)

      val text = AppModuleAndroidUtils.getString(
        R.string.drawer_controller_navigation_entry_deleted_many,
        selected.size
      )

      showToast(text)
    }
  }

  private fun showDrawerOptions() {
    val drawerOptions = mutableListOf<FloatingListMenuItem>()

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_GRID_MODE,
      name = AppModuleAndroidUtils.getString(R.string.drawer_controller_grid_mode),
      checked = ChanSettings.drawerGridMode.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_MOVE_LAST_ACCESSED_THREAD_TO_TOP,
      name = AppModuleAndroidUtils.getString(R.string.drawer_controller_move_last_accessed_thread_to_top),
      checked = ChanSettings.drawerMoveLastAccessedThreadToTop.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_SHOW_BOOKMARKS,
      name = AppModuleAndroidUtils.getString(R.string.drawer_controller_show_bookmarks),
      checked = ChanSettings.drawerShowBookmarkedThreads.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_SHOW_NAV_HISTORY,
      name = AppModuleAndroidUtils.getString(R.string.drawer_controller_show_navigation_history),
      checked = ChanSettings.drawerShowNavigationHistory.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_SHOW_DELETE_SHORTCUT,
      name = AppModuleAndroidUtils.getString(R.string.drawer_controller_delete_shortcut),
      checked = ChanSettings.drawerShowDeleteButtonShortcut.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_DELETE_BOOKMARK_WHEN_DELETING_NAV_HISTORY,
      name = AppModuleAndroidUtils.getString(R.string.drawer_controller_delete_bookmark_on_history_delete),
      checked = ChanSettings.drawerDeleteBookmarksWhenDeletingNavHistory.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_DELETE_NAV_HISTORY_WHEN_BOOKMARK_DELETED,
      name = AppModuleAndroidUtils.getString(R.string.drawer_controller_delete_nav_history_on_bookmark_delete),
      checked = ChanSettings.drawerDeleteNavHistoryWhenBookmarkDeleted.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_RESTORE_LAST_VISITED_CATALOG,
      name = AppModuleAndroidUtils.getString(R.string.setting_load_last_opened_board_upon_app_start_title),
      checked = ChanSettings.loadLastOpenedBoardUponAppStart.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_RESTORE_LAST_VISITED_THREAD,
      name = AppModuleAndroidUtils.getString(R.string.setting_load_last_opened_thread_upon_app_start_title),
      checked = ChanSettings.loadLastOpenedThreadUponAppStart.get()
    )

    drawerOptions += FloatingListMenuItem(
      key = ACTION_CLEAR_NAV_HISTORY,
      name = AppModuleAndroidUtils.getString(R.string.drawer_controller_clear_nav_history)
    )

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = drawerOptions,
      itemClickListener = { item ->
        controllerScope.launch {
          when (item.key) {
            ACTION_GRID_MODE -> {
              val drawerGridMode = ChanSettings.drawerGridMode.toggle()
              kurobaDrawerState.drawerGridMode.value = drawerGridMode
            }

            ACTION_MOVE_LAST_ACCESSED_THREAD_TO_TOP -> {
              ChanSettings.drawerMoveLastAccessedThreadToTop.toggle()
            }

            ACTION_SHOW_BOOKMARKS -> {
              mainControllerViewModel.deleteBookmarkedNavHistoryElements()
            }

            ACTION_SHOW_NAV_HISTORY -> {
              ChanSettings.drawerShowNavigationHistory.toggle()
              mainControllerViewModel.reloadNavigationHistory()
            }

            ACTION_SHOW_DELETE_SHORTCUT -> {
              kurobaDrawerState.updateDeleteButtonShortcut(ChanSettings.drawerShowDeleteButtonShortcut.toggle())
            }

            ACTION_DELETE_BOOKMARK_WHEN_DELETING_NAV_HISTORY -> {
              ChanSettings.drawerDeleteBookmarksWhenDeletingNavHistory.toggle()
            }

            ACTION_DELETE_NAV_HISTORY_WHEN_BOOKMARK_DELETED -> {
              ChanSettings.drawerDeleteNavHistoryWhenBookmarkDeleted.toggle()
            }

            ACTION_RESTORE_LAST_VISITED_CATALOG -> {
              ChanSettings.loadLastOpenedBoardUponAppStart.toggle()
            }

            ACTION_RESTORE_LAST_VISITED_THREAD -> {
              ChanSettings.loadLastOpenedThreadUponAppStart.toggle()
            }

            ACTION_CLEAR_NAV_HISTORY -> {
              dialogFactory.createSimpleConfirmationDialog(
                context = context,
                titleTextId = R.string.drawer_controller_clear_nav_history_dialog_title,
                negativeButtonText = AppModuleAndroidUtils.getString(R.string.do_not),
                positiveButtonText = AppModuleAndroidUtils.getString(R.string.clear),
                onPositiveButtonClickListener = {
                  controllerScope.launch { historyNavigationManager.clear() }
                }
              )
            }
          }
        }
      }
    )

    presentController(floatingListMenuController)
  }

  private fun restartTheAppAfterMigration() {
    val appliedMigrations = applicationMigrationHelper.appliedMigrations
    if (appliedMigrations.isEmpty()) {
      Logger.debug(TAG) { "appliedMigrations are empty, nothing to do" }
      return
    }

    val fromVersion = (appliedMigrations.min() - 1).coerceAtLeast(0)
    val toVersion = appliedMigrations.max()

    val changelog = buildString {
      appendLine("Migrated app data from version ${fromVersion} to ${toVersion}.")
      appendLine("Changelog:")

      for (migrationVersion in fromVersion..toVersion) {
        val changelog = applicationMigrationHelper.changelog(migrationVersion)
          ?: "No changelog provided"

        append("- (v${migrationVersion}): ")
        appendLine(changelog)
      }
    }

    dialogFactory.createSimpleInformationDialog(
      context = context,
      titleText = "Application restart is required.",
      descriptionText = "Application's internal data has been migrated so now the application needs to be restarted to ensure there are no inconsistencies.\n\n${changelog}",
      cancelable = false,
      checkAppVisibility = true,
      positiveButtonTextId = com.github.k1rakishou.chan.R.string.restart_the_app,
      onPositiveButtonClickListener = { appRestarter.restart() }
    )
  }

  companion object {
    private const val TAG = "MainController"

    val ControllerKey = ControllerKey(MainController::class.java.name)

    const val MIN_SPAN_COUNT = 3
    const val MAX_SPAN_COUNT = 6
    const val TEXT_ANIMATION_DURATION = 1000

    private const val ACTION_MOVE_LAST_ACCESSED_THREAD_TO_TOP = 0
    private const val ACTION_SHOW_BOOKMARKS = 1
    private const val ACTION_SHOW_NAV_HISTORY = 2
    private const val ACTION_SHOW_DELETE_SHORTCUT = 3
    private const val ACTION_CLEAR_NAV_HISTORY = 4
    private const val ACTION_RESTORE_LAST_VISITED_CATALOG = 5
    private const val ACTION_RESTORE_LAST_VISITED_THREAD = 6
    private const val ACTION_GRID_MODE = 7
    private const val ACTION_DELETE_BOOKMARK_WHEN_DELETING_NAV_HISTORY = 8
    private const val ACTION_DELETE_NAV_HISTORY_WHEN_BOOKMARK_DELETED = 9

    private const val ACTION_START_SELECTION = 100
    private const val ACTION_SELECT_ALL = 101
    private const val ACTION_PIN_UNPIN_SELECTED = 102
    private const val ACTION_PIN_UNPIN = 103
    private const val ACTION_DELETE_SELECTED = 104
    private const val ACTION_DELETE = 105
    private const val ACTION_SHOW_IN_BOOKMARKS = 106
    private const val ACTION_BOOKMARK_UNBOOKMARK = 107

    val GRID_COLUMN_WIDTH = AppModuleAndroidUtils.dp(80f)
    val LIST_MODE_ROW_HEIGHT = 52.dp
    val NAV_HISTORY_DELETE_BTN_SIZE = 24.dp
    val NAV_HISTORY_DELETE_BTN_BG_COLOR = Color(0x50000000)
  }
}