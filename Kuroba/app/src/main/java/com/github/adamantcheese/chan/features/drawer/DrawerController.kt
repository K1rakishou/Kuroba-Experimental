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
package com.github.adamantcheese.chan.features.drawer

import android.content.Context
import android.content.res.ColorStateList
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.epoxy.EpoxyRecyclerView
import com.airbnb.epoxy.EpoxyTouchHelper
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.manager.GlobalWindowInsetsManager
import com.github.adamantcheese.chan.core.navigation.HasNavigation
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.features.bookmarks.BookmarksController
import com.github.adamantcheese.chan.features.drawer.data.HistoryControllerState
import com.github.adamantcheese.chan.features.drawer.data.NavigationHistoryEntry
import com.github.adamantcheese.chan.features.drawer.epoxy.EpoxyHistoryEntryView
import com.github.adamantcheese.chan.features.drawer.epoxy.EpoxyHistoryEntryViewModel_
import com.github.adamantcheese.chan.features.drawer.epoxy.epoxyHistoryEntryView
import com.github.adamantcheese.chan.features.settings.MainSettingsControllerV2
import com.github.adamantcheese.chan.ui.controller.BrowseController
import com.github.adamantcheese.chan.ui.controller.ThreadController
import com.github.adamantcheese.chan.ui.controller.ThreadSlideController
import com.github.adamantcheese.chan.ui.controller.ViewThreadController
import com.github.adamantcheese.chan.ui.controller.navigation.*
import com.github.adamantcheese.chan.ui.epoxy.epoxyErrorView
import com.github.adamantcheese.chan.ui.epoxy.epoxyLoadingView
import com.github.adamantcheese.chan.ui.epoxy.epoxyTextView
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.ui.view.HidingBottomNavigationView
import com.github.adamantcheese.chan.ui.widget.SimpleEpoxySwipeCallbacks
import com.github.adamantcheese.chan.utils.AndroidUtils.*
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.addOneshotModelBuildListener
import com.github.adamantcheese.chan.utils.plusAssign
import com.github.adamantcheese.common.updatePaddings
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import java.util.*
import javax.inject.Inject

