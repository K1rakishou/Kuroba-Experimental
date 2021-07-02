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
import android.graphics.Color
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.GravityCompat
import androidx.core.view.doOnPreDraw
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyModel
import com.airbnb.epoxy.EpoxyTouchHelper
import com.github.k1rakishou.BottomNavViewButton
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
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
import com.github.k1rakishou.chan.features.drawer.data.ImagesLoaderRequestData
import com.github.k1rakishou.chan.features.drawer.data.NavigationHistoryEntry
import com.github.k1rakishou.chan.features.drawer.epoxy.EpoxyHistoryGridEntryViewModel_
import com.github.k1rakishou.chan.features.drawer.epoxy.EpoxyHistoryListEntryViewModel_
import com.github.k1rakishou.chan.features.drawer.epoxy.epoxyHistoryGridEntryView
import com.github.k1rakishou.chan.features.drawer.epoxy.epoxyHistoryHeaderView
import com.github.k1rakishou.chan.features.drawer.epoxy.epoxyHistoryListEntryView
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2OptionsController
import com.github.k1rakishou.chan.features.image_saver.ResolveDuplicateImagesController
import com.github.k1rakishou.chan.features.search.GlobalSearchController
import com.github.k1rakishou.chan.features.settings.MainSettingsControllerV2
import com.github.k1rakishou.chan.features.thread_downloading.LocalArchiveController
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
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableDivider
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.ui.view.NavigationViewContract
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanel
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItem
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.widget.SimpleEpoxySwipeCallbacks
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.addOneshotModelBuildListener
import com.github.k1rakishou.chan.utils.countDigits
import com.github.k1rakishou.chan.utils.findControllerOrNull
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.isDarkColor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.persist_state.PersistableChanState
import com.google.android.material.badge.BadgeDrawable
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
  lateinit var pageRequestManager: PageRequestManager
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var chanThreadManager: ChanThreadManager
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var imageSaverV2: ImageSaverV2
  @Inject
  lateinit var globalViewStateManager: GlobalViewStateManager

  private val controller = MainEpoxyController()

  private lateinit var rootLayout: TouchBlockingFrameLayout
  private lateinit var container: FrameLayout
  private lateinit var drawerLayout: DrawerLayout
  private lateinit var drawer: LinearLayout
  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView
  private lateinit var navigationViewContract: NavigationViewContract
  private lateinit var divider: ColorizableDivider
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
    divider = view.findViewById(R.id.divider)
    navigationViewContract = view.findViewById(R.id.navigation_view) as NavigationViewContract
    bottomMenuPanel = view.findViewById(R.id.bottom_menu_panel)

    epoxyRecyclerView = view.findViewById(R.id.drawer_recycler_view)
    epoxyRecyclerView.setController(controller)

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

    EpoxyTouchHelper
      .initSwiping(epoxyRecyclerView)
      .right()
      .withTargets(EpoxyHistoryListEntryViewModel_::class.java, EpoxyHistoryGridEntryViewModel_::class.java)
      .andCallbacks(object : SimpleEpoxySwipeCallbacks<EpoxyModel<*>>() {
        override fun onSwipeCompleted(
          model: EpoxyModel<*>,
          itemView: View?,
          position: Int,
          direction: Int
        ) {
          super.onSwipeCompleted(model, itemView, position, direction)

          val descriptor = when (model) {
            is EpoxyHistoryListEntryViewModel_ -> model.descriptor()
            is EpoxyHistoryGridEntryViewModel_ -> model.descriptor()
            else -> throw IllegalArgumentException("Unknown model: ${model.javaClass.simpleName}")
          }

          drawerPresenter.onNavElementSwipedAway(descriptor)
        }
      })

    EpoxyTouchHelper
      .initSwiping(epoxyRecyclerView)
      .right()
      .withTarget(EpoxyHistoryGridEntryViewModel_::class.java)
      .andCallbacks(object : SimpleEpoxySwipeCallbacks<EpoxyHistoryGridEntryViewModel_>() {
        override fun onSwipeCompleted(
          model: EpoxyHistoryGridEntryViewModel_,
          itemView: View?,
          position: Int,
          direction: Int
        ) {
          super.onSwipeCompleted(model, itemView, position, direction)

          drawerPresenter.onNavElementSwipedAway(model.descriptor())
        }
      })

    updateRecyclerLayoutMode()

    compositeDisposable.add(
      drawerPresenter.listenForStateChanges()
        .subscribe(
          { state -> onDrawerStateChanged(state) },
          { error ->
            Logger.e(TAG, "Unknown error subscribed to drawerPresenter.listenForStateChanges()", error)
          }
        )
    )

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

    divider.setBackgroundColor(themeEngine.chanTheme.dividerColor)
    navigationViewContract.setBackgroundColor(themeEngine.chanTheme.primaryColor)

    val uncheckedColor = if (ThemeEngine.isNearToFullyBlackColor(themeEngine.chanTheme.primaryColor)) {
      Color.DKGRAY
    } else {
      ThemeEngine.manipulateColor(themeEngine.chanTheme.primaryColor, .7f)
    }

    navigationViewContract.viewItemIconTintList = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
      intArrayOf(Color.WHITE, uncheckedColor)
    )

    navigationViewContract.viewItemTextColor = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
      intArrayOf(Color.WHITE, uncheckedColor)
    )
  }

  override fun onDestroy() {
    super.onDestroy()

    epoxyRecyclerView.clear()
    themeEngine.removeListener(this)
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    drawerPresenter.onDestroy()
    compositeDisposable.clear()
    bottomNavViewGestureDetector.cleanup()
  }

  override fun onInsetsChanged() {
    val navigationViewSize = getDimen(R.dimen.navigation_view_size)

    epoxyRecyclerView.updatePaddings(
      top = globalWindowInsetsManager.top(),
      bottom = globalWindowInsetsManager.bottom()
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
      onRetryClicked = { imageSaverV2Options -> imageSaverV2.restartUncompleted(uniqueId, imageSaverV2Options) },
      onCancelClicked = { imageSaverV2.deleteDownload(uniqueId) }
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
        Color.LTGRAY
      } else {
        Color.DKGRAY
      }
    }

    badgeDrawable.backgroundColor = backgroundColor
    badgeDrawable.badgeTextColor = if (isDarkColor(backgroundColor)) {
      Color.WHITE
    } else {
      Color.BLACK
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
      Color.WHITE
    } else {
      Color.BLACK
    }
  }

  private fun BadgeDrawable.adjustBadgeDrawable() {
    when (navigationViewContract.type) {
      NavigationViewContract.Type.BottomNavView -> {
        verticalOffset = dp(4f)
        horizontalOffset = dp(2f)
        badgeGravity = BadgeDrawable.TOP_END
      }
      NavigationViewContract.Type.SideNavView -> {
        verticalOffset = 0
        horizontalOffset = number.countDigits() * dp(5f)
        badgeGravity = BadgeDrawable.TOP_START
      }
    }
  }

  private fun onDrawerStateChanged(state: HistoryControllerState) {
    val gridMode = PersistableChanState.drawerNavHistoryGridMode.get()

    controller.callback = buildFunc@ {
      addOneshotModelBuildListener {
        val llm = (epoxyRecyclerView.layoutManager as LinearLayoutManager)

        if (llm.findFirstCompletelyVisibleItemPosition() <= 1) {
          // Scroll to the top of the nav history list if the previous fully visible item's position
          // was either 0 or 1
          llm.scrollToPosition(0)
        }
      }

      if (state is HistoryControllerState.Loading) {
        epoxyLoadingView {
          id("history_loading_view")
        }

        return@buildFunc
      }

      epoxyHistoryHeaderView {
        id("navigation_history_header")
        onThemeSwitcherClicked { epoxyRecyclerView.postDelayed({ themeEngine.toggleTheme() }, 125L) }
        onDrawerSettingsClicked { showDrawerOptions() }
      }

      when (state) {
        HistoryControllerState.Empty -> {
          epoxyTextView {
            id("history_is_empty_text_view")
            message(context.getString(R.string.drawer_controller_navigation_history_is_empty))
          }
        }
        is HistoryControllerState.Error -> {
          epoxyErrorView {
            id("history_error_view")
            errorMessage(state.errorText)
          }
        }
        is HistoryControllerState.Data -> {
          state.navHistoryEntryList.forEach { navHistoryEntry ->
            val requestData = ImagesLoaderRequestData(
              navHistoryEntry.threadThumbnailUrl,
              navHistoryEntry.siteThumbnailUrl
            )

            if (gridMode) {
              epoxyHistoryGridEntryView {
                id("navigation_history_grid_entry_${navHistoryEntry.hashCode()}")
                descriptor(navHistoryEntry.descriptor)
                imageLoaderRequestData(requestData)
                title(navHistoryEntry.title)
                bindNavHistoryBookmarkAdditionalInfo(navHistoryEntry.additionalInfo)
                clickListener { onHistoryEntryViewClicked(navHistoryEntry) }
              }
            } else {
              epoxyHistoryListEntryView {
                id("navigation_history_list_entry_${navHistoryEntry.hashCode()}")
                descriptor(navHistoryEntry.descriptor)
                imageLoaderRequestData(requestData)
                title(navHistoryEntry.title)
                bindNavHistoryBookmarkAdditionalInfo(navHistoryEntry.additionalInfo)
                clickListener { onHistoryEntryViewClicked(navHistoryEntry) }
              }
            }
          }
        }
        HistoryControllerState.Loading -> throw IllegalStateException("Must be handled separately")
      }
    }

    controller.requestModelBuild()
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

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_TOGGLE_NAV_HISTORY_LAYOUT_MODE,
      name = getString(R.string.drawer_controller_history_grid_layout_mode),
      isCurrentlySelected = PersistableChanState.drawerNavHistoryGridMode.get()
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
            drawerPresenter.mapBookmarksIntoNewNavigationElements(),
            createdByBookmarkCreation = false
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
      ACTION_TOGGLE_NAV_HISTORY_LAYOUT_MODE -> {
        PersistableChanState.drawerNavHistoryGridMode.toggle()
        updateRecyclerLayoutMode()
      }
    }
  }

  private fun updateRecyclerLayoutMode() {
    val isGridMode = PersistableChanState.drawerNavHistoryGridMode.get()
    if (isGridMode) {
      if (drawer.width <= 0) {
        drawer.doOnPreDraw { updateRecyclerLayoutMode() }
        return
      }

      val spanCount = (drawer.width / GRID_COLUMN_WIDTH).coerceIn(MIN_SPAN_COUNT, MAX_SPAN_COUNT)

      epoxyRecyclerView.layoutManager = GridLayoutManager(context, spanCount).apply {
        spanSizeLookup = controller.spanSizeLookup
      }

      drawerPresenter.reloadNavigationHistory()
      return
    }

    epoxyRecyclerView.layoutManager = LinearLayoutManager(context)
    drawerPresenter.reloadNavigationHistory()
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

  private class MainEpoxyController : EpoxyController() {
    var callback: EpoxyController.() -> Unit = {}

    override fun buildModels() {
      callback(this)
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
    private const val ACTION_TOGGLE_NAV_HISTORY_LAYOUT_MODE = 4

    private val GRID_COLUMN_WIDTH = dp(80f)
  }
}