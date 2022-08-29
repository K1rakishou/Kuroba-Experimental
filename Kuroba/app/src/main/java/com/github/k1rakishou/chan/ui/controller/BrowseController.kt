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
package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.Toast
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.BoardPostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.ui.NavigationControllerContainerLayout
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.FirewallBypassManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.presenter.BrowsePresenter
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.features.bypass.CookieResult
import com.github.k1rakishou.chan.features.bypass.SiteFirewallBypassController
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerActivity
import com.github.k1rakishou.chan.features.setup.BoardSelectionController
import com.github.k1rakishou.chan.features.setup.SiteSettingsController
import com.github.k1rakishou.chan.features.setup.SitesSetupController
import com.github.k1rakishou.chan.features.site_archive.BoardArchiveController
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController.ReplyAutoCloseListener
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController.SlideChangeListener
import com.github.k1rakishou.chan.ui.controller.navigation.SplitNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.layout.ThreadLayout.ThreadLayoutCallback
import com.github.k1rakishou.chan.ui.toolbar.CheckableToolbarMenuSubItem
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem
import com.github.k1rakishou.chan.ui.view.KurobaBottomNavigationView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.chan.utils.SpannableHelper
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
  lateinit var _boardManager: Lazy<BoardManager>
  @Inject
  lateinit var _historyNavigationManager: Lazy<HistoryNavigationManager>
  @Inject
  lateinit var _siteResolver: Lazy<SiteResolver>
  @Inject
  lateinit var _firewallBypassManager: Lazy<FirewallBypassManager>

  private val boardManager: BoardManager
    get() = _boardManager.get()
  private val historyNavigationManager: HistoryNavigationManager
    get() = _historyNavigationManager.get()
  private val siteResolver: SiteResolver
    get() = _siteResolver.get()
  private val firewallBypassManager: FirewallBypassManager
    get() = _firewallBypassManager.get()

  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  private var initialized = false
  private var menuBuiltOnce = false
  private var updateCompositeCatalogNavigationSubtitleJob: Job? = null

  override val threadControllerType: ThreadSlideController.ThreadControllerType
    get() = ThreadSlideController.ThreadControllerType.Catalog

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    val navControllerContainerLayout = inflate(context, R.layout.controller_browse)
    val container = navControllerContainerLayout.findViewById<View>(R.id.browse_controller_container) as NavigationControllerContainerLayout
    container.initBrowseControllerTracker(this, navigationController!!)
    container.addView(view)
    view = container

    // Navigation
    initNavigation()

    // Initialization
    serializedCoroutineExecutor = SerializedCoroutineExecutor(mainScope)

    threadLayout.setBoardPostViewMode(ChanSettings.boardPostViewMode.get())

    serializedCoroutineExecutor.post {
      val order = PostsFilter.Order.find(ChanSettings.boardOrder.get())

      threadLayout.presenter.setOrder(order, isManuallyChangedOrder = false)
    }

    mainScope.launch {
      firewallBypassManager.showFirewallControllerEvents.collect { showFirewallControllerInfo ->
        val alreadyPresenting = isAlreadyPresenting { controller ->
          controller is SiteFirewallBypassController && controller.alive
        }

        if (alreadyPresenting) {
          return@collect
        }

        val firewallType = showFirewallControllerInfo.firewallType
        val urlToOpen = showFirewallControllerInfo.urlToOpen
        val siteDescriptor = showFirewallControllerInfo.siteDescriptor
        val onFinished = showFirewallControllerInfo.onFinished

        try {
          showSiteFirewallBypassController(
            firewallType = firewallType,
            urlToOpen = urlToOpen,
            siteDescriptor = siteDescriptor
          )
        } finally {
          onFinished.complete(Unit)
        }
      }
    }
  }

  override fun onShow() {
    super.onShow()

    mainControllerCallbacks.resetBottomNavViewCheckState()

    if (KurobaBottomNavigationView.isBottomNavViewEnabled()) {
      mainControllerCallbacks.showBottomNavBar(unlockTranslation = false, unlockCollapse = false)
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    updateCompositeCatalogNavigationSubtitleJob?.cancel()
    updateCompositeCatalogNavigationSubtitleJob = null

    presenter.destroy()
  }

  override suspend fun showSitesNotSetup() {
    super.showSitesNotSetup()

    // this controller is used for catalog views; displaying things on two rows for them middle
    // menu is how we want it done these need to be setup before the view is rendered,
    // otherwise the subtitle view is removed
    navigation.title = getString(R.string.browse_controller_title_app_setup)
    navigation.subtitle = getString(R.string.browse_controller_subtitle)
    buildMenu()

    initialized = true
  }

  suspend fun setCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor) {
    Logger.d(TAG, "setCatalog($catalogDescriptor)")

    presenter.loadCatalog(catalogDescriptor)
    initialized = true
  }

  suspend fun loadWithDefaultBoard() {
    Logger.d(TAG, "loadWithDefaultBoard()")

    presenter.loadWithDefaultBoard()
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
    navigation.title = getString(R.string.loading)
    requireNavController().requireToolbar().updateTitle(navigation)

    // Presenter
    presenter.create(mainScope, this)
  }

  private fun openBoardSelectionController() {
    val siteDescriptor = if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      null
    } else {
      chanDescriptor?.siteDescriptor()
    }

    val boardSelectionController = BoardSelectionController(
      context = context,
      currentSiteDescriptor = siteDescriptor,
      callback = object : BoardSelectionController.UserSelectionListener {
        override fun onOpenSitesSettingsClicked() {
          openSitesSetupController()
        }

        override fun onSiteSelected(siteDescriptor: SiteDescriptor) {
          openSiteSettingsController(siteDescriptor)
        }

        override fun onCatalogSelected(catalogDescriptor: ChanDescriptor.ICatalogDescriptor) {
          if (currentOpenedDescriptorStateManager.currentCatalogDescriptor == catalogDescriptor) {
            return
          }

          mainScope.launch(Dispatchers.Main.immediate) { loadCatalog(catalogDescriptor) }
        }
      })

    requireNavController().presentController(boardSelectionController)
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
    val menuBuilder = navigation.buildMenu(context)
      .withItem(R.drawable.ic_search_white_24dp) { item -> searchClicked(item) }
      .withItem(R.drawable.ic_refresh_white_24dp) { item -> reloadClicked(item) }

    val overflowBuilder = menuBuilder.withOverflow(requireNavController())
    if (!ChanSettings.enableReplyFab.get()) {
      overflowBuilder.withSubItem(ACTION_REPLY, R.string.action_reply) { item -> replyClicked(item) }
    }

    val modeStringId = when (ChanSettings.boardPostViewMode.get()) {
      BoardPostViewMode.LIST -> R.string.action_switch_catalog_grid
      BoardPostViewMode.GRID -> R.string.action_switch_catalog_stagger
      BoardPostViewMode.STAGGER -> R.string.action_switch_board
    }

    val supportsArchive = siteSupportsBuiltInBoardArchive()
    val isCompositeCatalog = threadLayout.presenter.currentChanDescriptor is ChanDescriptor.CompositeCatalogDescriptor
    val isUnlimitedCatalog = threadLayout.presenter.isUnlimitedCatalog && !isCompositeCatalog

    overflowBuilder
      .withSubItem(ACTION_CHANGE_VIEW_MODE, modeStringId) { item -> viewModeClicked(item) }
      .addSortMenu()
      .addDevMenu()
      .withSubItem(
        ACTION_LOAD_WHOLE_COMPOSITE_CATALOG,
        R.string.action_rest_composite_catalog,
        isCompositeCatalog,
        { mainScope.launch { threadLayout.presenter.loadWholeCompositeCatalog() } }
      )
      .withSubItem(ACTION_CATALOG_ALBUM, R.string.action_catalog_album, { threadLayout.presenter.showAlbum() })
      .withSubItem(ACTION_OPEN_BROWSER, R.string.action_open_browser, !isCompositeCatalog, { item -> openBrowserClicked(item) })
      .withSubItem(ACTION_OPEN_CATALOG_OR_THREAD_BY_IDENTIFIER, R.string.action_open_catalog_or_thread_by_identifier, { item -> openCatalogOrThreadByIdentifier(item) })
      .withSubItem(ACTION_OPEN_MEDIA_BY_URL, R.string.action_open_media_by_url, { item -> openMediaByUrl(item) })
      .withSubItem(ACTION_OPEN_UNLIMITED_CATALOG_PAGE, R.string.action_open_catalog_page, isUnlimitedCatalog, { openCatalogPageClicked() })
      .withSubItem(ACTION_SHARE, R.string.action_share, { item -> shareClicked(item) })
      .withSubItem(ACTION_BOARD_ARCHIVE, R.string.action_board_archive, supportsArchive, { viewBoardArchiveClicked() })
      .withSubItem(ACTION_VIEW_REMOVED_THREADS, R.string.action_view_removed_threads, { threadLayout.presenter.showRemovedPostsDialog() })
      .withSubItem(ACTION_SCROLL_TO_TOP, R.string.action_scroll_to_top, { item -> upClicked(item) })
      .withSubItem(ACTION_SCROLL_TO_BOTTOM, R.string.action_scroll_to_bottom, { item -> downClicked(item) })
      .build()
      .build()

    requireNavController().requireToolbar().setNavigationItem(
      false,
      true,
      navigation,
      themeEngine.chanTheme
    )
  }

  @Suppress("MoveLambdaOutsideParentheses")
  private fun NavigationItem.MenuOverflowBuilder.addDevMenu(): NavigationItem.MenuOverflowBuilder {
    withNestedOverflow(
      ACTION_DEV_MENU,
      R.string.action_browse_dev_menu,
      isDevBuild()
    )
      .addNestedItem(
        DEV_BOOKMARK_EVERY_THREAD,
        R.string.dev_bookmark_every_thread,
        true,
        DEV_BOOKMARK_EVERY_THREAD,
        { mainScope.launch { presenter.bookmarkEveryThread(threadLayout.presenter.currentChanDescriptor) } }
      )
      .addNestedItem(
        DEV_CACHE_EVERY_THREAD,
        R.string.dev_cache_every_thread,
        true,
        DEV_CACHE_EVERY_THREAD,
        { presenter.cacheEveryThreadClicked(threadLayout.presenter.currentChanDescriptor) }
      )
      .build()

    return this
  }

  @Suppress("MoveLambdaOutsideParentheses")
  private fun NavigationItem.MenuOverflowBuilder.addSortMenu(): NavigationItem.MenuOverflowBuilder {
    val currentOrder = PostsFilter.Order.find(ChanSettings.boardOrder.get())
    val groupId = "catalog_sort"

    withNestedOverflow(ACTION_SORT, R.string.action_sort, true)
      .addNestedCheckableItem(
        SORT_MODE_BUMP,
        R.string.order_bump,
        true,
        currentOrder == PostsFilter.Order.BUMP,
        PostsFilter.Order.BUMP,
        groupId,
        { subItem -> onSortItemClicked(subItem) }
      )
      .addNestedCheckableItem(
        SORT_MODE_REPLY,
        R.string.order_reply,
        true,
        currentOrder == PostsFilter.Order.REPLY,
        PostsFilter.Order.REPLY,
        groupId,
        { subItem -> onSortItemClicked(subItem) }
      )
      .addNestedCheckableItem(
        SORT_MODE_IMAGE,
        R.string.order_image,
        true,
        currentOrder == PostsFilter.Order.IMAGE,
        PostsFilter.Order.IMAGE,
        groupId,
        { subItem -> onSortItemClicked(subItem) }
      )
      .addNestedCheckableItem(
        SORT_MODE_NEWEST,
        R.string.order_newest,
        true,
        currentOrder == PostsFilter.Order.NEWEST,
        PostsFilter.Order.NEWEST,
        groupId,
        { subItem -> onSortItemClicked(subItem) }
      )
      .addNestedCheckableItem(
        SORT_MODE_OLDEST,
        R.string.order_oldest,
        true,
        currentOrder == PostsFilter.Order.OLDEST,
        PostsFilter.Order.OLDEST,
        groupId,
        { subItem -> onSortItemClicked(subItem) }
      )
      .addNestedCheckableItem(
        SORT_MODE_MODIFIED,
        R.string.order_modified,
        true,
        currentOrder == PostsFilter.Order.MODIFIED,
        PostsFilter.Order.MODIFIED,
        groupId,
        { subItem -> onSortItemClicked(subItem) }
      )
      .addNestedCheckableItem(
        SORT_MODE_ACTIVITY,
        R.string.order_activity,
        true,
        currentOrder == PostsFilter.Order.ACTIVITY,
        PostsFilter.Order.ACTIVITY,
        groupId,
        { subItem -> onSortItemClicked(subItem) }
      )
      .build()

    return this
  }

  private fun onSortItemClicked(subItem: ToolbarMenuSubItem) {
    serializedCoroutineExecutor.post {
      val order = subItem.value as? PostsFilter.Order
        ?: return@post

      ChanSettings.boardOrder.set(order.orderName)

      navigation.findSubItem(ACTION_SORT)?.let { sortSubItem ->
        resetSelectedSortOrderItem(sortSubItem)
      }

      subItem as CheckableToolbarMenuSubItem
      subItem.isChecked = true

      val presenter = threadLayout.presenter
      presenter.setOrder(order, isManuallyChangedOrder = true)
    }
  }

  private fun resetSelectedSortOrderItem(item: ToolbarMenuSubItem) {
    if (item is CheckableToolbarMenuSubItem) {
      item.isChecked = false
    }

    for (nestedItem in item.moreItems) {
      resetSelectedSortOrderItem(nestedItem as CheckableToolbarMenuSubItem)
    }
  }

  private fun searchClicked(item: ToolbarMenuItem) {
    val presenter = threadLayout.presenter
    if (!presenter.isBoundAndCached || chanDescriptor == null) {
      return
    }

    threadLayout.popupHelper.showSearchPopup(chanDescriptor!!)
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
    var postViewMode = ChanSettings.boardPostViewMode.get()

    postViewMode = when (postViewMode) {
      BoardPostViewMode.LIST -> BoardPostViewMode.GRID
      BoardPostViewMode.GRID -> BoardPostViewMode.STAGGER
      BoardPostViewMode.STAGGER -> BoardPostViewMode.LIST
    }

    ChanSettings.boardPostViewMode.set(postViewMode)

    val viewModeText = when (postViewMode) {
      BoardPostViewMode.LIST -> R.string.action_switch_catalog_grid
      BoardPostViewMode.GRID -> R.string.action_switch_catalog_stagger
      BoardPostViewMode.STAGGER -> R.string.action_switch_board
    }

    item.text = getString(viewModeText)
    threadLayout.setBoardPostViewMode(postViewMode)
  }

  private fun openBrowserClicked(item: ToolbarMenuSubItem) {
    handleShareOrOpenInBrowser(false)
  }

  private fun openCatalogOrThreadByIdentifier(item: ToolbarMenuSubItem) {
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

  private fun openMediaByUrl(item: ToolbarMenuSubItem) {
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
    mainScope.launch {
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
    mainScope.launch(Dispatchers.Main.immediate) {
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

  private fun shareClicked(item: ToolbarMenuSubItem) {
    handleShareOrOpenInBrowser(true)
  }

  private fun viewBoardArchiveClicked() {
    val boardArchiveController = BoardArchiveController(
      context = context,
      catalogDescriptor = chanDescriptor!! as CatalogDescriptor,
      onThreadClicked = { threadDescriptor ->
        mainScope.launch { showThread(threadDescriptor, animated = true) }
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

  private fun upClicked(item: ToolbarMenuSubItem) {
    threadLayout.presenter.scrollTo(0, false)
  }

  private fun downClicked(item: ToolbarMenuSubItem) {
    threadLayout.presenter.scrollTo(-1, false)
  }

  override fun onReplyViewShouldClose() {
    threadLayout.openReply(false)
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

    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (site == null) {
      Logger.e(TAG, "handleShareOrOpenInBrowser() site == null " +
        "(siteDescriptor = ${chanDescriptor.siteDescriptor()})")
      showToast(R.string.cannot_open_in_browser_already_deleted)
      return
    }

    val link = site.resolvable().desktopUrl(chanDescriptor, null)
    if (share) {
      AppModuleAndroidUtils.shareLink(link)
    } else {
      AppModuleAndroidUtils.openLink(link)
    }
  }

  override suspend fun loadCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor) {
    mainScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "loadCatalog($catalogDescriptor)")

      updateToolbarTitle(catalogDescriptor)
      threadLayout.presenter.bindChanDescriptor(catalogDescriptor as ChanDescriptor)

      if (!menuBuiltOnce) {
        menuBuiltOnce = true
        buildMenu()
      }

      updateMenuItems()
    }
  }

  override suspend fun updateToolbarTitle(catalogDescriptor: ChanDescriptor.ICatalogDescriptor) {
    boardManager.awaitUntilInitialized()

    updateCompositeCatalogNavigationSubtitleJob?.cancel()
    updateCompositeCatalogNavigationSubtitleJob = null

    if (catalogDescriptor is CatalogDescriptor) {
      val boardDescriptor = catalogDescriptor.boardDescriptor

      val board = boardManager.byBoardDescriptor(boardDescriptor)
        ?: return

      navigation.title = "/" + boardDescriptor.boardCode + "/"
      navigation.subtitle = board.name ?: ""

      requireNavController().requireToolbar().updateTitle(navigation)
    } else {
      catalogDescriptor as ChanDescriptor.CompositeCatalogDescriptor

      navigation.title = getString(R.string.composite_catalog)
      navigation.subtitle = getString(R.string.browse_controller_composite_catalog_subtitle_loading)
      requireNavController().requireToolbar().updateTitle(navigation)

      updateCompositeCatalogNavigationSubtitleJob = mainScope.launch {
        val newTitle = presenter.getCompositeCatalogNavigationTitle(catalogDescriptor)
        if (newTitle.isNotNullNorEmpty()) {
          navigation.title = newTitle
        }

        navigation.subtitle = SpannableHelper.getCompositeCatalogNavigationSubtitle(
          siteManager = siteManager,
          coroutineScope = this,
          context = context,
          fontSizePx = sp(12f),
          compositeCatalogDescriptor = catalogDescriptor
        )

        requireNavController().requireToolbar().updateTitle(navigation)
      }
    }
  }

  override suspend fun showCatalogWithoutFocusing(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean) {
    mainScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "showCatalogWithoutFocusing($catalogDescriptor, $animated)")

      showCatalogInternal(
        catalogDescriptor = catalogDescriptor,
        showCatalogOptions = ShowCatalogOptions(
          switchToCatalogController = false,
          withAnimation = animated
        )
      )

      initialized = true
    }
  }

  override suspend fun showCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean) {
    mainScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "showBoard($catalogDescriptor, $animated)")

      showCatalogInternal(
        catalogDescriptor = catalogDescriptor,
        showCatalogOptions = ShowCatalogOptions(
          switchToCatalogController = true,
          withAnimation = animated
        )
      )

      initialized = true
    }
  }

  override suspend fun setCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean) {
    mainScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "setCatalog($catalogDescriptor, $animated)")

      setCatalog(catalogDescriptor)
      initialized = true
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
      mainScope.launch { showThread(descriptor = threadDescriptor, animated = true) }
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
        val navigationController = splitNav.getRightController() as StyledToolbarNavigationController?
        navigationController?.top as? ViewThreadController
      }
      slideNav != null -> {
        slideNav.getRightController() as? ViewThreadController
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

  override suspend fun showThreadWithoutFocusing(descriptor: ThreadDescriptor, animated: Boolean) {
    showThreadInternal(
      descriptor = descriptor,
      showThreadOptions = ShowThreadOptions(
        switchToThreadController = false,
        pushControllerWithAnimation = animated
      )
    )
  }

  private fun showThreadInternal(descriptor: ThreadDescriptor, showThreadOptions: ShowThreadOptions) {
    mainScope.launch(Dispatchers.Main.immediate) {
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
          if (splitNav.getRightController() is StyledToolbarNavigationController) {
            val navigationController = splitNav.getRightController() as StyledToolbarNavigationController
            if (navigationController.top is ViewThreadController) {
              val viewThreadController = navigationController.top as ViewThreadController
              viewThreadController.loadThread(descriptor)
              viewThreadController.onShow()
              viewThreadController.onGainedFocus(ThreadSlideController.ThreadControllerType.Thread)
            }
          } else {
            val navigationController = StyledToolbarNavigationController(context)
            splitNav.setRightController(navigationController, showThreadOptions.pushControllerWithAnimation)
            val viewThreadController = ViewThreadController(context, mainControllerCallbacks, descriptor)
            navigationController.pushController(viewThreadController, false)
            viewThreadController.onGainedFocus(ThreadSlideController.ThreadControllerType.Thread)
          }

          splitNav.switchToController(
            leftController = false,
            animated = showThreadOptions.pushControllerWithAnimation
          )
        }
        slideNav != null -> {
          // Create a threadview in the right part of the slide nav *without* a toolbar
          if (slideNav.getRightController() is ViewThreadController) {
            (slideNav.getRightController() as ViewThreadController).loadThread(descriptor)
            (slideNav.getRightController() as ViewThreadController).onShow()
          } else {
            val viewThreadController = ViewThreadController(
              context,
              mainControllerCallbacks,
              descriptor
            )

            slideNav.setRightController(
              rightController = viewThreadController,
              animated = showThreadOptions.pushControllerWithAnimation
            )
          }

          if (showThreadOptions.switchToThreadController) {
            slideNav.switchToController(
              leftController = false,
              animated = showThreadOptions.pushControllerWithAnimation
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
            to = viewThreadController,
            animated = showThreadOptions.pushControllerWithAnimation
          )
        }
      }

      initialized = true
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
          slideNav.switchToController(true, showCatalogOptions.withAnimation)
        }
      } else {
        if (navigationController != null) {
          // We wouldn't want to pop BrowseController when opening a board
          if (navigationController!!.top !is BrowseController) {
            navigationController!!.popController(showCatalogOptions.withAnimation)
          }
        }
      }
    }

    setCatalog(catalogDescriptor)
  }

  private fun updateMenuItems() {
    navigation.findSubItem(ACTION_BOARD_ARCHIVE)?.let { menuItem ->
      val supportsArchive = siteSupportsBuiltInBoardArchive()
      menuItem.visible = supportsArchive
    }

    navigation.findSubItem(ACTION_LOAD_WHOLE_COMPOSITE_CATALOG)?.let { menuItem ->
      val isCompositeCatalog =
        threadLayout.presenter.currentChanDescriptor is ChanDescriptor.CompositeCatalogDescriptor

      menuItem.visible = isCompositeCatalog
    }

    navigation.findSubItem(ACTION_OPEN_BROWSER)?.let { menuItem ->
      val isNotCompositeCatalog =
        threadLayout.presenter.currentChanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor

      menuItem.visible = isNotCompositeCatalog
    }

    navigation.findSubItem(ACTION_OPEN_UNLIMITED_CATALOG_PAGE)?.let { menuItem ->
      val isUnlimitedCatalog = threadLayout.presenter.isUnlimitedOrCompositeCatalog
        && threadLayout.presenter.currentChanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor

      menuItem.visible = isUnlimitedCatalog
    }

    navigation.findSubItem(ACTION_SHARE)?.let { menuItem ->
      val isNotCompositeCatalog =
        threadLayout.presenter.currentChanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor

      menuItem.visible = isNotCompositeCatalog
    }
  }

  override fun onLostFocus(wasFocused: ThreadSlideController.ThreadControllerType) {
    super.onLostFocus(wasFocused)
    check(wasFocused == threadControllerType) { "Unexpected controllerType: $wasFocused" }
  }

  override fun onGainedFocus(nowFocused: ThreadSlideController.ThreadControllerType) {
    super.onGainedFocus(nowFocused)
    check(nowFocused == threadControllerType) { "Unexpected controllerType: $nowFocused" }

    if (chanDescriptor != null && historyNavigationManager.isInitialized) {
      if (threadLayout.presenter.chanThreadLoadingState == ThreadPresenter.ChanThreadLoadingState.Loaded) {
        mainScope.launch { historyNavigationManager.moveNavElementToTop(chanDescriptor!!) }
      }
    }

    currentOpenedDescriptorStateManager.updateCurrentFocusedController(
      ThreadPresenter.CurrentFocusedController.Catalog
    )
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

  private suspend fun showSiteFirewallBypassController(
    firewallType: FirewallType,
    urlToOpen: HttpUrl,
    siteDescriptor: SiteDescriptor
  ) {
    val cookieResult = suspendCancellableCoroutine<CookieResult> { continuation ->
      val controller = SiteFirewallBypassController(
        context = context,
        firewallType = firewallType,
        urlToOpen = urlToOpen.toString(),
        onResult = { cookieResult -> continuation.resumeValueSafe(cookieResult) }
      )

      Logger.d(TAG, "presentController SiteFirewallBypassController " +
        "(firewallType=${firewallType}, urlToOpen=${urlToOpen}, hashcode=${controller.hashCode()})")
      presentController(controller)

      continuation.invokeOnCancellation {
        Logger.d(TAG, "stopPresenting SiteFirewallBypassController " +
          "(firewallType=${firewallType}, urlToOpen=${urlToOpen}, hashcode=${controller.hashCode()})")

        if (controller.alive) {
          controller.stopPresenting()
        }
      }
    }

    when (firewallType) {
      FirewallType.Cloudflare -> {
        when (cookieResult) {
          CookieResult.Canceled -> {
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.firewall_check_canceled, firewallType),
              Toast.LENGTH_LONG
            )
          }
          CookieResult.NotSupported -> {
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.firewall_check_not_supported, firewallType, siteDescriptor.siteName),
              Toast.LENGTH_LONG
            )
          }
          is CookieResult.Error -> {
            val errorMsg = cookieResult.exception.errorMessageOrClassName()
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.firewall_check_failure, firewallType, errorMsg),
              Toast.LENGTH_LONG
            )
          }
          is CookieResult.CookieValue -> {
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.firewall_check_success, firewallType),
              Toast.LENGTH_LONG
            )
          }
        }
      }
      FirewallType.DvachAntiSpam -> {
        when (cookieResult) {
          CookieResult.Canceled -> {
            AppModuleAndroidUtils.showToast(
              context,
              R.string.dvach_antispam_result_canceled,
              Toast.LENGTH_LONG
            )
          }
          CookieResult.NotSupported -> {
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.firewall_check_not_supported, firewallType, siteDescriptor.siteName),
              Toast.LENGTH_LONG
            )
          }
          is CookieResult.Error -> {
            val errorMsg = cookieResult.exception.errorMessageOrClassName()
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.dvach_antispam_result_error, errorMsg),
              Toast.LENGTH_LONG
            )
          }
          is CookieResult.CookieValue -> {
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.dvach_antispam_result_success),
              Toast.LENGTH_LONG
            )
          }
        }
      }
      FirewallType.YandexSmartCaptcha -> {
        // No-op. We only handle Yandex's captcha in one place (ImageSearchController)
      }
    }
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
  }
}
