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
package com.github.adamantcheese.chan.ui.controller

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.text.InputType
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.ui.NavigationControllerContainerLayout
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.manager.BoardManager
import com.github.adamantcheese.chan.core.manager.HistoryNavigationManager
import com.github.adamantcheese.chan.core.manager.SiteManager
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.presenter.BrowsePresenter
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode
import com.github.adamantcheese.chan.features.drawer.DrawerCallbacks
import com.github.adamantcheese.chan.features.setup.BoardSelectionController
import com.github.adamantcheese.chan.features.setup.SiteSettingsController
import com.github.adamantcheese.chan.features.setup.SitesSetupController
import com.github.adamantcheese.chan.ui.adapter.PostsFilter
import com.github.adamantcheese.chan.ui.controller.ThreadSlideController.ReplyAutoCloseListener
import com.github.adamantcheese.chan.ui.controller.ThreadSlideController.SlideChangeListener
import com.github.adamantcheese.chan.ui.controller.navigation.SplitNavigationController
import com.github.adamantcheese.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.adamantcheese.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.adamantcheese.chan.ui.helper.HintPopup
import com.github.adamantcheese.chan.ui.layout.ThreadLayout.ThreadLayoutCallback
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuSubItem
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.DialogUtils.createSimpleDialogWithInput
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

