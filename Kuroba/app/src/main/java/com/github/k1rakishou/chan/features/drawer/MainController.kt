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
import android.content.res.ColorStateList
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.github.k1rakishou.BottomNavViewButton
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
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
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.core.navigation.HasNavigation
import com.github.k1rakishou.chan.features.drawer.data.HistoryControllerState
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
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
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
import com.github.k1rakishou.chan.ui.view.NavigationViewContract
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanel
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItem
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.countDigits
import com.github.k1rakishou.chan.utils.findControllerOrNull
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.isDarkColor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.persist_state.PersistableChanState
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.android.material.badge.BadgeDrawable
import dagger.Lazy
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject


class MainController(
  context: Context
) : Controller(context),
  MainControllerView,
  MainControllerCallbacks,
  View.OnClickListener,
  WindowInsetsListener,
  ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var settingsNotificationManager: SettingsNotificationManager
  @Inject
  lateinit var historyNavigationManager: HistoryNavigationManager
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var pageRequestManager: Lazy<PageRequestManager>
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var chanThreadManager: ChanThreadManager
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var imageSaverV2: Lazy<ImageSaverV2>
  @Inject
  lateinit var imageLoaderV2: Lazy<ImageLoaderV2>
  @Inject
  lateinit var globalViewStateManager: GlobalViewStateManager

  private lateinit var rootLayout: TouchBlockingFrameLayout
  private lateinit var container: TouchBlockingFrameLayoutNoBackground
  private lateinit var drawerLayout: DrawerLayout
  private lateinit var drawer: TouchBlockingLinearLayoutNoBackground
  private lateinit var navigationViewContract: NavigationViewContract
  private lateinit var bottomMenuPanel: BottomMenuPanel

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

  private val drawerPresenter by lazy {
    MainControllerPresenter(
      isDevFlavor = isDevBuild(),
      historyNavigationManager = historyNavigationManager,
      siteManager = siteManager,
      bookmarksManager = bookmarksManager,
      pageRequestManager = pageRequestManager,
      archivesManager = archivesManager,
      chanThreadManager = chanThreadManager
    )
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

    setBottomNavViewButtons()
    navigationViewContract.selectedMenuItemId = R.id.action_browse
    navigationViewContract.viewElevation = dp(4f).toFloat()
    navigationViewContract.disableTooltips()

    // Must be above bottomNavView
    bottomMenuPanel.elevation = dp(6f).toFloat()

    navigationViewContract.setOnNavigationItemSelectedListener { menuItem ->
      if (navigationViewContract.selectedMenuItemId == menuItem.itemId) {
        return@setOnNavigationItemSelectedListener true
      }

      onNavigationItemSelectedListener(menuItem)
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
        ProvideWindowInsets {
          val chanTheme = LocalChanTheme.current

          Column(modifier = Modifier
            .fillMaxSize()
            .background(chanTheme.backColorCompose)
          ) {
            BuildContent()
          }
        }
      }
    }

    compositeDisposable.add(
      drawerPresenter.listenForBookmarksBadgeStateChanges()
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

    // Must be called after drawerPresenter.listenForStateChanges() so it receives the "Loading"
    // state as well as other states
    drawerPresenter.onCreate(this)
    globalWindowInsetsManager.addInsetsUpdatesListener(this)

    themeEngine.addListener(this)
    onThemeChanged()
  }

  private fun setBottomNavViewButtons() {
    val bottomNavViewButtons = PersistableChanState.reorderableBottomNavViewButtons.get()
    navigationViewContract.navigationMenu.clear()

    bottomNavViewButtons.bottomNavViewButtons().forEachIndexed { index, bottomNavViewButton ->
      when (bottomNavViewButton) {
        BottomNavViewButton.Search -> {
          navigationViewContract.navigationMenu
            .add(Menu.NONE, R.id.action_search, index, R.string.menu_search)
            .setIcon(R.drawable.ic_search_white_24dp)
        }
        BottomNavViewButton.Archive -> {
          navigationViewContract.navigationMenu
            .add(Menu.NONE, R.id.action_archive, index, R.string.menu_archive)
            .setIcon(R.drawable.ic_baseline_archive_24)
        }
        BottomNavViewButton.Bookmarks -> {
          navigationViewContract.navigationMenu
            .add(Menu.NONE, R.id.action_bookmarks, index, R.string.menu_bookmarks)
            .setIcon(R.drawable.ic_baseline_bookmarks)
        }
        BottomNavViewButton.Browse -> {
          navigationViewContract.navigationMenu
            .add(Menu.NONE, R.id.action_browse, index, R.string.menu_browse)
            .setIcon(R.drawable.ic_baseline_laptop)
        }
        BottomNavViewButton.Settings -> {
          navigationViewContract.navigationMenu
            .add(Menu.NONE, R.id.action_settings, index, R.string.menu_settings)
            .setIcon(R.drawable.ic_baseline_settings)
        }
      }
    }
  }

  override fun onShow() {
    super.onShow()

    drawerPresenter.updateBadge()
  }

  override fun onThemeChanged() {
    drawerPresenter.onThemeChanged()
    settingsNotificationManager.onThemeChanged()

    navigationViewContract.setBackgroundColor(themeEngine.chanTheme.primaryColor)

    val uncheckedColor = if (ThemeEngine.isNearToFullyBlackColor(themeEngine.chanTheme.primaryColor)) {
      android.graphics.Color.DKGRAY
    } else {
      ThemeEngine.manipulateColor(themeEngine.chanTheme.primaryColor, .7f)
    }

    navigationViewContract.viewItemIconTintList = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
      intArrayOf(android.graphics.Color.WHITE, uncheckedColor)
    )

    navigationViewContract.viewItemTextColor = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
      intArrayOf(android.graphics.Color.WHITE, uncheckedColor)
    )
  }

  override fun onDestroy() {
    super.onDestroy()

    themeEngine.removeListener(this)
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    drawerPresenter.onDestroy()
    compositeDisposable.clear()
    bottomNavViewGestureDetector.cleanup()
  }

  override fun onInsetsChanged() {
    val navigationViewSize = getDimen(R.dimen.navigation_view_size)

    drawerPresenter.paddings = PaddingValues(
      top = globalWindowInsetsManager.topDp(),
      bottom = globalWindowInsetsManager.bottomDp()
    )

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

  private fun onNavigationItemSelectedListener(menuItem: MenuItem) {
    when (menuItem.itemId) {
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

      // Closing any "floating" controllers like ImageViewController
      closeAllFloatingControllers(topController.childControllers)

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

  private fun closeAllFloatingControllers(childControllers: List<Controller>) {
    for (childController in childControllers) {
      childController.presentingThisController?.stopPresenting(false)

      if (childController.childControllers.isNotEmpty()) {
        closeAllFloatingControllers(childController.childControllers)
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

    return if (drawerLayout.isDrawerOpen(drawer)) {
      drawerLayout.closeDrawer(drawer)
      true
    } else {
      super.onBack()
    }
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
    navigationViewContract.updateMenuItem(R.id.action_browse) { isChecked = true }
  }

  fun setArchiveMenuItemSelected() {
    navigationViewContract.updateMenuItem(R.id.action_archive) { isChecked = true }
  }

  fun setSettingsMenuItemSelected() {
    navigationViewContract.updateMenuItem(R.id.action_settings) { isChecked = true }
  }

  fun setBookmarksMenuItemSelected() {
    navigationViewContract.updateMenuItem(R.id.action_bookmarks) { isChecked = true }
  }

  fun setGlobalSearchMenuItemSelected() {
    navigationViewContract.updateMenuItem(R.id.action_search) { isChecked = true }
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
      onRetryClicked = { imageSaverV2Options -> imageSaverV2.get().restartUncompleted(uniqueId, imageSaverV2Options) },
      onCancelClicked = { imageSaverV2.get().deleteDownload(uniqueId) }
    )

    val imageSaverV2OptionsController = ImageSaverV2OptionsController(context, options)
    presentController(imageSaverV2OptionsController)
  }

  private fun onBookmarksBadgeStateChanged(state: MainControllerPresenter.BookmarksBadgeState) {
    if (state.totalUnseenPostsCount <= 0) {
      if (navigationViewContract.getBadge(R.id.action_bookmarks) != null) {
        navigationViewContract.removeBadge(R.id.action_bookmarks)
      }

      return
    }

    val badgeDrawable = navigationViewContract.getOrCreateBadge(R.id.action_bookmarks)
    badgeDrawable.maxCharacterCount = BOOKMARKS_BADGE_COUNTER_MAX_NUMBERS
    badgeDrawable.number = state.totalUnseenPostsCount
    badgeDrawable.adjustBadgeDrawable()

    val backgroundColor = if (state.hasUnreadReplies) {
      themeEngine.chanTheme.accentColor
    } else {
      if (isDarkColor(themeEngine.chanTheme.primaryColor)) {
        android.graphics.Color.LTGRAY
      } else {
        android.graphics.Color.DKGRAY
      }
    }

    badgeDrawable.backgroundColor = backgroundColor
    badgeDrawable.badgeTextColor = if (isDarkColor(backgroundColor)) {
      android.graphics.Color.WHITE
    } else {
      android.graphics.Color.BLACK
    }
  }

  private fun onSettingsNotificationChanged() {
    val notificationsCount = settingsNotificationManager.count()

    if (notificationsCount <= 0) {
      if (navigationViewContract.getBadge(R.id.action_settings) != null) {
        navigationViewContract.removeBadge(R.id.action_settings)
      }

      return
    }

    val badgeDrawable = navigationViewContract.getOrCreateBadge(R.id.action_settings)
    badgeDrawable.maxCharacterCount = SETTINGS_BADGE_COUNTER_MAX_NUMBERS
    badgeDrawable.number = notificationsCount
    badgeDrawable.adjustBadgeDrawable()

    badgeDrawable.backgroundColor = themeEngine.chanTheme.accentColor
    badgeDrawable.badgeTextColor = if (isDarkColor(themeEngine.chanTheme.accentColor)) {
      android.graphics.Color.WHITE
    } else {
      android.graphics.Color.BLACK
    }
  }

  private fun BadgeDrawable.adjustBadgeDrawable() {
    when (navigationViewContract.type) {
      NavigationViewContract.Type.BottomNavView -> {
        verticalOffset = dp(4f)
        horizontalOffset = dp(5f)
        badgeGravity = BadgeDrawable.TOP_END
      }
      NavigationViewContract.Type.SideNavView -> {
        verticalOffset = 0
        horizontalOffset = number.countDigits().coerceAtMost(5) * dp(5f)
        badgeGravity = BadgeDrawable.TOP_START
      }
    }
  }

  @Composable
  private fun ColumnScope.BuildContent() {
    val historyControllerState by drawerPresenter.historyControllerStateFlow.collectAsState()

    Spacer(modifier = Modifier.height(drawerPresenter.paddings.calculateTopPadding()))
    BuildNavigationHistoryListHeader()

    val navHistoryEntryList = when (historyControllerState) {
      HistoryControllerState.Empty -> {
        KurobaComposeText(
          modifier = Modifier.fillMaxSize(),
          text = stringResource(id = R.string.drawer_controller_navigation_history_is_empty)
        )
        return
      }
      HistoryControllerState.Loading -> {
        KurobaComposeProgressIndicator()
        return
      }
      is HistoryControllerState.Error -> {
        KurobaComposeErrorMessage(errorMessage = (historyControllerState as HistoryControllerState.Error).errorText)
        return
      }
      is HistoryControllerState.Data -> {
        (historyControllerState as HistoryControllerState.Data).navHistoryEntryList
      }
    }

    BuildNavigationHistoryList(navHistoryEntryList)
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun BuildNavigationHistoryList(navHistoryEntryList: List<NavigationHistoryEntry>) {
    val state = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
      BoxWithConstraints(modifier = Modifier
        .fillMaxWidth()
        .weight(1f)) {
        val spanCount = with(LocalDensity.current) {
          (maxWidth.toPx() / GRID_COLUMN_WIDTH).toInt().coerceIn(MIN_SPAN_COUNT, MAX_SPAN_COUNT)
        }

        val bottomPadding = remember(key1 = drawerPresenter.paddings) {
          PaddingValues(bottom = drawerPresenter.paddings.calculateBottomPadding())
        }

        val chanTheme = LocalChanTheme.current

        LazyVerticalGrid(
          state = state,
          modifier = Modifier
            .fillMaxSize()
            .simpleVerticalScrollbar(state, chanTheme),
          contentPadding = bottomPadding,
          cells = GridCells.Fixed(count = spanCount),
          content = {
            items(count = navHistoryEntryList.size) { index ->
              val navHistoryEntry = navHistoryEntryList.get(index)
              BuildNavigationHistoryListEntry(navHistoryEntry)
            }
          })
      }
    }
  }

  @Composable
  private fun BuildNavigationHistoryListHeader() {
    Row(modifier = Modifier
      .fillMaxWidth()
      .height(32.dp)
      .padding(2.dp),
      horizontalArrangement = Arrangement.End
    ) {
      KurobaComposeIcon(
        drawableId = R.drawable.ic_baseline_wb_sunny_24,
        themeEngine = themeEngine,
        modifier = Modifier.kurobaClickable(onClick = { rootLayout.postDelayed({ themeEngine.toggleTheme() }, 125L) })
      )

      Spacer(modifier = Modifier.width(16.dp))

      KurobaComposeIcon(
        drawableId = R.drawable.ic_more_vert_white_24dp,
        themeEngine = themeEngine,
        modifier = Modifier.kurobaClickable(onClick = { showDrawerOptions() })
      )

      Spacer(modifier = Modifier.width(16.dp))
    }
  }

  @Composable
  private fun BuildNavigationHistoryListEntry(navHistoryEntry: NavigationHistoryEntry) {
    val chanDescriptor = navHistoryEntry.descriptor

    val siteIconRequest = remember(key1 = chanDescriptor) {
      if (navHistoryEntry.siteThumbnailUrl != null) {
        ImageLoaderRequest(ImageLoaderRequestData.Url(navHistoryEntry.siteThumbnailUrl))
      } else {
        null
      }
    }

    val thumbnailRequest = remember(key1 = chanDescriptor) {
      ImageLoaderRequest(ImageLoaderRequestData.Url(navHistoryEntry.threadThumbnailUrl))
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(all = 2.dp)
        .kurobaClickable(
          onClick = { onHistoryEntryViewClicked(navHistoryEntry) },
          onLongClick = { onHistoryEntryViewLongClicked(navHistoryEntry) }
        ),
    ) {
      Box {
        val contentScale = if (navHistoryEntry.descriptor is ChanDescriptor.CatalogDescriptor) {
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
          imageLoaderV2 = imageLoaderV2.get()
        )

        val circleColor = remember {
          Color(0x80000000L)
        }

        Image(
          modifier = Modifier
            .align(Alignment.TopStart)
            .size(20.dp)
            .kurobaClickable(onClick = { onNavHistoryDeleteClicked(navHistoryEntry.descriptor) })
            .background(color = circleColor, shape = CircleShape),
          painter = painterResource(id = R.drawable.ic_clear_white_24dp),
          contentDescription = null
        )

        Row(modifier = Modifier
          .wrapContentHeight()
          .wrapContentWidth()
          .align(Alignment.TopEnd)
        ) {

          if (navHistoryEntry.pinned) {
            Image(
              modifier = Modifier
                .size(20.dp),
              painter = painterResource(id = R.drawable.sticky_icon),
              contentDescription = null
            )
          }

          if (siteIconRequest != null) {
            KurobaComposeImage(
              request = siteIconRequest,
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .size(20.dp),
              imageLoaderV2 = imageLoaderV2.get()
            )
          }

        }

      }

      if (navHistoryEntry.additionalInfo != null) {
        val additionalInfo = navHistoryEntry.additionalInfo
        val additionalInfoString = remember(key1 = additionalInfo) {
          additionalInfo.toAnnotatedString(themeEngine)
        }

        KurobaComposeText(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
          maxLines = 1,
          textAlign = TextAlign.Center,
          overflow = TextOverflow.Ellipsis,
          fontSize = 14.sp,
          text = additionalInfoString
        )
      }

      KurobaComposeText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        text = navHistoryEntry.title,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        fontSize = 12.sp,
        textAlign = TextAlign.Center
      )
    }
  }

  private fun showDrawerOptions() {
    val drawerOptions = mutableListOf<FloatingListMenuItem>()

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

    drawerOptions += FloatingListMenuItem(
      key = ACTION_CLEAR_NAV_HISTORY,
      name = getString(R.string.drawer_controller_clear_nav_history)
    )

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = drawerOptions,
      itemClickListener = { item -> onDrawerOptionClicked(item) }
    )

    presentController(floatingListMenuController)
  }

  private fun onDrawerOptionClicked(item: FloatingListMenuItem) {
    when (item.key) {
      ACTION_MOVE_LAST_ACCESSED_THREAD_TO_TOP -> {
        ChanSettings.drawerMoveLastAccessedThreadToTop.toggle()
      }
      ACTION_SHOW_BOOKMARKS -> {
        ChanSettings.drawerShowBookmarkedThreads.toggle()

        if (ChanSettings.drawerShowBookmarkedThreads.get()) {
          historyNavigationManager.createNewNavElements(
            newNavigationElements = drawerPresenter.mapBookmarksIntoNewNavigationElements(),
            canInsertAtTheBeginning = true
          )
        } else {
          val bookmarkDescriptors = bookmarksManager
            .mapAllBookmarks { threadBookmarkView -> threadBookmarkView.threadDescriptor }

          historyNavigationManager.removeNavElements(bookmarkDescriptors)
        }

        drawerPresenter.reloadNavigationHistory()
      }
      ACTION_SHOW_NAV_HISTORY -> {
        ChanSettings.drawerShowNavigationHistory.toggle()
        drawerPresenter.reloadNavigationHistory()
      }
      ACTION_CLEAR_NAV_HISTORY -> {
        dialogFactory.createSimpleConfirmationDialog(
          context = context,
          titleTextId = R.string.drawer_controller_clear_nav_history_dialog_title,
          negativeButtonText = getString(R.string.do_not),
          positiveButtonText = getString(R.string.clear),
          onPositiveButtonClickListener = { historyNavigationManager.clear() }
        )
      }
    }
  }

  private fun onHistoryEntryViewLongClicked(navHistoryEntry: NavigationHistoryEntry) {
    val chanDescriptorString = navHistoryEntry.descriptor.userReadableString()

    when (drawerPresenter.pinOrUnpin(navHistoryEntry.descriptor)) {
      HistoryNavigationManager.PinResult.Pinned -> {
        showToast(getString(R.string.drawer_controller_navigation_entry_pinned, chanDescriptorString))
      }
      HistoryNavigationManager.PinResult.Unpinned -> {
        showToast(getString(R.string.drawer_controller_navigation_entry_unpinned, chanDescriptorString))
      }
      HistoryNavigationManager.PinResult.Failure -> {
        showToast(getString(R.string.drawer_controller_navigation_entry_failed_to_pin_unpin, chanDescriptorString))
      }
      HistoryNavigationManager.PinResult.NoSpaceToPin -> {
        showToast(getString(R.string.drawer_controller_navigation_entry_no_slots_for_pin, chanDescriptorString))
      }
    }
  }

  private fun onNavHistoryDeleteClicked(descriptor: ChanDescriptor) {
    drawerPresenter.deleteNavElement(descriptor)
    showToast(getString(R.string.drawer_controller_navigation_entry_deleted, descriptor.userReadableString()))
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
        is ChanDescriptor.CatalogDescriptor -> {
          currentTopThreadController.showBoard(descriptor.boardDescriptor, true)
        }
      }

      if (drawerLayout.isDrawerOpen(drawer)) {
        drawerLayout.closeDrawer(drawer)
      }
    }
  }

  companion object {
    private const val TAG = "MainController"
    private const val BOOKMARKS_BADGE_COUNTER_MAX_NUMBERS = 5
    private const val SETTINGS_BADGE_COUNTER_MAX_NUMBERS = 2

    private const val MIN_SPAN_COUNT = 3
    private const val MAX_SPAN_COUNT = 6

    private const val ACTION_MOVE_LAST_ACCESSED_THREAD_TO_TOP = 0
    private const val ACTION_SHOW_BOOKMARKS = 1
    private const val ACTION_SHOW_NAV_HISTORY = 2
    private const val ACTION_CLEAR_NAV_HISTORY = 3

    private val GRID_COLUMN_WIDTH = dp(80f)
  }
}