class DrawerController(
  context: Context
) : Controller(context),
  DrawerView,
  DrawerCallbacks,
  View.OnClickListener {

  @Inject
  lateinit var themeHelper: ThemeHelper
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private lateinit var rootLayout: FrameLayout
  private lateinit var container: FrameLayout
  private lateinit var drawerLayout: DrawerLayout
  private lateinit var drawer: LinearLayout
  private lateinit var epoxyRecyclerView: EpoxyRecyclerView
  private lateinit var bottomNavView: HidingBottomNavigationView

  private val drawerPresenter = DrawerPresenter(getFlavorType() == FlavorType.Dev)
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
      val topController = top
        ?: return null

      if (topController is StyledToolbarNavigationController) {
        navigationController = topController
      } else if (topController is SplitNavigationController) {
        if (topController.getLeftController() is StyledToolbarNavigationController) {
          navigationController = topController.getLeftController() as StyledToolbarNavigationController
        }
      } else if (topController is ThreadSlideController) {
        navigationController = topController.leftController as StyledToolbarNavigationController
      }

      checkNotNull(navigationController) {
        "The child controller of a DrawerController must either be StyledToolbarNavigationController " +
          "or an DoubleNavigationController that has a ToolbarNavigationController."
      }

      return navigationController
    }

  override fun onCreate() {
    super.onCreate()
    Chan.inject(this)

    view = inflate(context, R.layout.controller_navigation_drawer)
    rootLayout = view.findViewById(R.id.root_layout)
    container = view.findViewById(R.id.container)
    drawerLayout = view.findViewById(R.id.drawer_layout)
    drawerLayout.setDrawerShadow(R.drawable.panel_shadow, GravityCompat.START)
    drawer = view.findViewById(R.id.drawer)
    epoxyRecyclerView = view.findViewById(R.id.drawer_recycler_view)

    val bottomNavBarHeight = getDimen(R.dimen.bottom_nav_view_height)
    bottomNavView = view.findViewById(R.id.bottom_navigation_view)
    bottomNavView.selectedItemId = R.id.action_browse
    bottomNavView.elevation = dp(4f).toFloat()

    bottomNavView.itemIconTintList = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
      intArrayOf(themeHelper.theme.textPrimary, themeHelper.theme.textSecondary)
    )
    bottomNavView.itemTextColor = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
      intArrayOf(themeHelper.theme.textPrimary, themeHelper.theme.textSecondary)
    )
    bottomNavView.setBackgroundColor(themeHelper.theme.backColorSecondary)

    bottomNavView.setOnNavigationItemSelectedListener { menuItem ->
      if (bottomNavView.selectedItemId == menuItem.itemId) {
        return@setOnNavigationItemSelectedListener true
      }

      onNavigationItemSelectedListener(menuItem)
      return@setOnNavigationItemSelectedListener true
    }

    EpoxyTouchHelper
      .initSwiping(epoxyRecyclerView)
      .right()
      .withTarget(EpoxyHistoryEntryViewModel_::class.java)
      .andCallbacks(object : SimpleEpoxySwipeCallbacks<EpoxyHistoryEntryViewModel_>() {
        override fun onSwipeCompleted(
          model: EpoxyHistoryEntryViewModel_,
          itemView: View?,
          position: Int,
          direction: Int
        ) {
          super.onSwipeCompleted(model, itemView, position, direction)

          drawerPresenter.onNavElementSwipedAway(model.descriptor())
        }
      })

    compositeDisposable += globalWindowInsetsManager.listenForInsetsChanges()
      .subscribe {
        epoxyRecyclerView.updatePaddings(
          top = globalWindowInsetsManager.top(),
          bottom = globalWindowInsetsManager.bottom()
        )

        bottomNavView.layoutParams.height = bottomNavBarHeight + globalWindowInsetsManager.bottom()
        bottomNavView.updatePaddings(bottom = globalWindowInsetsManager.bottom())
      }

    compositeDisposable += drawerPresenter.listenForStateChanges()
      .subscribe(
        { state -> onDrawerStateChanged(state) },
        { error ->
          Logger.e(TAG, "Unknown error subscribed to drawerPresenter.listenForStateChanges()", error)
        }
      )

    compositeDisposable += drawerPresenter.listenForBookmarksBadgeStateChanges()
      .subscribe(
        { state -> onBookmarksBadgeStateChanged(state) },
        { error ->
          Logger.e(TAG, "Unknown error subscribed to drawerPresenter.listenForBookmarksBadgeStateChanges()", error)
        }
      )

    // Must be called after drawerPresenter.listenForStateChanges() so it receives the "Loading"
    // state as well as other states
    drawerPresenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    drawerPresenter.onDestroy()
    compositeDisposable.clear()
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
        bottomNavView.setToolbar(toolbar)
      }
    }
  }

  fun openBookmarksController(threadDescriptors: List<ChanDescriptor.ThreadDescriptor>) {
    closeAllNonMainControllers()
    openControllerWrappedIntoBottomNavAwareController(BookmarksController(context, threadDescriptors))
    setBookmarksMenuItemSelected()
  }

  fun openSettingsController() {
    closeAllNonMainControllers()
    openControllerWrappedIntoBottomNavAwareController(MainSettingsControllerV2(context))
    setSettingsMenuItemSelected()
  }

  fun openControllerWrappedIntoBottomNavAwareController(controller: Controller) {
    val bottomNavBarAwareNavigationController = BottomNavBarAwareNavigationController(
      context,
      object : BottomNavBarAwareNavigationController.CloseBottomNavBarAwareNavigationControllerListener {
        override fun onCloseController() {
          closeBottomNavBarAwareNavigationControllerListener()
        }
      }
    )

    pushChildController(bottomNavBarAwareNavigationController)
    bottomNavBarAwareNavigationController.pushController(controller)
  }

  fun setSettingsMenuItemSelected() {
    bottomNavView.menu.findItem(R.id.action_settings)?.isChecked = true
  }

  fun setBookmarksMenuItemSelected() {
    bottomNavView.menu.findItem(R.id.action_bookmarks)?.isChecked = true
  }

  fun loadThread(
    descriptor: ChanDescriptor.ThreadDescriptor,
    closeAllNonMainControllers: Boolean = false
  ) {
    if (closeAllNonMainControllers) {
      closeAllNonMainControllers()
    }

    topThreadController?.showThread(descriptor)
  }

  private fun onNavigationItemSelectedListener(menuItem: MenuItem) {
    when (menuItem.itemId) {
      R.id.action_browse -> closeAllNonMainControllers()
      R.id.action_bookmarks -> {
        openBookmarksController(emptyList())
      }
      R.id.action_settings -> {
        openSettingsController()
      }
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

  private fun closeAllNonMainControllers() {
    var currentNavController = top
      ?: return
    val isPhoneMode = ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.PHONE

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

      if (isPhoneMode && (topController is BrowseController || topController is ViewThreadController)) {
        // If we pop BrowseController when in PHONE mode we will soft-lock the app
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
    bottomNavView.hide(lockTranslation, lockCollapse)
  }

  override fun showBottomNavBar(unlockTranslation: Boolean, unlockCollapse: Boolean) {
    bottomNavView.show(unlockTranslation, unlockCollapse)
  }

  override fun resetBottomNavViewState(unlockTranslation: Boolean, unlockCollapse: Boolean) {
    bottomNavView.resetState(unlockTranslation, unlockCollapse)
  }

  override fun passMotionEventIntoDrawer(event: MotionEvent): Boolean {
    return drawerLayout.onTouchEvent(event)
  }

  override fun resetBottomNavViewCheckState() {
    BackgroundUtils.ensureMainThread()

    // Hack! To reset the bottomNavView's checked item to "browse" when pressing back one either
    // of the bottomNavView's child controllers (Bookmarks or Settings)
    bottomNavView.menu.findItem(R.id.action_browse)?.isChecked = true
  }

  private fun onBookmarksBadgeStateChanged(state: DrawerPresenter.BookmarksBadgeState) {
    if (state.totalUnseenPostsCount > 0) {
      val badgeDrawable = bottomNavView.getOrCreateBadge(R.id.action_bookmarks)

      badgeDrawable.maxCharacterCount = BADGE_COUNTER_MAX_NUMBERS
      badgeDrawable.number = state.totalUnseenPostsCount

      if (state.hasUnreadReplies) {
        badgeDrawable.backgroundColor = themeHelper.theme.accentColor.color
      } else {
        badgeDrawable.backgroundColor = themeHelper.theme.backColor
      }

      badgeDrawable.badgeTextColor = themeHelper.theme.textPrimary
      return
    }

    if (bottomNavView.getBadge(R.id.action_bookmarks) != null) {
      bottomNavView.removeBadge(R.id.action_bookmarks)
    }
  }

  private fun onDrawerStateChanged(state: HistoryControllerState) {
    epoxyRecyclerView.withModels {
      addOneshotModelBuildListener {
        val llm = (epoxyRecyclerView.layoutManager as LinearLayoutManager)

        if (llm.findFirstCompletelyVisibleItemPosition() <= 1) {
          // Scroll to the top of the nav history list if the previous fully visible item's position
          // was either 0 or 1
          llm.scrollToPosition(0)
        }
      }

      when (state) {
        HistoryControllerState.Loading -> {
          epoxyLoadingView {
            id("history_loading_view")
          }
        }
        HistoryControllerState.Empty -> {
          epoxyTextView {
            id("history_is_empty_text_view")
            message(context.getString(R.string.navigation_history_is_empty))
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
            val requestData = EpoxyHistoryEntryView.ImageLoaderRequestData(navHistoryEntry.thumbnailUrl)

            epoxyHistoryEntryView {
              id("navigation_history_entry_${navHistoryEntry.hashCode()}")
              descriptor(navHistoryEntry.descriptor)
              imageLoaderRequestData(requestData)
              title(navHistoryEntry.title)
              clickListener {
                onHistoryEntryViewClicked(navHistoryEntry)
              }
            }
          }
        }
      }
    }
  }

  private fun onHistoryEntryViewClicked(navHistoryEntry: NavigationHistoryEntry) {
    topThreadController?.let { threadController ->
      val isCurrentlyVisible = drawerPresenter.isCurrentlyVisible(navHistoryEntry.descriptor)
      if (!isCurrentlyVisible) {
        when (val descriptor = navHistoryEntry.descriptor) {
          is ChanDescriptor.ThreadDescriptor -> {
            threadController.showThread(descriptor)
          }
          is ChanDescriptor.CatalogDescriptor -> {
            threadController.showBoard(descriptor.boardDescriptor)
          }
        }
      }
    }

    if (drawerLayout.isDrawerOpen(drawer)) {
      drawerLayout.closeDrawer(drawer)
    }
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

  companion object {
    private const val TAG = "DrawerController"
    private const val BADGE_COUNTER_MAX_NUMBERS = 5
  }
}