class BrowseController(context: Context) : ThreadController(context),
  ThreadLayoutCallback,
  BrowsePresenter.Callback,
  SlideChangeListener,
  ReplyAutoCloseListener {

  @Inject
  lateinit var presenter: BrowsePresenter
  @Inject
  lateinit var themeHelper: ThemeHelper
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var databaseManager: DatabaseManager
  @Inject
  lateinit var historyNavigationManager: HistoryNavigationManager

  private var order: PostsFilter.Order = PostsFilter.Order.BUMP
  private var hint: HintPopup? = null
  private var initialized = false
  private var menuBuilt = false

  @JvmField
  var searchQuery: String? = null

  override fun onCreate() {
    super.onCreate()
    Chan.inject(this)

    val navControllerContainerLayout = AndroidUtils.inflate(context, R.layout.controller_browse)
    val container = navControllerContainerLayout.findViewById<View>(R.id.container) as NavigationControllerContainerLayout
    container.initBrowseControllerTracker(this, navigationController)
    container.addView(view)
    view = container

    // Initialization
    order = PostsFilter.Order.find(ChanSettings.boardOrder.get())
    threadLayout.setPostViewMode(ChanSettings.boardViewMode.get())
    threadLayout.presenter.setOrder(order)

    // Navigation
    initNavigation()
  }

  override fun onShow() {
    super.onShow()

    if (drawerCallbacks != null) {
      drawerCallbacks!!.resetBottomNavViewCheckState()
      if (ChanSettings.getCurrentLayoutMode() != ChanSettings.LayoutMode.SPLIT) {
        drawerCallbacks!!.showBottomNavBar(unlockTranslation = false, unlockCollapse = false)
      }
    }

    if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.PHONE) {
      if (chanDescriptor != null) {
        historyNavigationManager.moveNavElementToTop(chanDescriptor!!)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    if (hint != null) {
      hint!!.dismiss()
      hint = null
    }

    drawerCallbacks = null
    presenter.destroy()
  }

  override fun showSitesNotSetup() {
    super.showSitesNotSetup()

    if (hint != null) {
      hint!!.dismiss()
      hint = null
    }

    val hintView: View = requireToolbar().findViewById(R.id.title_container)
    hint = HintPopup.show(context, hintView, R.string.thread_empty_setup_hint).apply {
      alignCenter()
      wiggle()
    }

    // this controller is used for catalog views; displaying things on two rows for them middle
    // menu is how we want it done these need to be setup before the view is rendered,
    // otherwise the subtitle view is removed
    navigation.title = "App Setup"
    navigation.subtitle = "Tap for site/board setup"
    buildMenu()

    initialized = true
  }

  public override fun setDrawerCallbacks(drawerCallbacks: DrawerCallbacks?) {
    super.setDrawerCallbacks(drawerCallbacks)
  }

  suspend fun setBoard(descriptor: BoardDescriptor) {
    presenter.setBoard(descriptor)
    initialized = true
  }

  suspend fun loadWithDefaultBoard() {
    presenter.loadWithDefaultBoard(false)
    initialized = true
  }

  private fun initNavigation() {
    // Navigation item
    navigation.hasDrawer = true
    navigation.setMiddleMenu {
      if (!initialized) {
        return@setMiddleMenu
      }

      if (!siteManager.areSitesSetup()) {
        openSitesSetupController()
      } else {
        openBoardSelectionController()
      }
    }

    // Toolbar menu
    navigation.hasBack = false

    // this controller is used for catalog views; displaying things on two rows for them middle
    // menu is how we want it done these need to be setup before the view is rendered,
    // otherwise the subtitle view is removed
    navigation.title = "Loading..."
    requireNavController().requireToolbar().updateTitle(navigation)

    // Presenter
    presenter.create(mainScope, this)
  }

  private fun openBoardSelectionController() {
    val boardSelectionController = BoardSelectionController(
      context,
      object : BoardSelectionController.UserSelectionListener {
        override fun onSiteSelected(siteDescriptor: SiteDescriptor) {
          openSiteSettingsController(siteDescriptor)
        }

        override fun onBoardSelected(boardDescriptor: BoardDescriptor) {
          if (boardManager.currentBoardDescriptor() == boardDescriptor) {
            return
          }

          mainScope.launch { loadBoard(boardDescriptor) }
        }
      })

    navigationController!!.presentController(boardSelectionController)

    requireStartActivity().setSettingsMenuItemSelected()
  }

  private fun openSitesSetupController() {
    val sitesSetupController = SitesSetupController(context)
    if (doubleNavigationController != null) {
      doubleNavigationController!!.openControllerWrappedIntoBottomNavAwareController(sitesSetupController)
    } else {
      requireStartActivity().openControllerWrappedIntoBottomNavAwareController(sitesSetupController)
    }

    requireStartActivity().setSettingsMenuItemSelected()
  }

  private fun openSiteSettingsController(siteDescriptor: SiteDescriptor) {
    val siteSettingsController = SiteSettingsController(context, siteDescriptor)
    if (doubleNavigationController != null) {
      doubleNavigationController!!.openControllerWrappedIntoBottomNavAwareController(siteSettingsController)
    } else {
      requireStartActivity().openControllerWrappedIntoBottomNavAwareController(siteSettingsController)
    }

    requireStartActivity().setSettingsMenuItemSelected()
  }

  @Suppress("MoveLambdaOutsideParentheses")
  private fun buildMenu() {
    val menuBuilder = navigation.buildMenu()
      .withItem(R.drawable.ic_search_white_24dp) { item -> searchClicked(item) }
      .withItem(R.drawable.ic_refresh_white_24dp) { item -> reloadClicked(item) }

    val overflowBuilder = menuBuilder.withOverflow(navigationController)
    if (!ChanSettings.enableReplyFab.get()) {
      overflowBuilder.withSubItem(ACTION_REPLY, R.string.action_reply) { item -> replyClicked(item) }
    }

    val modeStringId = if (ChanSettings.boardViewMode.get() == PostViewMode.LIST) {
      R.string.action_switch_catalog
    } else {
      R.string.action_switch_board
    }

    overflowBuilder
      .withSubItem(ACTION_CHANGE_VIEW_MODE, modeStringId) { item -> viewModeClicked(item) }
      .addSortMenu()
      .addDevMenu()
      .withSubItem(ACTION_OPEN_BROWSER, R.string.action_open_browser, { item -> openBrowserClicked(item) })
      .withSubItem(ACTION_OPEN_THREAD_BY_ID, R.string.action_open_thread_by_id, { item -> openThreadById(item) })
      .withSubItem(ACTION_SHARE, R.string.action_share, { item -> shareClicked(item) })
      .withSubItem(ACTION_SCROLL_TO_TOP, R.string.action_scroll_to_top, { item -> upClicked(item) })
      .withSubItem(ACTION_SCROLL_TO_BOTTOM, R.string.action_scroll_to_bottom, { item -> downClicked(item) })
      .build()
      .build()

    requireNavController().requireToolbar().setNavigationItem(
      true,
      true,
      navigation,
      themeHelper.theme
    )
  }

  @Suppress("MoveLambdaOutsideParentheses")
  private fun NavigationItem.MenuOverflowBuilder.addDevMenu(): NavigationItem.MenuOverflowBuilder {
    withNestedOverflow(
      ACTION_DEV_MENU,
      R.string.action_browse_dev_menu,
      AndroidUtils.getFlavorType() == AndroidUtils.FlavorType.Dev
    )
      .addNestedItem(
        DEV_BOOKMARK_EVERY_THREAD,
        R.string.dev_bookmark_every_thread,
        true,
        false,
        DEV_BOOKMARK_EVERY_THREAD,
        { subItem -> onBookmarkEveryThreadClicked(subItem) }
      )
      .build()

    return this
  }

  @Suppress("MoveLambdaOutsideParentheses")
  private fun NavigationItem.MenuOverflowBuilder.addSortMenu(): NavigationItem.MenuOverflowBuilder {
    var currentOrder = PostsFilter.Order.find(ChanSettings.boardOrder.get())
    if (currentOrder == null) {
      currentOrder = PostsFilter.Order.BUMP
    }

    withNestedOverflow(ACTION_SORT, R.string.action_sort, true)
      .addNestedItem(
        SORT_MODE_BUMP,
        R.string.order_bump,
        true,
        currentOrder == PostsFilter.Order.BUMP,
        PostsFilter.Order.BUMP,
        { subItem -> onSortItemClicked(subItem) }
      )
      .addNestedItem(
        SORT_MODE_REPLY,
        R.string.order_reply,
        true,
        currentOrder == PostsFilter.Order.REPLY,
        PostsFilter.Order.REPLY,
        { subItem -> onSortItemClicked(subItem) }
      )
      .addNestedItem(
        SORT_MODE_IMAGE,
        R.string.order_image,
        true,
        currentOrder == PostsFilter.Order.IMAGE,
        PostsFilter.Order.IMAGE,
        { subItem -> onSortItemClicked(subItem) }
      )
      .addNestedItem(
        SORT_MODE_NEWEST,
        R.string.order_newest,
        true,
        currentOrder == PostsFilter.Order.IMAGE,
        PostsFilter.Order.NEWEST,
        { subItem -> onSortItemClicked(subItem) }
      )
      .addNestedItem(
        SORT_MODE_OLDEST,
        R.string.order_oldest,
        true,
        currentOrder == PostsFilter.Order.OLDEST,
        PostsFilter.Order.OLDEST,
        { subItem -> onSortItemClicked(subItem) }
      )
      .addNestedItem(
        SORT_MODE_MODIFIED,
        R.string.order_modified,
        true,
        currentOrder == PostsFilter.Order.MODIFIED,
        PostsFilter.Order.MODIFIED,
        { subItem -> onSortItemClicked(subItem) }
      )
      .addNestedItem(
        SORT_MODE_ACTIVITY,
        R.string.order_activity,
        true,
        currentOrder == PostsFilter.Order.ACTIVITY,
        PostsFilter.Order.ACTIVITY,
        { subItem -> onSortItemClicked(subItem) }
      )
      .build()

    return this
  }

  private fun onBookmarkEveryThreadClicked(subItem: ToolbarMenuSubItem) {
    val id = subItem.value as? Int
      ?: return

    when (id) {
      DEV_BOOKMARK_EVERY_THREAD -> {
        presenter.bookmarkEveryThread(threadLayout.presenter.chanThread)
      }
    }
  }

  private fun onSortItemClicked(subItem: ToolbarMenuSubItem) {
    val order = subItem.value as? PostsFilter.Order
      ?: return

    ChanSettings.boardOrder.set(order.orderName)
    this@BrowseController.order = order

    val sortSubItem = navigation.findSubItem(ACTION_SORT)
    resetSelectedSortOrderItem(sortSubItem)
    subItem.isCurrentlySelected = true

    val presenter = threadLayout.presenter
    presenter.setOrder(order)
  }

  private fun resetSelectedSortOrderItem(item: ToolbarMenuSubItem) {
    item.isCurrentlySelected = false

    for (nestedItem in item.moreItems) {
      resetSelectedSortOrderItem(nestedItem)
    }
  }

  private fun searchClicked(item: ToolbarMenuItem) {
    val presenter = threadLayout.presenter
    if (!presenter.isBound) {
      return
    }

    val refreshView: View = item.view
    refreshView.scaleX = 1f
    refreshView.scaleY = 1f
    refreshView.animate()
      .scaleX(10f)
      .scaleY(10f)
      .setDuration(500)
      .setInterpolator(AccelerateInterpolator(2f))
      .setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
          refreshView.scaleX = 1f
          refreshView.scaleY = 1f
        }
      })

    (navigationController as ToolbarNavigationController).showSearch()
  }

  private fun reloadClicked(item: ToolbarMenuItem) {
    val presenter = threadLayout.presenter
    if (!presenter.isBound) {
      return
    }

    presenter.requestData()

    // Give the rotation menu item view a spin.
    val refreshView: View = item.view
    // Disable the ripple effect until the animation ends, but turn it back on so tap/hold ripple works
    refreshView.setBackgroundResource(0)

    val animation: Animation = RotateAnimation(
      0f,
      360f,
      RotateAnimation.RELATIVE_TO_SELF,
      0.5f,
      RotateAnimation.RELATIVE_TO_SELF,
      0.5f
    )

    animation.duration = 500L
    animation.setAnimationListener(object : Animation.AnimationListener {
      override fun onAnimationStart(animation: Animation) {}
      override fun onAnimationEnd(animation: Animation) {
        refreshView.setBackgroundResource(R.drawable.item_background)
      }

      override fun onAnimationRepeat(animation: Animation) {}
    })

    refreshView.startAnimation(animation)
  }

  private fun replyClicked(item: ToolbarMenuSubItem) {
    threadLayout.openReply(true)
  }

  private fun viewModeClicked(item: ToolbarMenuSubItem) {
    handleViewMode(item)
  }

  private fun openBrowserClicked(item: ToolbarMenuSubItem) {
    handleShareAndOpenInBrowser(false)
  }

  private fun openThreadById(item: ToolbarMenuSubItem) {
    if (chanDescriptor == null) {
      return
    }

    createSimpleDialogWithInput(
      context,
      R.string.browse_controller_enter_thread_id,
      R.string.browse_controller_enter_thread_id_msg,
      { input: String ->
        try {
          val threadDescriptor = ThreadDescriptor.create(
            chanDescriptor!!.siteName(),
            chanDescriptor!!.boardCode(),
            input.toLong()
          )

          openThread(threadDescriptor)
        } catch (e: NumberFormatException) {
          AndroidUtils.showToast(
            context,
            context.getString(R.string.browse_controller_error_parsing_thread_id)
          )
        }
      },
      InputType.TYPE_CLASS_NUMBER
    ).show()
  }

  private fun shareClicked(item: ToolbarMenuSubItem) {
    handleShareAndOpenInBrowser(true)
  }

  private fun upClicked(item: ToolbarMenuSubItem) {
    threadLayout.presenter.scrollTo(0, false)
  }

  private fun downClicked(item: ToolbarMenuSubItem) {
    threadLayout.presenter.scrollTo(-1, false)
  }

  override fun onReplyViewShouldClose() {
    threadLayout.openReply(false)
  }

  // TODO(KurobaEx):
  fun onSiteClicked(siteDescriptor: SiteDescriptor) {
    presenter.onBoardsFloatingMenuSiteClicked(siteDescriptor)
  }

  // TODO(KurobaEx):
  fun openSetup() {
    Objects.requireNonNull(navigationController, "navigationController is null")

    openBoardSelectionController()
  }

  private fun handleShareAndOpenInBrowser(share: Boolean) {
    val presenter = threadLayout.presenter
    if (!presenter.isBound) {
      return
    }

    if (presenter.chanThread == null) {
      Logger.e(TAG, "handleShareAndOpenInBrowser() chanThread == null")
      AndroidUtils.showToast(context, R.string.cannot_open_in_browser_already_deleted)
      return
    }

    val chanDescriptor = presenter.chanDescriptor
    if (chanDescriptor == null) {
      Logger.e(TAG, "handleShareAndOpenInBrowser() chanDescriptor == null")
      AndroidUtils.showToast(context, R.string.cannot_open_in_browser_already_deleted)
      return
    }

    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (site == null) {
      Logger.e(TAG, "handleShareAndOpenInBrowser() site == null " +
        "(siteDescriptor = ${chanDescriptor.siteDescriptor()})")
      AndroidUtils.showToast(context, R.string.cannot_open_in_browser_already_deleted)
      return
    }

    val link = site.resolvable().desktopUrl(chanDescriptor, null)
    if (share) {
      AndroidUtils.shareLink(link)
    } else {
      AndroidUtils.openLinkInBrowser(context, link, themeHelper.theme)
    }
  }

  private fun handleViewMode(item: ToolbarMenuSubItem) {
    var postViewMode = ChanSettings.boardViewMode.get()

    postViewMode = if (postViewMode == PostViewMode.LIST) {
      PostViewMode.CARD
    } else {
      PostViewMode.LIST
    }

    ChanSettings.boardViewMode.set(postViewMode)

    val viewModeText = if (postViewMode == PostViewMode.LIST) {
      R.string.action_switch_catalog
    } else {
      R.string.action_switch_board
    }

    item.text = AndroidUtils.getString(viewModeText)
    threadLayout.setPostViewMode(postViewMode)
  }

  override suspend fun loadBoard(boardDescriptor: BoardDescriptor) {
    boardManager.awaitUntilInitialized()

    val board = boardManager.byBoardDescriptor(boardDescriptor)
      ?: return

    navigation.title = "/" + boardDescriptor.boardCode + "/"
    navigation.subtitle = board.name

    if (!menuBuilt) {
      menuBuilt = true
      buildMenu()
    }

    val presenter = threadLayout.presenter
    presenter.bindChanDescriptor(CatalogDescriptor.create(boardDescriptor.siteName(), boardDescriptor.boardCode))
    presenter.requestData()

    requireNavController().requireToolbar().updateTitle(navigation)
  }

  override fun loadSiteSetup(siteDescriptor: SiteDescriptor) {
    val siteSetupController = SiteSettingsController(context, siteDescriptor)
    if (doubleNavigationController != null) {
      doubleNavigationController!!.openControllerWrappedIntoBottomNavAwareController(siteSetupController)
    } else {
      requireStartActivity().openControllerWrappedIntoBottomNavAwareController(siteSetupController)
    }

    requireStartActivity().setSettingsMenuItemSelected()
  }

  override fun openThread(threadToOpenDescriptor: ThreadDescriptor) {
    showThread(threadToOpenDescriptor)
  }

  override fun showThread(descriptor: ThreadDescriptor) {
    showThread(descriptor, true)
  }

  override suspend fun showBoard(descriptor: BoardDescriptor) {
    showBoardInternal(descriptor)
    initialized = true
  }

  override suspend fun showBoardAndSearch(descriptor: BoardDescriptor, searchQuery: String?) {
    // we don't actually need to do anything here because you can't tap board links in the browse
    // controller set the board just in case?
    setBoard(descriptor)
  }

  // Creates or updates the target ThreadViewController
  // This controller can be in various places depending on the layout
  // We dynamically search for it
  fun showThread(threadDescriptor: ThreadDescriptor, animated: Boolean) {
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
        if (splitNav.getRightController() is StyledToolbarNavigationController) {
          val navigationController = splitNav.getRightController() as StyledToolbarNavigationController
          if (navigationController.top is ViewThreadController) {
            val viewThreadController = navigationController.top as ViewThreadController?
            viewThreadController!!.setDrawerCallbacks(drawerCallbacks)

            viewThreadController.loadThread(threadDescriptor)
          }
        } else {
          val navigationController = StyledToolbarNavigationController(context)
          splitNav.setRightController(navigationController)
          val viewThreadController = ViewThreadController(context, threadDescriptor)
          navigationController.pushController(viewThreadController, false)
          viewThreadController.setDrawerCallbacks(drawerCallbacks)
        }
        splitNav.switchToController(false)
      }
      slideNav != null -> {
        // Create a threadview in the right part of the slide nav *without* a toolbar
        if (slideNav.getRightController() is ViewThreadController) {
          (slideNav.getRightController() as ViewThreadController).loadThread(threadDescriptor)
        } else {
          val viewThreadController = ViewThreadController(
            context,
            threadDescriptor
          )

          slideNav.setRightController(viewThreadController)
          viewThreadController.setDrawerCallbacks(drawerCallbacks)
        }
        slideNav.switchToController(false)
      }
      else -> {
        // the target ThreadNav must be pushed to the parent nav controller
        // (BrowseController -> ToolbarNavigationController)
        val viewThreadController = ViewThreadController(
          context,
          threadDescriptor
        )

        Objects.requireNonNull(navigationController, "navigationController is null")
        navigationController!!.pushController(viewThreadController, animated)

        viewThreadController.setDrawerCallbacks(drawerCallbacks)
      }
    }

    historyNavigationManager.moveNavElementToTop(threadDescriptor)
    initialized = true
  }

  private suspend fun showBoardInternal(boardDescriptor: BoardDescriptor) {
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

    // Do nothing when split navigation is enabled because both controllers are always visible
    // so we don't need to switch between left and right controllers
    if (splitNav == null) {
      if (slideNav != null) {
        slideNav.switchToController(true)
      } else {
        if (navigationController != null) {
          // We wouldn't want to pop BrowseController when opening a board
          if (navigationController!!.top !is BrowseController) {
            navigationController!!.popController(true)
          }
        }
      }
    }

    setBoard(boardDescriptor)
  }

  override fun onSlideChanged(leftOpen: Boolean) {
    super.onSlideChanged(leftOpen)

    if (searchQuery != null) {
      toolbar.openSearch(searchQuery)
      toolbar.searchInput(searchQuery)
      searchQuery = null
    }

    if (chanDescriptor != null) {
      historyNavigationManager.moveNavElementToTop(chanDescriptor!!)
    }
  }

  override fun getPostForPostImage(postImage: PostImage): Post {
    return threadLayout.presenter.getPostFromPostImage(postImage)!!
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
    private const val ACTION_OPEN_THREAD_BY_ID = 909
    // TODO(KurobaEx): add action "open is a separate (new?) tab"

    private const val SORT_MODE_BUMP = 1000
    private const val SORT_MODE_REPLY = 1001
    private const val SORT_MODE_IMAGE = 1002
    private const val SORT_MODE_NEWEST = 1003
    private const val SORT_MODE_OLDEST = 1004
    private const val SORT_MODE_MODIFIED = 1005
    private const val SORT_MODE_ACTIVITY = 1006

    private const val DEV_BOOKMARK_EVERY_THREAD = 2000
  }
}
