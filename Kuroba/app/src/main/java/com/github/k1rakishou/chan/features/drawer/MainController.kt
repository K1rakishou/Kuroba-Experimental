/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.features.drawer

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import coil.transform.CircleCropTransformation
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.GlobalViewStateManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.core.navigation.HasNavigation
import com.github.k1rakishou.chan.features.drawer.data.HistoryControllerState
import com.github.k1rakishou.chan.features.drawer.data.NavHistoryBookmarkAdditionalInfo
import com.github.k1rakishou.chan.features.drawer.data.NavigationHistoryEntry
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2OptionsController
import com.github.k1rakishou.chan.features.image_saver.ResolveDuplicateImagesController
import com.github.k1rakishou.chan.features.search.GlobalSearchController
import com.github.k1rakishou.chan.features.settings.MainSettingsControllerV2
import com.github.k1rakishou.chan.features.thread_downloading.LocalArchiveController
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.KurobaComposeImage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeSelectionIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaSearchInput
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.compose.bottom_panel.KurobaComposeIconPanel
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.search.SimpleSearchState
import com.github.k1rakishou.chan.ui.compose.search.rememberSimpleSearchState
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.ThreadController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.controller.ViewThreadController
import com.github.k1rakishou.chan.ui.controller.navigation.BottomNavBarAwareNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.SplitNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.TabHostController
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayoutNoBackground
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingLinearLayoutNoBackground
import com.github.k1rakishou.chan.ui.view.KurobaBottomNavigationView
import com.github.k1rakishou.chan.ui.view.NavigationViewContract
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanel
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItem
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.TimeUtils
import com.github.k1rakishou.chan.utils.findControllerOrNull
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject


class MainController(
  context: Context
) : Controller(context),
  MainControllerCallbacks,
  View.OnClickListener,
  WindowInsetsListener,
  ThemeEngine.ThemeChangesListener,
  DrawerLayout.DrawerListener {

  @Inject
  lateinit var _themeEngine: Lazy<ThemeEngine>
  @Inject
  lateinit var _globalWindowInsetsManager: Lazy<GlobalWindowInsetsManager>
  @Inject
  lateinit var _settingsNotificationManager: Lazy<SettingsNotificationManager>
  @Inject
  lateinit var _historyNavigationManager: Lazy<HistoryNavigationManager>
  @Inject
  lateinit var _siteManager: Lazy<SiteManager>
  @Inject
  lateinit var _boardManager: Lazy<BoardManager>
  @Inject
  lateinit var _bookmarksManager: Lazy<BookmarksManager>
  @Inject
  lateinit var _pageRequestManager: Lazy<PageRequestManager>
  @Inject
  lateinit var _archivesManager: Lazy<ArchivesManager>
  @Inject
  lateinit var _chanThreadManager: Lazy<ChanThreadManager>
  @Inject
  lateinit var _dialogFactory: Lazy<DialogFactory>
  @Inject
  lateinit var _imageSaverV2: Lazy<ImageSaverV2>
  @Inject
  lateinit var _imageLoaderV2: Lazy<ImageLoaderV2>
  @Inject
  lateinit var _globalViewStateManager: Lazy<GlobalViewStateManager>
  @Inject
  lateinit var _threadDownloadManager: Lazy<ThreadDownloadManager>

  private val themeEngine: ThemeEngine
    get() = _themeEngine.get()
  private val globalWindowInsetsManager: GlobalWindowInsetsManager
    get() = _globalWindowInsetsManager.get()
  private val settingsNotificationManager: SettingsNotificationManager
    get() = _settingsNotificationManager.get()
  private val historyNavigationManager: HistoryNavigationManager
    get() = _historyNavigationManager.get()
  private val siteManager: SiteManager
    get() = _siteManager.get()
  private val boardManager: BoardManager
    get() = _boardManager.get()
  private val bookmarksManager: BookmarksManager
    get() = _bookmarksManager.get()
  private val pageRequestManager: PageRequestManager
    get() = _pageRequestManager.get()
  private val archivesManager: ArchivesManager
    get() = _archivesManager.get()
  private val chanThreadManager: ChanThreadManager
    get() = _chanThreadManager.get()
  private val dialogFactory: DialogFactory
    get() = _dialogFactory.get()
  private val imageSaverV2: ImageSaverV2
    get() = _imageSaverV2.get()
  private val imageLoaderV2: ImageLoaderV2
    get() = _imageLoaderV2.get()
  private val globalViewStateManager: GlobalViewStateManager
    get() = _globalViewStateManager.get()
  private val threadDownloadManager: ThreadDownloadManager
    get() = _threadDownloadManager.get()

  private lateinit var rootLayout: TouchBlockingFrameLayout
  private lateinit var container: TouchBlockingFrameLayoutNoBackground
  private lateinit var drawerLayout: DrawerLayout
  private lateinit var drawer: TouchBlockingLinearLayoutNoBackground
  private lateinit var navigationViewContract: NavigationViewContract
  private lateinit var bottomMenuPanel: BottomMenuPanel

  private val kurobaComposeBottomPanel by lazy {
    KurobaComposeIconPanel(
      context = context,
      orientation = KurobaComposeIconPanel.Orientation.Horizontal,
      defaultSelectedMenuItemId = R.id.action_browse,
      menuItems = KurobaBottomNavigationView.bottomNavViewButtons()
    )
  }

  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks
    get() = (context as StartActivityStartupHandlerHelper.StartActivityCallbacks)

  private val bottomNavViewGestureDetector by lazy {
    return@lazy BottomNavViewLongTapSwipeUpGestureDetector(
      context = context,
      navigationViewContract = navigationViewContract,
      onSwipedUpAfterLongPress = {
        globalViewStateManager.onBottomNavViewSwipeUpGestureTriggered()
      }
    )
  }

  private val bottomPadding = mutableStateOf(0)
  private val drawerOpenedState = mutableStateOf(false)

  private val drawerViewModel by lazy {
    requireComponentActivity().viewModelByKey<MainControllerViewModel>()
  }

  private val childControllersStack = Stack<Controller>()

  private val topThreadController: ThreadController?
    get() {
      val nav = mainToolbarNavigationController
        ?: return null

      if (nav.top is ThreadController) {
        return nav.top as ThreadController?
      }

      if (nav.top is ThreadSlideController) {
        val slideNav = nav.top as ThreadSlideController?

        if (slideNav?.leftController is ThreadController) {
          return slideNav.leftController as ThreadController
        }
      }

      return null
    }

  private val mainToolbarNavigationController: ToolbarNavigationController?
    get() {
      var navigationController: ToolbarNavigationController? = null
      var topController: Controller? = top

      if (topController is BottomNavBarAwareNavigationController) {
        topController = childControllers.getOrNull(childControllers.lastIndex - 1)
      }

      if (topController == null) {
        return null
      }

      if (topController is StyledToolbarNavigationController) {
        navigationController = topController
      } else if (topController is SplitNavigationController) {
        if (topController.getLeftController() is StyledToolbarNavigationController) {
          navigationController = topController.getLeftController() as StyledToolbarNavigationController
        }
      } else if (topController is ThreadSlideController) {
        navigationController = topController.getLeftController() as StyledToolbarNavigationController
      }

      if (navigationController == null) {
        Logger.e(TAG, "topController is an unexpected controller " +
          "type: ${topController::class.java.simpleName}")
      }

      return navigationController
    }

  override val navigationViewContractType: NavigationViewContract.Type
    get() = navigationViewContract.type

  override val isBottomPanelShown: Boolean
    get() = bottomMenuPanel.isBottomPanelShown

  override val bottomPanelHeight: Int
    get() = bottomMenuPanel.totalHeight()

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onCreate() {
    super.onCreate()

    view = if (ChanSettings.isSplitLayoutMode()) {
      inflate(context, R.layout.controller_main_split_mode)
    } else {
      inflate(context, R.layout.controller_main)
    }

    rootLayout = view.findViewById(R.id.main_root_layout)
    container = view.findViewById(R.id.main_controller_container)
    drawerLayout = view.findViewById(R.id.drawer_layout)
    drawerLayout.setDrawerShadow(R.drawable.panel_shadow, GravityCompat.START)
    drawer = view.findViewById(R.id.drawer_part)
    navigationViewContract = view.findViewById(R.id.navigation_view) as NavigationViewContract
    bottomMenuPanel = view.findViewById(R.id.bottom_menu_panel)

    navigationViewContract.selectedMenuItemId = R.id.action_browse
    navigationViewContract.viewElevation = dp(4f).toFloat()

    // Must be above bottomNavView
    bottomMenuPanel.elevation = dp(6f).toFloat()

    drawerLayout.addDrawerListener(this)

    navigationViewContract.setOnNavigationItemSelectedListener { menuItemId ->
      if (navigationViewContract.selectedMenuItemId == menuItemId) {
        return@setOnNavigationItemSelectedListener true
      }

      onNavigationItemSelectedListener(menuItemId)
      return@setOnNavigationItemSelectedListener true
    }

    navigationViewContract.setOnOuterInterceptTouchEventListener { event ->
      if (!ChanSettings.replyLayoutOpenCloseGestures.get()) {
        return@setOnOuterInterceptTouchEventListener false
      }

      if (navigationViewContract.selectedMenuItemId == R.id.action_browse) {
        return@setOnOuterInterceptTouchEventListener bottomNavViewGestureDetector.onInterceptTouchEvent(event)
      }

      return@setOnOuterInterceptTouchEventListener false
    }

    navigationViewContract.setOnOuterTouchEventListener { event ->
      if (!ChanSettings.replyLayoutOpenCloseGestures.get()) {
        return@setOnOuterTouchEventListener false
      }

      if (navigationViewContract.selectedMenuItemId == R.id.action_browse) {
        return@setOnOuterTouchEventListener bottomNavViewGestureDetector.onTouchEvent(event)
      }

      return@setOnOuterTouchEventListener false
    }

    val drawerComposeView = view.findViewById<ComposeView>(R.id.drawer_compose_view)
    drawerComposeView.setContent {
      ProvideChanTheme(themeEngine) {
        val chanTheme = LocalChanTheme.current
        val bgColor = chanTheme.backColorCompose

        BoxWithConstraints(
          modifier = Modifier.fillMaxSize()
        ) {
          // A hack to fix crash
          // "java.lang.IllegalArgumentException LazyVerticalGrid's width should be bound by parent."
          // I dunno why or when this happens but this happens.
          if (constraints.maxWidth != Constraints.Infinity) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
            ) {
              BuildContent()
            }
          }
        }
      }
    }

    compositeDisposable.add(
      drawerViewModel.listenForBookmarksBadgeStateChanges()
        .subscribe(
          { state -> onBookmarksBadgeStateChanged(state) },
          { error ->
            Logger.e(TAG, "Unknown error subscribed to drawerPresenter.listenForBookmarksBadgeStateChanges()", error)
          }
        )
    )

    compositeDisposable.add(
      settingsNotificationManager.listenForNotificationUpdates()
        .subscribe { onSettingsNotificationChanged() }
    )

    mainScope.launch {
      threadDownloadManager.threadDownloadUpdateFlow
        .debounce(500L)
        .collect { event -> onNewThreadDownloadEvent(event) }
    }

    globalWindowInsetsManager.addInsetsUpdatesListener(this)

    themeEngine.addListener(this)
    onThemeChanged()
  }

  override fun onShow() {
    super.onShow()

    drawerViewModel.updateBadge()
  }

  override fun onThemeChanged() {
    drawerViewModel.onThemeChanged()
    settingsNotificationManager.onThemeChanged()
    navigationViewContract.onThemeChanged(themeEngine.chanTheme)
  }

  override fun onDestroy() {
    super.onDestroy()

    drawerLayout.removeDrawerListener(this)
    themeEngine.removeListener(this)
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    compositeDisposable.clear()
    bottomNavViewGestureDetector.cleanup()
  }

  override fun onInsetsChanged() {
    val navigationViewSize = getDimen(R.dimen.navigation_view_size)

    when (navigationViewContract.type) {
      NavigationViewContract.Type.BottomNavView -> {
        navigationViewContract.actualView.layoutParams.height =
          navigationViewSize + globalWindowInsetsManager.bottom()

        navigationViewContract.updatePaddings(
          leftPadding = null,
          bottomPadding = globalWindowInsetsManager.bottom()
        )
      }
      NavigationViewContract.Type.SideNavView -> {
        navigationViewContract.actualView.layoutParams.width =
          navigationViewSize + globalWindowInsetsManager.left()

        navigationViewContract.updatePaddings(
          leftPadding = globalWindowInsetsManager.left(),
          bottomPadding = globalWindowInsetsManager.bottom()
        )
      }
    }

    bottomPadding.value = calculateBottomPaddingForRecyclerInDp(
      globalWindowInsetsManager = globalWindowInsetsManager,
      mainControllerCallbacks = this
    )
  }

  override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
  }

  override fun onDrawerOpened(drawerView: View) {
    drawerOpenedState.value = true
  }

  override fun onDrawerClosed(drawerView: View) {
    drawerViewModel.clearSelection()
    drawerOpenedState.value = false
  }

  override fun onDrawerStateChanged(newState: Int) {
  }

  fun loadMainControllerDrawerData() {
    drawerViewModel.firstLoadDrawerData()
  }

  fun pushChildController(childController: Controller) {
    if (childControllers.isNotEmpty()) {
      childControllersStack.push(childControllers.last())
    }

    setCurrentChildController(childController)
  }

  private fun setCurrentChildController(childController: Controller) {
    addChildController(childController)
    childController.attachToParentView(container)
    childController.onShow()
  }

  private fun popChildController(isFromOnBack: Boolean): Boolean {
    if (childControllers.isEmpty() || childControllersStack.isEmpty()) {
      return false
    }

    val prevController = childControllers.last()

    if (isFromOnBack) {
      if (prevController is NavigationController && prevController.onBack()) {
        return true
      }
    }

    prevController.onHide()
    removeChildController(prevController)

    if (childControllersStack.isNotEmpty()) {
      val newController = childControllersStack.pop()

      newController.attachToParentView(container)
      newController.onShow()
    }

    if (childControllersStack.isEmpty()) {
      resetBottomNavViewCheckState()
    }

    return true
  }

  fun attachBottomNavViewToToolbar() {
    val topController = top
      ?: return

    if (topController is ToolbarNavigationController) {
      val toolbar = topController.toolbar
      if (toolbar != null) {
        navigationViewContract.setToolbar(toolbar)
      }
    }
  }

  fun openGlobalSearchController() {
    closeAllNonMainControllers()

    val globalSearchController = GlobalSearchController(context, startActivityCallback)
    openControllerWrappedIntoBottomNavAwareController(globalSearchController)

    setGlobalSearchMenuItemSelected()
  }

  fun openArchiveController() {
    closeAllNonMainControllers()

    val localArchiveController = LocalArchiveController(context, this, startActivityCallback)
    openControllerWrappedIntoBottomNavAwareController(localArchiveController)

    setArchiveMenuItemSelected()
  }

  fun openBookmarksController(threadDescriptors: List<ChanDescriptor.ThreadDescriptor>) {
    closeAllNonMainControllers()

    val tabHostController = TabHostController(context, threadDescriptors, this, startActivityCallback)
    openControllerWrappedIntoBottomNavAwareController(tabHostController)

    setBookmarksMenuItemSelected()
  }

  fun openSettingsController() {
    closeAllNonMainControllers()
    openControllerWrappedIntoBottomNavAwareController(MainSettingsControllerV2(context, this))
    setSettingsMenuItemSelected()
  }

  fun openControllerWrappedIntoBottomNavAwareController(controller: Controller) {
    val bottomNavBarAwareNavigationController = BottomNavBarAwareNavigationController(
      context,
      navigationViewContract.type,
      object : BottomNavBarAwareNavigationController.CloseBottomNavBarAwareNavigationControllerListener {
        override fun onCloseController() {
          closeBottomNavBarAwareNavigationControllerListener()
        }

        override fun onShowMenu() {
          onMenuClicked()
        }
      }
    )

    pushChildController(bottomNavBarAwareNavigationController)
    bottomNavBarAwareNavigationController.pushController(controller)
  }

  fun getViewThreadController(): ViewThreadController? {
    var topController: Controller? = top

    if (topController is BottomNavBarAwareNavigationController) {
      topController = childControllers.getOrNull(childControllers.lastIndex - 1)
    }

    if (topController == null) {
      return null
    }

    if (topController is SplitNavigationController) {
      return topController
        .findControllerOrNull { controller -> controller is ViewThreadController }
        as? ViewThreadController
    }

    if (topController is StyledToolbarNavigationController) {
      val threadSlideController = topController.top as? ThreadSlideController
      if (threadSlideController != null) {
        return threadSlideController.getRightController() as? ViewThreadController
      }
    }

    return null
  }

  suspend fun loadThreadWithoutFocusing(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    closeAllNonMainControllers: Boolean = false,
    animated: Boolean
  ) {
    mainScope.launch {
      if (closeAllNonMainControllers) {
        closeAllNonMainControllers()
      }

      topThreadController?.showThreadWithoutFocusing(threadDescriptor, animated)
    }
  }

  suspend fun loadThread(
    descriptor: ChanDescriptor.ThreadDescriptor,
    closeAllNonMainControllers: Boolean = false,
    animated: Boolean
  ) {
    mainScope.launch {
      if (closeAllNonMainControllers) {
        closeAllNonMainControllers()
      }

      topThreadController?.showThread(descriptor, animated)
    }
  }

  fun closeAllNonMainControllers() {
    controllerNavigationManager.onCloseAllNonMainControllers()

    var currentNavController = top
      ?: return

    while (true) {
      if (currentNavController is BottomNavBarAwareNavigationController) {
        popChildController(false)

        currentNavController = top
          ?: return

        continue
      }

      val topController = currentNavController.top
        ?: return

      closeAllChildControllers(topController.childControllers)

      if (topController is HasNavigation) {
        return
      }

      if (currentNavController is NavigationController) {
        currentNavController.popController(false)
      } else if (currentNavController is DoubleNavigationController) {
        currentNavController.popController(false)
      }
    }
  }

  override fun onClick(v: View) {
    // no-op
  }

  fun onMenuClicked() {
    val topController = mainToolbarNavigationController?.top
      ?: return

    if (topController.navigation.hasDrawer) {
      drawerLayout.openDrawer(drawer)
    }
  }

  override fun onBack(): Boolean {
    if (popChildController(true)) {
      return true
    }

    if (drawerViewModel.selectedHistoryEntries.isNotEmpty()) {
      drawerViewModel.clearSelection()
      return true
    }

    if (drawerLayout.isDrawerOpen(drawer)) {
      drawerLayout.closeDrawer(drawer)
      return true
    }

    return super.onBack()
  }

  override fun hideBottomNavBar(lockTranslation: Boolean, lockCollapse: Boolean) {
    navigationViewContract.hide(lockTranslation, lockCollapse)
  }

  override fun showBottomNavBar(unlockTranslation: Boolean, unlockCollapse: Boolean) {
    navigationViewContract.show(unlockTranslation, unlockCollapse)
  }

  override fun resetBottomNavViewState(unlockTranslation: Boolean, unlockCollapse: Boolean) {
    navigationViewContract.resetState(unlockTranslation, unlockCollapse)
  }

  override fun passMotionEventIntoDrawer(event: MotionEvent): Boolean {
    return drawerLayout.onTouchEvent(event)
  }

  override fun resetBottomNavViewCheckState() {
    BackgroundUtils.ensureMainThread()

    // Hack! To reset the bottomNavView's checked item to "browse" when pressing back one either
    // of the bottomNavView's child controllers (Bookmarks or Settings)
    setBrowseMenuItemSelected()
  }

  override fun onBottomPanelStateChanged(func: (BottomMenuPanel.State) -> Unit) {
    bottomMenuPanel.onBottomPanelStateChanged(func)
  }

  override fun showBottomPanel(items: List<BottomMenuPanelItem>) {
    navigationViewContract.actualView.isEnabled = false
    bottomMenuPanel.show(items)
  }

  override fun hideBottomPanel() {
    navigationViewContract.actualView.isEnabled = true
    bottomMenuPanel.hide()
  }

  override fun passOnBackToBottomPanel(): Boolean {
    return bottomMenuPanel.onBack()
  }

  fun setBrowseMenuItemSelected() {
    navigationViewContract.setMenuItemSelected(R.id.action_browse)
    kurobaComposeBottomPanel.setMenuItemSelected(R.id.action_browse)
  }

  fun setArchiveMenuItemSelected() {
    navigationViewContract.setMenuItemSelected(R.id.action_archive)
    kurobaComposeBottomPanel.setMenuItemSelected(R.id.action_archive)
  }

  fun setSettingsMenuItemSelected() {
    navigationViewContract.setMenuItemSelected(R.id.action_settings)
    kurobaComposeBottomPanel.setMenuItemSelected(R.id.action_settings)
  }

  fun setBookmarksMenuItemSelected() {
    navigationViewContract.setMenuItemSelected(R.id.action_bookmarks)
    kurobaComposeBottomPanel.setMenuItemSelected(R.id.action_bookmarks)
  }

  fun setGlobalSearchMenuItemSelected() {
    navigationViewContract.setMenuItemSelected(R.id.action_search)
    kurobaComposeBottomPanel.setMenuItemSelected(R.id.action_search)
  }

  fun setDrawerEnabled(enabled: Boolean) {
    val lockMode = if (enabled) {
      DrawerLayout.LOCK_MODE_UNLOCKED
    } else {
      DrawerLayout.LOCK_MODE_LOCKED_CLOSED
    }

    drawerLayout.setDrawerLockMode(lockMode, GravityCompat.START)

    if (!enabled) {
      drawerLayout.closeDrawer(drawer)
    }
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
      onCancelClicked = { imageSaverV2.deleteDownload(uniqueId) }
    )

    val imageSaverV2OptionsController = ImageSaverV2OptionsController(context, options)
    presentController(imageSaverV2OptionsController)
  }

  @Composable
  private fun ColumnScope.BuildContent() {
    val historyControllerState by drawerViewModel.historyControllerState
    val searchState = rememberSimpleSearchState<NavigationHistoryEntry>()

    BuildNavigationHistoryListHeader(
      searchQuery = searchState.queryState,
      onSearchQueryChanged = { newQuery -> searchState.query = newQuery },
      onSwitchDayNightThemeIconClick = {
        if (TimeUtils.isHalloweenToday()) {
          showToast(R.string.not_allowed_during_halloween)
          return@BuildNavigationHistoryListHeader
        }

        rootLayout.postDelayed({ themeEngine.toggleTheme() }, 125L)
      },
      onShowDrawerOptionsIconClick = { showDrawerOptions() }
    )

    when (historyControllerState) {
      HistoryControllerState.Loading -> {
        KurobaComposeProgressIndicator()
      }
      is HistoryControllerState.Error -> {
        KurobaComposeErrorMessage(
          errorMessage = (historyControllerState as HistoryControllerState.Error).errorText
        )
      }
      is HistoryControllerState.Data -> {
        val navHistoryEntryList = remember { drawerViewModel.navigationHistoryEntryList }
        if (navHistoryEntryList.isEmpty()) {
          KurobaComposeText(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            text = stringResource(id = R.string.drawer_controller_navigation_history_is_empty),
            textAlign = TextAlign.Center,
          )
        } else {
          BuildNavigationHistoryList(
            navHistoryEntryList = navHistoryEntryList,
            searchState = searchState,
            onHistoryEntryViewClicked = { navHistoryEntry ->
              onHistoryEntryViewClicked(navHistoryEntry)

              mainScope.launch {
                delay(100L)
                searchState.reset()
              }
            },
            onHistoryEntryViewLongClicked = { navHistoryEntry ->
              onHistoryEntryViewLongClicked(navHistoryEntry)
            },
            onHistoryEntrySelectionChanged = { currentlySelected, navHistoryEntry ->
              drawerViewModel.selectUnselect(navHistoryEntry, currentlySelected.not())
            },
            onNavHistoryDeleteClicked = { navHistoryEntry ->
              onNavHistoryDeleteClicked(navHistoryEntry)
            }
          )
        }
      }
    }

    if (!ChanSettings.isNavigationViewEnabled() && !ChanSettings.isSplitLayoutMode()) {
      kurobaComposeBottomPanel.BuildPanel(
        onMenuItemClicked = { clickedMenuItemId ->
          onNavigationItemSelectedListener(clickedMenuItemId)

          if (drawerLayout.isDrawerOpen(drawer)) {
            drawerLayout.closeDrawer(drawer)
          }
        }
      )
    }
  }

  @Composable
  private fun ColumnScope.BuildNavigationHistoryList(
    navHistoryEntryList: List<NavigationHistoryEntry>,
    searchState: SimpleSearchState<NavigationHistoryEntry>,
    onHistoryEntryViewClicked: (NavigationHistoryEntry) -> Unit,
    onHistoryEntryViewLongClicked: (NavigationHistoryEntry) -> Unit,
    onHistoryEntrySelectionChanged: (Boolean, NavigationHistoryEntry) -> Unit,
    onNavHistoryDeleteClicked: (NavigationHistoryEntry) -> Unit
  ) {
    LaunchedEffect(key1 = searchState.query, block = {
      if (searchState.query.isEmpty()) {
        searchState.results = navHistoryEntryList
        return@LaunchedEffect
      }

      delay(125L)

      withContext(Dispatchers.Default) {
        searchState.searching = true
        searchState.results = processSearchQuery(searchState.query, navHistoryEntryList)
        searchState.searching = false
      }
    })

    if (searchState.searching) {
      KurobaComposeProgressIndicator()
      return
    }

    val query = searchState.query
    val searchResults = searchState.results

    if (searchResults.isEmpty()) {
      KurobaComposeText(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(8.dp),
        textAlign = TextAlign.Center,
        text = stringResource(id = R.string.search_nothing_found_with_query, query)
      )

      return
    }

    val selectedHistoryEntries = remember { drawerViewModel.selectedHistoryEntries }
    val isLowRamDevice = ChanSettings.isLowRamDevice()
    val padding by bottomPadding
    val contentPadding = remember(key1 = padding) { PaddingValues(bottom = 4.dp + padding.dp) }

    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      val chanTheme = LocalChanTheme.current
      val drawerGridMode by drawerViewModel.drawerGridMode

      if (drawerGridMode) {
        val state = rememberLazyGridState()

        val spanCount = with(LocalDensity.current) {
          (maxWidth.toPx() / GRID_COLUMN_WIDTH).toInt().coerceIn(MIN_SPAN_COUNT, MAX_SPAN_COUNT)
        }

        LazyVerticalGrid(
          state = state,
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .simpleVerticalScrollbar(state, chanTheme, contentPadding),
          contentPadding = contentPadding,
          columns = GridCells.Fixed(count = spanCount),
          content = {
            items(count = searchResults.size) { index ->
              val navHistoryEntry = searchResults[index]
              val isSelectionMode = selectedHistoryEntries.isNotEmpty()
              val isSelected = selectedHistoryEntries.contains(navHistoryEntry.descriptor)

              key(searchResults[index].descriptor) {
                BuildNavigationHistoryListEntryGridMode(
                  searchQuery = query,
                  navHistoryEntry = navHistoryEntry,
                  isSelectionMode = isSelectionMode,
                  isSelected = isSelected,
                  isLowRamDevice = isLowRamDevice,
                  onHistoryEntryViewClicked = onHistoryEntryViewClicked,
                  onHistoryEntryViewLongClicked = onHistoryEntryViewLongClicked,
                  onHistoryEntrySelectionChanged = onHistoryEntrySelectionChanged,
                  onNavHistoryDeleteClicked = onNavHistoryDeleteClicked
                )
              }
            }
          })
      } else {
        val state = rememberLazyListState()

        LazyColumn(
          state = state,
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .simpleVerticalScrollbar(state, chanTheme, contentPadding),
          contentPadding = contentPadding,
          content = {
            items(count = searchResults.size) { index ->
              val navHistoryEntry = searchResults[index]
              val isSelectionMode = selectedHistoryEntries.isNotEmpty()
              val isSelected = selectedHistoryEntries.contains(navHistoryEntry.descriptor)

              key(searchResults[index].descriptor) {
                BuildNavigationHistoryListEntryListMode(
                  searchQuery = query,
                  navHistoryEntry = navHistoryEntry,
                  isSelectionMode = isSelectionMode,
                  isSelected = isSelected,
                  isLowRamDevice = isLowRamDevice,
                  onHistoryEntryViewClicked = onHistoryEntryViewClicked,
                  onHistoryEntryViewLongClicked = onHistoryEntryViewLongClicked,
                  onHistoryEntrySelectionChanged = onHistoryEntrySelectionChanged,
                  onNavHistoryDeleteClicked = onNavHistoryDeleteClicked
                )
              }
            }
          })
      }
    }
  }

  @Composable
  private fun BuildNavigationHistoryListHeader(
    searchQuery: MutableState<String>,
    onSearchQueryChanged: (String) -> Unit,
    onSwitchDayNightThemeIconClick: () -> Unit,
    onShowDrawerOptionsIconClick: () -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val backgroundColor = chanTheme.primaryColorCompose

    val currentInsetsCompose by globalWindowInsetsManager.currentInsetsCompose
    val topInset = currentInsetsCompose.calculateTopPadding()
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

    Row(
      modifier = Modifier
        .background(backgroundColor)
    ) {
      Column(modifier = Modifier
        .fillMaxWidth()
        .height(toolbarHeight + topInset)
      ) {
        Spacer(modifier = Modifier.height(topInset))

        Row(
          modifier = Modifier
            .fillMaxHeight()
            .padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
          horizontalArrangement = Arrangement.End
        ) {
          Row(
            modifier = Modifier
              .wrapContentHeight()
              .weight(1f)
          ) {
            KurobaSearchInput(
              modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp),
              chanTheme = chanTheme,
              onBackgroundColor = backgroundColor,
              searchQueryState = searchQuery,
              onSearchQueryChanged = onSearchQueryChanged
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          KurobaComposeIcon(
            drawableId = R.drawable.ic_baseline_wb_sunny_24,
            modifier = Modifier
              .align(Alignment.CenterVertically)
              .kurobaClickable(onClick = onSwitchDayNightThemeIconClick),
            colorBehindIcon = backgroundColor
          )

          Spacer(modifier = Modifier.width(16.dp))

          KurobaComposeIcon(
            drawableId = R.drawable.ic_more_vert_white_24dp,
            modifier = Modifier
              .align(Alignment.CenterVertically)
              .kurobaClickable(onClick = onShowDrawerOptionsIconClick),
            colorBehindIcon = backgroundColor
          )
        }
      }
    }
  }

  @Composable
  private fun BuildNavigationHistoryListEntryListMode(
    searchQuery: String,
    navHistoryEntry: NavigationHistoryEntry,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isLowRamDevice: Boolean,
    onHistoryEntryViewClicked: (NavigationHistoryEntry) -> Unit,
    onHistoryEntryViewLongClicked: (NavigationHistoryEntry) -> Unit,
    onHistoryEntrySelectionChanged: (Boolean, NavigationHistoryEntry) -> Unit,
    onNavHistoryDeleteClicked: (NavigationHistoryEntry) -> Unit
  ) {
    val chanDescriptor = navHistoryEntry.descriptor
    val chanTheme = LocalChanTheme.current

    val circleCropTransformation = remember(key1 = chanDescriptor) {
      if (chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
        emptyList()
      } else {
        listOf(CIRCLE_CROP)
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(LIST_MODE_ROW_HEIGHT)
        .padding(all = 2.dp)
        .kurobaClickable(
          bounded = true,
          onClick = {
            if (isSelectionMode) {
              onHistoryEntrySelectionChanged(isSelected, navHistoryEntry)
            } else {
              onHistoryEntryViewClicked(navHistoryEntry)
            }
          },
          onLongClick = {
            onHistoryEntryViewLongClicked(navHistoryEntry)
          }
        ),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box {
        val contentScale = if (navHistoryEntry.descriptor is ChanDescriptor.ICatalogDescriptor) {
          ContentScale.Fit
        } else {
          ContentScale.Crop
        }

        val thumbnailRequest = remember(key1 = chanDescriptor) {
          if (navHistoryEntry.isCompositeIconUrl) {
            ImageLoaderRequest(
              data = ImageLoaderRequestData.DrawableResource(R.drawable.composition_icon),
              transformations = circleCropTransformation
            )
          } else {
            ImageLoaderRequest(
              data = ImageLoaderRequestData.Url(
                httpUrl = navHistoryEntry.threadThumbnailUrl,
                cacheFileType = CacheFileType.NavHistoryThumbnail
              ),
              transformations = circleCropTransformation
            )
          }
        }

        KurobaComposeImage(
          request = thumbnailRequest,
          contentScale = contentScale,
          modifier = Modifier
            .size(LIST_MODE_ROW_HEIGHT)
            .padding(horizontal = 6.dp, vertical = 2.dp),
          imageLoaderV2 = imageLoaderV2
        )

        val showDeleteButtonShortcut by remember { drawerViewModel.showDeleteButtonShortcut }

        if (isSelectionMode) {
          KurobaComposeSelectionIndicator(
            size = NAV_HISTORY_DELETE_BTN_SIZE,
            currentlySelected = isSelected,
            onSelectionChanged = { checked -> drawerViewModel.selectUnselect(navHistoryEntry, checked) }
          )
        } else if (showDeleteButtonShortcut) {
          val shape = remember { CircleShape }

          Box(
            modifier = Modifier
              .align(Alignment.TopStart)
              .size(NAV_HISTORY_DELETE_BTN_SIZE)
              .kurobaClickable(onClick = { onNavHistoryDeleteClicked(navHistoryEntry) })
              .background(color = NAV_HISTORY_DELETE_BTN_BG_COLOR, shape = shape)
          ) {
            Image(
              modifier = Modifier.padding(4.dp),
              painter = painterResource(id = R.drawable.ic_clear_white_24dp),
              contentDescription = null
            )
          }
        }

        Column(
          modifier = Modifier
            .wrapContentHeight()
            .wrapContentWidth()
            .align(Alignment.TopEnd)
        ) {
          val siteIconRequest = remember(key1 = chanDescriptor) {
            if (navHistoryEntry.siteThumbnailUrl != null) {
              val data = ImageLoaderRequestData.Url(
                httpUrl = navHistoryEntry.siteThumbnailUrl,
                cacheFileType = CacheFileType.SiteIcon
              )

              ImageLoaderRequest(data = data)
            } else {
              null
            }
          }

          if (siteIconRequest != null) {
            KurobaComposeImage(
              request = siteIconRequest,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(20.dp),
              imageLoaderV2 = imageLoaderV2,
              error = {
                Image(
                  modifier = Modifier.fillMaxSize(),
                  painter = painterResource(id = R.drawable.error_icon),
                  contentDescription = null
                )
              }
            )

            Spacer(modifier = Modifier.weight(1f))
          }

          if (navHistoryEntry.pinned) {
            Image(
              modifier = Modifier.size(20.dp),
              painter = painterResource(id = R.drawable.sticky_icon),
              contentDescription = null
            )
          }
        }
      }

      KurobaComposeText(
        modifier = Modifier
          .weight(1f)
          .wrapContentHeight(),
        text = navHistoryEntry.title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontSize = 16.sp
      )

      if (navHistoryEntry.additionalInfo != null) {
        BuildAdditionalBookmarkInfoText(
          isLowRamDevice = isLowRamDevice,
          isListMode = true,
          searchQuery = searchQuery,
          additionalInfo = navHistoryEntry.additionalInfo,
          chanTheme = chanTheme
        )
      }
    }
  }

  @Composable
  private fun BuildNavigationHistoryListEntryGridMode(
    searchQuery: String,
    navHistoryEntry: NavigationHistoryEntry,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isLowRamDevice: Boolean,
    onHistoryEntryViewClicked: (NavigationHistoryEntry) -> Unit,
    onHistoryEntryViewLongClicked: (NavigationHistoryEntry) -> Unit,
    onHistoryEntrySelectionChanged: (Boolean, NavigationHistoryEntry) -> Unit,
    onNavHistoryDeleteClicked: (NavigationHistoryEntry) -> Unit
  ) {
    val chanDescriptor = navHistoryEntry.descriptor
    val chanTheme = LocalChanTheme.current

    val siteIconRequest = remember(key1 = chanDescriptor) {
      if (navHistoryEntry.siteThumbnailUrl != null) {
        val data = ImageLoaderRequestData.Url(
          httpUrl = navHistoryEntry.siteThumbnailUrl,
          cacheFileType = CacheFileType.SiteIcon
        )

        ImageLoaderRequest(data)
      } else {
        null
      }
    }

    val thumbnailRequest = remember(key1 = chanDescriptor) {
      if (navHistoryEntry.isCompositeIconUrl) {
        ImageLoaderRequest(ImageLoaderRequestData.DrawableResource(R.drawable.composition_icon))
      } else {
        val data = ImageLoaderRequestData.Url(
          httpUrl = navHistoryEntry.threadThumbnailUrl,
          cacheFileType = CacheFileType.NavHistoryThumbnail
        )

        ImageLoaderRequest(data)
      }
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(all = 2.dp)
        .kurobaClickable(
          onClick = {
            if (isSelectionMode) {
              onHistoryEntrySelectionChanged(isSelected, navHistoryEntry)
            } else {
              onHistoryEntryViewClicked(navHistoryEntry)
            }
          },
          onLongClick = {
            onHistoryEntryViewLongClicked(navHistoryEntry)
          }
        ),
    ) {
      Box {
        val contentScale = if (navHistoryEntry.descriptor is ChanDescriptor.ICatalogDescriptor) {
          ContentScale.Fit
        } else {
          ContentScale.Crop
        }

        KurobaComposeImage(
          request = thumbnailRequest,
          contentScale = contentScale,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          imageLoaderV2 = imageLoaderV2
        )

        val showDeleteButtonShortcut by remember { drawerViewModel.showDeleteButtonShortcut }

        if (isSelectionMode) {
          KurobaComposeSelectionIndicator(
            size = NAV_HISTORY_DELETE_BTN_SIZE,
            currentlySelected = isSelected,
            onSelectionChanged = { checked -> drawerViewModel.selectUnselect(navHistoryEntry, checked) }
          )
        } else if (showDeleteButtonShortcut) {
          val shape = remember { CircleShape }

          Box(
            modifier = Modifier
              .align(Alignment.TopStart)
              .size(NAV_HISTORY_DELETE_BTN_SIZE)
              .kurobaClickable(onClick = { onNavHistoryDeleteClicked(navHistoryEntry) })
              .background(color = NAV_HISTORY_DELETE_BTN_BG_COLOR, shape = shape)
          ) {
            Image(
              modifier = Modifier.padding(4.dp),
              painter = painterResource(id = R.drawable.ic_clear_white_24dp),
              contentDescription = null
            )
          }
        }

        Row(
          modifier = Modifier
            .wrapContentHeight()
            .wrapContentWidth()
            .align(Alignment.TopEnd)
        ) {

          if (navHistoryEntry.pinned) {
            Image(
              modifier = Modifier.size(20.dp),
              painter = painterResource(id = R.drawable.sticky_icon),
              contentDescription = null
            )
          }

          if (siteIconRequest != null) {
            KurobaComposeImage(
              request = siteIconRequest,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(20.dp),
              imageLoaderV2 = imageLoaderV2,
              error = {
                Image(
                  modifier = Modifier.fillMaxSize(),
                  painter = painterResource(id = R.drawable.error_icon),
                  contentDescription = null
                )
              }
            )
          }
        }
      }

      if (navHistoryEntry.additionalInfo != null) {
        BuildAdditionalBookmarkInfoText(
          isLowRamDevice = isLowRamDevice,
          isListMode = false,
          searchQuery = searchQuery,
          additionalInfo = navHistoryEntry.additionalInfo,
          chanTheme = chanTheme,
        )
      }

      KurobaComposeText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        text = navHistoryEntry.title,
        maxLines = 4,
        fontSize = 12.sp,
        textAlign = TextAlign.Center
      )
    }
  }

  @Composable
  private fun BuildAdditionalBookmarkInfoText(
    isLowRamDevice: Boolean,
    isListMode: Boolean,
    searchQuery: String,
    additionalInfo: NavHistoryBookmarkAdditionalInfo,
    chanTheme: ChanTheme
  ) {
    val drawerOpened by drawerOpenedState

    // Now this is epic. So what this thing does (and ^ this one above), they receive all changes
    // to navigation history elements and if the drawer is currently opened display them (with or
    // without animation depending on settings and other stuff) but if the drawer is currently closed
    // then the changes are accumulated and the next time the drawer is opened the difference is
    // displayed. Basically once you open the drawer you will see the changes applied to bookmarks
    // during the time the drawer was closed.
    val prevAdditionalInfoState = remember { mutableStateOf(additionalInfo.copy()) }
    val prevAdditionalInfo by prevAdditionalInfoState

    val currentAdditionalInfo = if (drawerOpened) {
      additionalInfo
    } else {
      prevAdditionalInfo
    }

    val transition = updateTransition(
      targetState = currentAdditionalInfo,
      label = "Text transition animation"
    )

    val animationDisabled = isLowRamDevice
      || searchQuery.isNotEmpty()
      || !drawerOpened
      || prevAdditionalInfo == currentAdditionalInfo

    val textAnimationSpec: FiniteAnimationSpec<Int> = if (animationDisabled) {
      // This will disable animations, basically it will switch to the final animation frame right
      // away
      snap()
    } else {
      tween(durationMillis = TEXT_ANIMATION_DURATION)
    }

    val newPostsCountAnimated by transition.animateInt(
      transitionSpec = { textAnimationSpec },
      label = "New posts animation"
    ) { info -> info.newPosts }
    val newQuotesCountAnimated by transition.animateInt(
      transitionSpec = { textAnimationSpec },
      label = "New quotes animation"
    ) { info -> info.newQuotes }

    val additionalInfoString = remember(
      additionalInfo,
      chanTheme,
      newPostsCountAnimated,
      newQuotesCountAnimated
    ) {
      return@remember currentAdditionalInfo.toAnnotatedString(
        chanTheme = chanTheme,
        newPostsCount = newPostsCountAnimated,
        newQuotesCount = newQuotesCountAnimated
      )
    }

    val alpha = .35f

    val targetColor = if (transition.isRunning) {
      when {
        currentAdditionalInfo.newQuotes > 0 -> {
          chanTheme.bookmarkCounterHasRepliesColorCompose.copy(alpha = alpha)
        }
        currentAdditionalInfo.newPosts > 0 || currentAdditionalInfo.watching -> {
          chanTheme.bookmarkCounterNormalColorCompose.copy(alpha = alpha)
        }
        else -> {
          chanTheme.bookmarkCounterNotWatchingColorCompose.copy(alpha = alpha)
        }
      }
    } else {
      Color.Unspecified
    }

    val shape = if (transition.isRunning) {
      RoundedCornerShape(4.dp)
    } else {
      RectangleShape
    }

    val bgAnimationSpec: AnimationSpec<Color> = if (animationDisabled) {
      snap()
    } else {
      tween(durationMillis = TEXT_ANIMATION_DURATION)
    }

    val backgroundColor by animateColorAsState(
      targetValue = targetColor,
      animationSpec = bgAnimationSpec
    )

    if (isListMode) {
      KurobaComposeText(
        modifier = Modifier
          .wrapContentWidth()
          .wrapContentHeight()
          .background(color = backgroundColor, shape = shape)
          .padding(horizontal = 6.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontSize = 16.sp,
        text = additionalInfoString
      )
    } else {
      KurobaComposeText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .background(color = backgroundColor, shape = shape),
        maxLines = 1,
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        fontSize = 14.sp,
        text = additionalInfoString
      )
    }

    if (drawerOpened) {
      prevAdditionalInfoState.value = additionalInfo.copy()
    }
  }

  private fun showDrawerOptions() {
    val drawerOptions = mutableListOf<FloatingListMenuItem>()

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_GRID_MODE,
      name = getString(R.string.drawer_controller_grid_mode),
      isCurrentlySelected = ChanSettings.drawerGridMode.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_MOVE_LAST_ACCESSED_THREAD_TO_TOP,
      name = getString(R.string.drawer_controller_move_last_accessed_thread_to_top),
      isCurrentlySelected = ChanSettings.drawerMoveLastAccessedThreadToTop.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_SHOW_BOOKMARKS,
      name = getString(R.string.drawer_controller_show_bookmarks),
      isCurrentlySelected = ChanSettings.drawerShowBookmarkedThreads.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_SHOW_NAV_HISTORY,
      name = getString(R.string.drawer_controller_show_navigation_history),
      isCurrentlySelected = ChanSettings.drawerShowNavigationHistory.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_SHOW_DELETE_SHORTCUT,
      name = getString(R.string.drawer_controller_delete_shortcut),
      isCurrentlySelected = ChanSettings.drawerShowDeleteButtonShortcut.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_DELETE_BOOKMARK_WHEN_DELETING_NAV_HISTORY,
      name = getString(R.string.drawer_controller_delete_bookmark_on_history_delete),
      isCurrentlySelected = ChanSettings.drawerDeleteBookmarksWhenDeletingNavHistory.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_DELETE_NAV_HISTORY_WHEN_BOOKMARK_DELETED,
      name = getString(R.string.drawer_controller_delete_nav_history_on_bookmark_delete),
      isCurrentlySelected = ChanSettings.drawerDeleteNavHistoryWhenBookmarkDeleted.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_RESTORE_LAST_VISITED_CATALOG,
      name = getString(R.string.setting_load_last_opened_board_upon_app_start_title),
      isCurrentlySelected = ChanSettings.loadLastOpenedBoardUponAppStart.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_RESTORE_LAST_VISITED_THREAD,
      name = getString(R.string.setting_load_last_opened_thread_upon_app_start_title),
      isCurrentlySelected = ChanSettings.loadLastOpenedThreadUponAppStart.get()
    )

    drawerOptions += FloatingListMenuItem(
      key = ACTION_CLEAR_NAV_HISTORY,
      name = getString(R.string.drawer_controller_clear_nav_history)
    )

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = drawerOptions,
      itemClickListener = { item ->
        mainScope.launch {
          when (item.key) {
            ACTION_GRID_MODE -> {
              val drawerGridMode = ChanSettings.drawerGridMode.toggle()
              drawerViewModel.drawerGridMode.value = drawerGridMode
            }
            ACTION_MOVE_LAST_ACCESSED_THREAD_TO_TOP -> {
              ChanSettings.drawerMoveLastAccessedThreadToTop.toggle()
            }
            ACTION_SHOW_BOOKMARKS -> {
              drawerViewModel.deleteBookmarkedNavHistoryElements()
            }
            ACTION_SHOW_NAV_HISTORY -> {
              ChanSettings.drawerShowNavigationHistory.toggle()
              drawerViewModel.reloadNavigationHistory()
            }
            ACTION_SHOW_DELETE_SHORTCUT -> {
              drawerViewModel.updateDeleteButtonShortcut(ChanSettings.drawerShowDeleteButtonShortcut.toggle())
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
                negativeButtonText = getString(R.string.do_not),
                positiveButtonText = getString(R.string.clear),
                onPositiveButtonClickListener = {
                  mainScope.launch { historyNavigationManager.clear() }
                }
              )
            }
          }
        }
      }
    )

    presentController(floatingListMenuController)
  }

  private fun onHistoryEntryViewLongClicked(navHistoryEntry: NavigationHistoryEntry) {
    val drawerOptions = mutableListOf<FloatingListMenuItem>()

    if (drawerViewModel.selectedHistoryEntries.isEmpty()) {
      drawerOptions += FloatingListMenuItem(
        key = ACTION_START_SELECTION,
        name = getString(R.string.drawer_controller_start_navigation_history_selection)
      )

      drawerOptions += FloatingListMenuItem(
        key = ACTION_SELECT_ALL,
        name = getString(R.string.drawer_controller_navigation_history_select_all)
      )
    }

    if (drawerViewModel.selectedHistoryEntries.isNotEmpty()) {
      drawerOptions += FloatingListMenuItem(
        key = ACTION_PIN_UNPIN_SELECTED,
        name = getString(R.string.drawer_controller_pin_unpin_selected)
      )

      drawerOptions += FloatingListMenuItem(
        key = ACTION_DELETE_SELECTED,
        name = getString(R.string.drawer_controller_delete_selected)
      )
    }

    if (drawerViewModel.selectedHistoryEntries.isEmpty()) {
      drawerOptions += FloatingListMenuItem(
        key = ACTION_PIN_UNPIN,
        name = getString(R.string.drawer_controller_pin_unpin)
      )

      if (navHistoryEntry.descriptor is ChanDescriptor.ThreadDescriptor) {
        drawerOptions += FloatingListMenuItem(
          key = ACTION_BOOKMARK_UNBOOKMARK,
          name = getString(R.string.drawer_controller_bookmark_unbookmark)
        )
      }

      if (navHistoryEntry.descriptor.isThreadDescriptor() && navHistoryEntry.additionalInfo != null) {
        drawerOptions += FloatingListMenuItem(
          key = ACTION_SHOW_IN_BOOKMARKS,
          name = getString(R.string.drawer_controller_show_in_bookmarks)
        )
      }

      drawerOptions += FloatingListMenuItem(
        key = ACTION_DELETE,
        name = getString(R.string.drawer_controller_delete_one)
      )
    }

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = drawerOptions,
      itemClickListener = { item ->
        mainScope.launch {
          when (item.key) {
            ACTION_START_SELECTION -> {
              drawerViewModel.toggleSelection(navHistoryEntry)
            }
            ACTION_SELECT_ALL -> {
              drawerViewModel.selectAll()
            }
            ACTION_PIN_UNPIN -> {
              pinUnpin(listOf(navHistoryEntry.descriptor))
            }
            ACTION_BOOKMARK_UNBOOKMARK -> {
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
            ACTION_PIN_UNPIN_SELECTED -> {
              pinUnpin(drawerViewModel.getSelectedDescriptors())
              drawerViewModel.clearSelection()
            }
            ACTION_DELETE_SELECTED -> {
              onNavHistoryDeleteSelectedClicked(drawerViewModel.getSelectedDescriptors())
              drawerViewModel.clearSelection()
            }
            ACTION_DELETE -> {
              onNavHistoryDeleteClicked(navHistoryEntry)
            }
            ACTION_SHOW_IN_BOOKMARKS -> {
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

  private suspend fun pinUnpin(descriptors: Collection<ChanDescriptor>) {
    if (descriptors.isEmpty()) {
      return
    }

    when (drawerViewModel.pinOrUnpin(descriptors)) {
      HistoryNavigationManager.PinResult.Pinned -> {
        val text = if (descriptors.size == 1) {
          getString(R.string.drawer_controller_navigation_entry_pinned_one, descriptors.first().userReadableString())
        } else {
          getString(R.string.drawer_controller_navigation_entry_pinned_many, descriptors.size)
        }

        showToast(text)
      }
      HistoryNavigationManager.PinResult.Unpinned -> {
        val text = if (descriptors.size == 1) {
          getString(R.string.drawer_controller_navigation_entry_unpinned_one, descriptors.first().userReadableString())
        } else {
          getString(R.string.drawer_controller_navigation_entry_unpinned_many, descriptors.size)
        }

        showToast(text)
      }
      HistoryNavigationManager.PinResult.Failure -> {
        showToast(getString(R.string.drawer_controller_navigation_entry_failed_to_pin_unpin))
      }
    }
  }

  private fun onNavHistoryDeleteClicked(navHistoryEntry: NavigationHistoryEntry) {
    mainScope.launch {
      drawerViewModel.deleteNavElement(navHistoryEntry)

      val text = getString(
        R.string.drawer_controller_navigation_entry_deleted_one,
        navHistoryEntry.descriptor.userReadableString()
      )

      showToast(text)
    }
  }

  private fun onNavHistoryDeleteSelectedClicked(selected: List<ChanDescriptor>) {
    if (selected.isEmpty()) {
      return
    }

    mainScope.launch {
      drawerViewModel.deleteNavElementsByDescriptors(selected)

      val text = getString(
        R.string.drawer_controller_navigation_entry_deleted_many,
        selected.size
      )

      showToast(text)
    }
  }

  private fun onHistoryEntryViewClicked(navHistoryEntry: NavigationHistoryEntry) {
    mainScope.launch {
      val currentTopThreadController = topThreadController
        ?: return@launch

      if (top is BottomNavBarAwareNavigationController) {
        closeAllNonMainControllers()
      }

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

  private fun onNavigationItemSelectedListener(menuItemId: Int) {
    when (menuItemId) {
      R.id.action_search -> openGlobalSearchController()
      R.id.action_archive -> openArchiveController()
      R.id.action_browse -> closeAllNonMainControllers()
      R.id.action_bookmarks -> openBookmarksController(emptyList())
      R.id.action_settings -> openSettingsController()
    }
  }

  private fun closeBottomNavBarAwareNavigationControllerListener() {
    val currentNavController = top
      ?: return

    if (currentNavController !is BottomNavBarAwareNavigationController) {
      return
    }

    popChildController(false)
  }

  private fun closeAllChildControllers(childControllers: List<Controller>) {
    for (childController in childControllers) {
      childController.presentingThisController?.stopPresenting(false)

      if (childController.childControllers.isNotEmpty()) {
        closeAllChildControllers(childController.childControllers)
      }
    }
  }

  private fun onBookmarksBadgeStateChanged(state: MainControllerViewModel.BookmarksBadgeState) {
    if (state.totalUnseenPostsCount <= 0) {
      navigationViewContract.updateBadge(
        menuItemId = R.id.action_bookmarks,
        menuItemBadgeInfo = null
      )

      kurobaComposeBottomPanel.updateBadge(
        menuItemId = R.id.action_bookmarks,
        menuItemBadgeInfo = null
      )

      mainToolbarNavigationController?.toolbar?.arrowMenuDrawable?.setBadge(0, false)
    } else {
      navigationViewContract.updateBadge(
        menuItemId = R.id.action_bookmarks,
        menuItemBadgeInfo = KurobaComposeIconPanel.MenuItemBadgeInfo.Counter(
          counter = state.totalUnseenPostsCount,
          highlight = state.hasUnreadReplies
        )
      )

      kurobaComposeBottomPanel.updateBadge(
        menuItemId = R.id.action_bookmarks,
        menuItemBadgeInfo = KurobaComposeIconPanel.MenuItemBadgeInfo.Counter(
          counter = state.totalUnseenPostsCount,
          highlight = state.hasUnreadReplies
        )
      )

      mainToolbarNavigationController?.toolbar?.arrowMenuDrawable?.setBadge(
        state.totalUnseenPostsCount,
        state.hasUnreadReplies
      )
    }
  }

  private suspend fun onNewThreadDownloadEvent(event: ThreadDownloadManager.Event) {
    val activeThreadDownloadsCount = threadDownloadManager.getAllActiveThreadDownloads().size

    if (activeThreadDownloadsCount <= 0) {
      navigationViewContract.updateBadge(
        menuItemId = R.id.action_archive,
        menuItemBadgeInfo = null
      )

      kurobaComposeBottomPanel.updateBadge(
        menuItemId = R.id.action_archive,
        menuItemBadgeInfo = null
      )
    } else {
      navigationViewContract.updateBadge(
        menuItemId = R.id.action_archive,
        menuItemBadgeInfo = KurobaComposeIconPanel.MenuItemBadgeInfo.Counter(
          counter = activeThreadDownloadsCount,
          highlight = false
        )
      )

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
      navigationViewContract.updateBadge(
        menuItemId = R.id.action_settings,
        menuItemBadgeInfo = null
      )

      kurobaComposeBottomPanel.updateBadge(
        menuItemId = R.id.action_settings,
        menuItemBadgeInfo = null
      )
    } else {
      navigationViewContract.updateBadge(
        menuItemId = R.id.action_settings,
        menuItemBadgeInfo = KurobaComposeIconPanel.MenuItemBadgeInfo.Dot
      )

      kurobaComposeBottomPanel.updateBadge(
        menuItemId = R.id.action_settings,
        menuItemBadgeInfo = KurobaComposeIconPanel.MenuItemBadgeInfo.Dot
      )
    }
  }

  private fun processSearchQuery(
    query: String,
    navHistoryEntryList: List<NavigationHistoryEntry>
  ): List<NavigationHistoryEntry> {
    if (query.isEmpty()) {
      return navHistoryEntryList
    }

    return navHistoryEntryList.filter { navigationHistoryEntry ->
      navigationHistoryEntry.title.contains(other = query, ignoreCase = true)
    }
  }

  companion object {
    private const val TAG = "MainController"
    private const val MIN_SPAN_COUNT = 3
    private const val MAX_SPAN_COUNT = 6
    private const val TEXT_ANIMATION_DURATION = 1000

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

    private val GRID_COLUMN_WIDTH = dp(80f)
    private val LIST_MODE_ROW_HEIGHT = 52.dp
    private val NAV_HISTORY_DELETE_BTN_SIZE = 24.dp
    private val NAV_HISTORY_DELETE_BTN_BG_COLOR = Color(0x50000000)
    private val CIRCLE_CROP = CircleCropTransformation()
  }
}