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
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.R.string.action_reload
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.StartActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager.BookmarkChange
import com.github.k1rakishou.chan.core.manager.BookmarksManager.BookmarkChange.BookmarksCreated
import com.github.k1rakishou.chan.core.manager.BookmarksManager.BookmarkChange.BookmarksDeleted
import com.github.k1rakishou.chan.core.manager.BookmarksManager.BookmarkChange.BookmarksInitialized
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.LocalSearchType
import com.github.k1rakishou.chan.core.manager.ThreadFollowHistoryManager
import com.github.k1rakishou.chan.features.drawer.DrawerCallbacks
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController.ReplyAutoCloseListener
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.controller.settings.RangeSettingUpdaterController
import com.github.k1rakishou.chan.ui.helper.HintPopup
import com.github.k1rakishou.chan.ui.layout.ThreadLayout.ThreadLayoutCallback
import com.github.k1rakishou.chan.ui.misc.ConstraintLayoutBiasPair
import com.github.k1rakishou.chan.ui.toolbar.CheckableToolbarMenuSubItem
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenu
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem.ToobarThreedotMenuCallback
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openLinkInBrowser
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.shareLink
import com.github.k1rakishou.chan.utils.SharingUtils.getUrlForSharing
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.common.options.ChanLoadOptions
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ArchiveDescriptor
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.util.ChanPostUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

open class ViewThreadController(
  context: Context,
  drawerCallbacks: DrawerCallbacks?,
  startingThreadDescriptor: ThreadDescriptor
) : ThreadController(context, drawerCallbacks),
  ThreadLayoutCallback,
  ToobarThreedotMenuCallback,
  ReplyAutoCloseListener {

  @Inject
  lateinit var historyNavigationManager: HistoryNavigationManager
  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var chanThreadManager: ChanThreadManager
  @Inject
  lateinit var threadFollowHistoryManager: ThreadFollowHistoryManager

  private var pinItemPinned = false

  private var hintPopup: HintPopup? = null
  private var threadDescriptor: ThreadDescriptor = startingThreadDescriptor

  override val threadControllerType: ThreadControllerType
    get() = ThreadControllerType.Thread

  override fun injectDependencies(component: StartActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    threadLayout.setPostViewMode(ChanSettings.PostViewMode.LIST)
    view.setBackgroundColor(themeEngine.chanTheme.backColor)
    navigation.hasDrawer = true
    navigation.scrollableTitle = ChanSettings.scrollingTextForThreadTitles.get()

    buildMenu()

    compositeDisposable += bookmarksManager.listenForBookmarksChanges()
      .filter { bookmarkChange: BookmarkChange? -> bookmarkChange !is BookmarksInitialized }
      .onBackpressureLatest()
      .debounce(350, TimeUnit.MILLISECONDS)
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { bookmarkChange -> updatePinIconStateIfNeeded(bookmarkChange) },
        { error -> Logger.e(TAG, "Error while listening for bookmarks changes", error) }
      )

    mainScope.launch(Dispatchers.Main.immediate) { loadThread(threadDescriptor) }
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

  override fun onShow() {
    super.onShow()
    setPinIconState(false)

    if (drawerCallbacks != null) {
      drawerCallbacks?.resetBottomNavViewCheckState()
      if (ChanSettings.getCurrentLayoutMode() != ChanSettings.LayoutMode.SPLIT) {
        drawerCallbacks?.showBottomNavBar(unlockTranslation = false, unlockCollapse = false)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    dismissHintPopup()
    updateLeftPaneHighlighting(null)
  }

  protected fun buildMenu() {
    val gravity = if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
      ConstraintLayoutBiasPair.TopRight
    } else {
      ConstraintLayoutBiasPair.Top
    }

    val menuBuilder = navigation.buildMenu(gravity)

    if (!ChanSettings.textOnly.get()) {
      menuBuilder
        .withItem(ACTION_ALBUM, R.drawable.ic_image_white_24dp) { item -> albumClicked(item) }
    }

    menuBuilder
      .withItem(ACTION_PIN, R.drawable.ic_bookmark_border_white_24dp) { item -> pinClicked(item) }
    val menuOverflowBuilder = menuBuilder.withOverflow(navigationController, this)

    if (!ChanSettings.enableReplyFab.get()) {
      menuOverflowBuilder
        .withSubItem(ACTION_REPLY, R.string.action_reply) { item -> replyClicked(item) }
    }

    menuOverflowBuilder
      .withSubItem(
        ACTION_SEARCH,
        R.string.action_search
      ) { item -> searchClicked(item) }
      .withSubItem(
        ACTION_RELOAD,
        action_reload
      ) { item -> reloadClicked(item) }
      .withSubItem(
        ACTION_FORCE_RELOAD,
        R.string.action_force_reload,
        isDevBuild()
      ) { item -> forceReloadClicked(item) }
      .withSubItem(
        ACTION_VIEW_REMOVED_POSTS,
        R.string.action_view_removed_posts
      ) { item -> showRemovedPostsDialog(item) }
      .withSubItem(
        ACTION_OPEN_THREAD_IN_ARCHIVE,
        R.string.action_open_thread_in_archive,
        archivesManager.supports(threadDescriptor)
      ) { showAvailableArchives(threadDescriptor) }
      .withSubItem(
        ACTION_OPEN_BROWSER,
        R.string.action_open_browser
      ) { item -> openBrowserClicked(item) }
      .withSubItem(
        ACTION_SHARE,
        R.string.action_share
      ) { item -> shareClicked(item) }
      .withSubItem(
        ACTION_GO_TO_POST,
        R.string.action_go_to_post,
        isDevBuild()
      ) { item -> onGoToPostClicked(item) }
      .withThreadOptions()
      .withSubItem(
        ACTION_SCROLL_TO_TOP,
        R.string.action_scroll_to_top
      ) { item -> upClicked(item) }
      .withSubItem(
        ACTION_SCROLL_TO_BOTTOM,
        R.string.action_scroll_to_bottom
      ) { item -> downClicked(item) }

    menuOverflowBuilder
      .build()
      .build()
  }

  private fun NavigationItem.MenuOverflowBuilder.withThreadOptions(): NavigationItem.MenuOverflowBuilder {
    val maxDisplayedPostsCapString = getMaxDisplayedPostsCapString()

    return withNestedOverflow(
      ACTION_THREAD_OPTIONS,
      R.string.action_thread_options,
      true
    )
      .addNestedCheckableItem(
        ACTION_USE_SCROLLING_TEXT_FOR_THREAD_TITLE,
        R.string.action_use_scrolling_text_for_thread_title,
        true,
        ChanSettings.scrollingTextForThreadTitles.get(),
        ACTION_USE_SCROLLING_TEXT_FOR_THREAD_TITLE
      ) { item -> onThreadViewOptionClicked(item) }
      .addNestedCheckableItem(
        ACTION_MARK_YOUR_POSTS_ON_SCROLLBAR,
        R.string.action_mark_replies_your_posts_on_scrollbar,
        true,
        ChanSettings.markYourPostsOnScrollbar.get(),
        ACTION_MARK_YOUR_POSTS_ON_SCROLLBAR
      ) { item -> onScrollbarLabelingOptionClicked(item) }
      .addNestedCheckableItem(
        ACTION_MARK_REPLIES_TO_YOU_ON_SCROLLBAR,
        R.string.action_mark_replies_to_your_posts_on_scrollbar,
        true,
        ChanSettings.markRepliesToYourPostOnScrollbar.get(),
        ACTION_MARK_REPLIES_TO_YOU_ON_SCROLLBAR
      ) { item -> onScrollbarLabelingOptionClicked(item) }
      .addNestedCheckableItem(
        ACTION_MARK_CROSS_THREAD_REPLIES_ON_SCROLLBAR,
        R.string.action_mark_cross_thread_quotes_on_scrollbar,
        true,
        ChanSettings.markCrossThreadQuotesOnScrollbar.get(),
        ACTION_MARK_CROSS_THREAD_REPLIES_ON_SCROLLBAR
      ) { item -> onScrollbarLabelingOptionClicked(item) }
      .addNestedItem(
        ACTION_SET_THREAD_MAX_POSTS_CAP,
        maxDisplayedPostsCapString,
        true,
        null
      ) { item -> onChangeThreadMaxPostsCapacityOptionClicked(item) }
      .addNestedCheckableItem(
        ACTION_REMEMBER_THREAD_NAVIGATION_HISTORY,
        R.string.action_remember_thread_navigation_history,
        true,
        ChanSettings.rememberThreadNavigationHistory.get(),
        null
      ) { item -> onRememberThreadNavHistoryOptionClicked(item) }
      .build()
  }

  private fun getMaxDisplayedPostsCapString(): String? {
    val current = if (ChanSettings.threadMaxPostCapacity.isDefault) {
      "disabled"
    } else {
      ChanSettings.threadMaxPostCapacity.get()
    }

    return getString(R.string.action_set_thread_max_displayed_posts_capacity, current)
  }

  private fun albumClicked(item: ToolbarMenuItem) {
    threadLayout.presenter.showAlbum()
  }

  private fun pinClicked(item: ToolbarMenuItem) {
    if (threadLayout.presenter.pin()) {
      setPinIconState(true)
    }
  }

  private fun searchClicked(item: ToolbarMenuSubItem) {
    if (navigationController is ToolbarNavigationController) {
      (navigationController as ToolbarNavigationController).showSearch()
    }
  }

  private fun replyClicked(item: ToolbarMenuSubItem) {
    threadLayout.openReply(true)
  }

  override fun onReplyViewShouldClose() {
    threadLayout.openReply(false)
  }

  private fun reloadClicked(item: ToolbarMenuSubItem) {
    threadLayout.presenter.normalLoad(
      showLoading = true,
      chanLoadOptions = ChanLoadOptions.ClearMemoryCache
    )
  }

  private fun forceReloadClicked(item: ToolbarMenuSubItem) {
    threadLayout.presenter.normalLoad(
      showLoading = true,
      chanLoadOptions = ChanLoadOptions.ClearMemoryAndDatabaseCaches
    )
  }

  private fun showAvailableArchives(descriptor: ThreadDescriptor) {
    Logger.d(TAG, "showAvailableArchives($descriptor)")
    
    val supportedArchiveDescriptors = archivesManager.getSupportedArchiveDescriptors(descriptor)
      .filter { archiveDescriptor ->
        return@filter siteManager.bySiteDescriptor(archiveDescriptor.siteDescriptor)?.enabled()
          ?: false
      }

    if (supportedArchiveDescriptors.isEmpty()) {
      Logger.d(TAG, "showAvailableArchives($descriptor) supportedThreadDescriptors is empty")

      val message = getString(
        R.string.thread_presenter_no_archives_found_to_open_thread,
        descriptor.toString()
      )
      showToast(message, Toast.LENGTH_LONG)
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
      ConstraintLayoutBiasPair.TopRight,
      items,
      itemClickListener = { clickedItem ->
        val archiveDescriptor = (clickedItem.key as? ArchiveDescriptor)
          ?: return@FloatingListMenuController

        threadLayout.presenter.openThreadInArchive(descriptor, archiveDescriptor)
      }
    )

    presentController(floatingListMenuController)
  }

  private fun showRemovedPostsDialog(item: ToolbarMenuSubItem?) {
    threadLayout.presenter.showRemovedPostsDialog()
  }

  private fun openBrowserClicked(item: ToolbarMenuSubItem) {
    if (threadLayout.presenter.currentChanDescriptor == null) {
      showToast(R.string.cannot_open_in_browser_already_deleted)
      return
    }

    val url = getUrlForSharing(siteManager, threadDescriptor)
    if (url == null) {
      showToast(R.string.cannot_open_in_browser_already_deleted)
      return
    }

    openLinkInBrowser(context, url, themeEngine.chanTheme)
  }

  private fun shareClicked(item: ToolbarMenuSubItem) {
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

  private fun onGoToPostClicked(item: ToolbarMenuSubItem) {
    dialogFactory.createSimpleDialogWithInput(
      context = context,
      titleTextId = R.string.view_thread_controller_enter_post_id,
      onValueEntered = { input: String ->
        try {
          val postNo = input.toInt()
          threadLayout.presenter.scrollToPostByPostNo(postNo.toLong())
        } catch (e: NumberFormatException) {
          //ignored
        }
      },
      inputType = DialogFactory.DialogInputType.Integer
    )
  }

  private fun onThreadViewOptionClicked(item: ToolbarMenuSubItem) {
    val clickedItemId = item.value as Int?
      ?: return

    if (clickedItemId == ACTION_USE_SCROLLING_TEXT_FOR_THREAD_TITLE) {
      item as CheckableToolbarMenuSubItem
      item.isChecked = ChanSettings.scrollingTextForThreadTitles.toggle()

      showToast(R.string.restart_the_app)
    } else {
      throw IllegalStateException("Unknown clickedItemId $clickedItemId")
    }
  }

  private fun onRememberThreadNavHistoryOptionClicked(item: ToolbarMenuSubItem) {
    item as CheckableToolbarMenuSubItem
    item.isChecked = ChanSettings.rememberThreadNavigationHistory.toggle()
  }

  private fun onChangeThreadMaxPostsCapacityOptionClicked(item: ToolbarMenuSubItem) {
    val minPostsCap = ChanSettings.threadMaxPostCapacity.min
    val maxPostsCap = ChanSettings.threadMaxPostCapacity.max
    val currentPostsCap = ChanSettings.threadMaxPostCapacity.get()
    val defaultPostsCap = ChanSettings.threadMaxPostCapacity.default
    val title = getString(R.string.view_thread_controller_max_posts_cap)

    fun applyThreadMaxPostCapacity(oldValue: Int, newValue: Int) {
      if (oldValue == newValue) {
        return
      }

      ChanSettings.threadMaxPostCapacity.set(newValue)

      val subItem = navigation.findNestedSubItem(ACTION_SET_THREAD_MAX_POSTS_CAP)
      if (subItem != null) {
        subItem.text = getMaxDisplayedPostsCapString()
      }

      threadLayout.presenter.normalLoad(
        showLoading = true,
        requestNewPostsFromServer = true,
        chanLoadOptions = ChanLoadOptions.ClearMemoryAndDatabaseCaches
      )
    }

    val rangeSettingUpdaterController = RangeSettingUpdaterController(
      context = context,
      constraintLayoutBiasPair = ConstraintLayoutBiasPair.TopRight,
      title = title,
      minValue = minPostsCap.toFloat(),
      maxValue = maxPostsCap.toFloat(),
      currentValue = currentPostsCap.toFloat(),
      resetClickedFunc = { applyThreadMaxPostCapacity(currentPostsCap, defaultPostsCap) },
      applyClickedFunc = { newCap -> applyThreadMaxPostCapacity(currentPostsCap, newCap) }
    )

    requireNavController().presentController(rangeSettingUpdaterController)
  }

  private fun onScrollbarLabelingOptionClicked(item: ToolbarMenuSubItem) {
    val clickedItemId = item.value as Int?
      ?: return

    when (clickedItemId) {
      ACTION_MARK_REPLIES_TO_YOU_ON_SCROLLBAR -> {
        item as CheckableToolbarMenuSubItem
        item.isChecked = ChanSettings.markRepliesToYourPostOnScrollbar.toggle()
      }
      ACTION_MARK_CROSS_THREAD_REPLIES_ON_SCROLLBAR -> {
        item as CheckableToolbarMenuSubItem
        item.isChecked = ChanSettings.markCrossThreadQuotesOnScrollbar.toggle()
      }
      ACTION_MARK_YOUR_POSTS_ON_SCROLLBAR -> {
        item as CheckableToolbarMenuSubItem
        item.isChecked = ChanSettings.markYourPostsOnScrollbar.toggle()
      }
      else -> throw IllegalStateException("Unknown clickedItemId $clickedItemId")
    }

    threadLayout.presenter.quickReload()
  }

  private fun upClicked(item: ToolbarMenuSubItem) {
    threadLayout.scrollTo(0, false)
  }

  private fun downClicked(item: ToolbarMenuSubItem) {
    threadLayout.scrollTo(-1, false)
    val threadDescriptor = threadLayout.presenter.threadDescriptorOrNull()
    if (threadDescriptor != null) {
      // Force mark all posts in this thread as seen (because sometimes the very last post
      // ends up staying unseen for some unknown reason).
      bookmarksManager.readPostsAndNotificationsForThread(threadDescriptor)
    }
  }

  override suspend fun showThread(descriptor: ThreadDescriptor, animated: Boolean) {
    mainScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "showThread($descriptor, $animated)")
      loadThread(descriptor)
    }
  }

  override suspend fun showExternalThread(threadToOpenDescriptor: ThreadDescriptor) {
    Logger.d(TAG, "showExternalThread($threadToOpenDescriptor)")

    val fullThreadName = threadToOpenDescriptor.siteName() + "/" +
      threadToOpenDescriptor.boardCode() + "/" +
      threadToOpenDescriptor.threadNo

    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.open_thread_confirmation,
      descriptionText = fullThreadName,
      negativeButtonText = getString(R.string.cancel),
      positiveButtonText = getString(R.string.ok),
      onPositiveButtonClickListener = { showExternalThreadInternal(threadToOpenDescriptor) }
    )
  }

  override suspend fun showBoard(descriptor: BoardDescriptor, animated: Boolean) {
    mainScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "showBoard($descriptor, $animated)")
      showBoardInternal(descriptor, animated)
    }
  }

  override suspend fun setBoard(descriptor: BoardDescriptor, animated: Boolean) {
    mainScope.launch(Dispatchers.Main.immediate){
      Logger.d(TAG, "setBoard($descriptor, $animated)")
      showBoardInternal(descriptor, animated)
    }
  }

  private fun showExternalThreadInternal(threadToOpenDescriptor: ThreadDescriptor) {
    mainScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "showExternalThreadInternal($threadToOpenDescriptor)")

      loadThread(threadToOpenDescriptor)
    }
  }

  private suspend fun showBoardInternal(boardDescriptor: BoardDescriptor, animated: Boolean) {
    Logger.d(TAG, "showBoardInternal($boardDescriptor, $animated)")
    historyNavigationManager.moveNavElementToTop(CatalogDescriptor(boardDescriptor))

    if (doubleNavigationController != null && doubleNavigationController?.leftController is BrowseController) {
      val browseController = doubleNavigationController!!.leftController as BrowseController
      browseController.setBoard(boardDescriptor)

      // slide layout
      doubleNavigationController!!.switchToController(true, animated)

      return
    }

    if (doubleNavigationController != null && doubleNavigationController?.leftController is StyledToolbarNavigationController) {
      // split layout
      val browseController = doubleNavigationController!!.leftController.childControllers[0] as BrowseController
      browseController.setBoard(boardDescriptor)

      val searchQuery = localSearchManager.getSearchQuery(LocalSearchType.CatalogSearch)
      if (searchQuery != null) {
        browseController.toolbar?.let { toolbar ->
          toolbar.openSearchWithCallback { toolbar.searchInput(searchQuery) }
        }
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
      browseController.setBoard(boardDescriptor)
      requireNavController().popController(animated)

      // search after we're at the browse controller
      val searchQuery = localSearchManager.getSearchQuery(LocalSearchType.CatalogSearch)
      if (searchQuery != null) {
        browseController.toolbar?.let { toolbar ->
          toolbar.openSearchWithCallback { toolbar.searchInput(searchQuery) }
        }
      }
    }
  }

  suspend fun loadThread(threadDescriptor: ThreadDescriptor) {
    Logger.d(TAG, "loadThread($threadDescriptor)")
    historyNavigationManager.moveNavElementToTop(threadDescriptor)

    val presenter = threadLayout.presenter
    if (threadDescriptor != presenter.currentChanDescriptor) {
      loadThreadInternal(threadDescriptor)
    }
  }

  private suspend fun loadThreadInternal(newThreadDescriptor: ThreadDescriptor) {
    val presenter = threadLayout.presenter
    val oldThreadDescriptor = threadLayout.presenter.currentChanDescriptor as? ThreadDescriptor

    presenter.bindChanDescriptor(newThreadDescriptor)
    this.threadDescriptor = newThreadDescriptor

    updateMenuItems()
    updateNavigationTitle(oldThreadDescriptor, newThreadDescriptor)
    requireNavController().requireToolbar().updateTitle(navigation)

    setPinIconState(false)
    updateLeftPaneHighlighting(newThreadDescriptor)
    showHints()

    threadFollowHistoryManager.pushThreadDescriptor(newThreadDescriptor)
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
      navigation.title = getString(R.string.loading)
    }
  }

  private fun setNavigationTitleFromDescriptor(threadDescriptor: ThreadDescriptor?) {
    val originalPost = chanThreadManager.getChanThread(threadDescriptor)
      ?.getOriginalPost()

    navigation.title = ChanPostUtils.getTitle(originalPost, threadDescriptor)
  }

  private fun updateMenuItems() {
    navigation.findSubItem(ACTION_OPEN_THREAD_IN_ARCHIVE)?.let { retrieveDeletedPostsItem ->
      retrieveDeletedPostsItem.visible = threadDescriptor.siteDescriptor().is4chan()
    }
  }

  private fun showHints() {
    val counter = ChanSettings.threadOpenCounter.increase()
    if (counter == 2) {
      view.postDelayed({
        val view = navigation.findItem(ToolbarMenu.OVERFLOW_ID)?.view
        if (view != null) {
          dismissHintPopup()
          hintPopup = HintPopup.show(context, view, getString(R.string.thread_up_down_hint), -dp(1f), 0)
        }
      }, 600)
    } else if (counter == 3) {
      view.postDelayed({
        val view = navigation.findItem(ACTION_PIN)?.view
        if (view != null) {
          dismissHintPopup()
          hintPopup = HintPopup.show(context, view, getString(R.string.thread_pin_hint), -dp(1f), 0)
        }
      }, 600)
    }
  }

  private fun dismissHintPopup() {
    if (hintPopup != null) {
      hintPopup!!.dismiss()
      hintPopup = null
    }
  }

  override fun onShowPosts() {
    super.onShowPosts()

    setNavigationTitleFromDescriptor(threadDescriptor)
    setPinIconState(false)
    requireNavController().requireToolbar().updateTitle(navigation)
    requireNavController().requireToolbar().updateViewForItem(navigation)
  }

  override fun onShowError() {
    super.onShowError()

    navigation.title = getString(R.string.thread_loading_error_title)
    requireNavController().requireToolbar().updateTitle(navigation)
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

  private fun updateLeftPaneHighlighting(chanDescriptor: ChanDescriptor?) {
    if (doubleNavigationController == null) {
      return
    }

    var threadController: ThreadController? = null
    val leftController = doubleNavigationController?.leftController

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
      val threadNo = chanDescriptor.threadNo
      threadController.selectPost(threadNo)
    } else {
      threadController.selectPost(-1L)
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

    val menuItem = navigation.findItem(ACTION_PIN)
      ?: return

    pinItemPinned = pinned

    val outline = ContextCompat.getDrawable(context, R.drawable.ic_bookmark_border_white_24dp)
    val white = ContextCompat.getDrawable(context, R.drawable.ic_bookmark_white_24dp)
    val drawable = if (pinned) white else outline

    menuItem.setImage(drawable, animated)
  }

  override fun threadBackPressed(): Boolean {
    threadFollowHistoryManager.removeTop()

    val threadDescriptor = threadFollowHistoryManager.peek()
      ?: return false

    mainScope.launch(Dispatchers.Main.immediate) { loadThread(threadDescriptor) }
    return true
  }

  override fun threadBackLongPressed() {
    threadFollowHistoryManager.clearAllExcept(threadDescriptor)
    showToast(R.string.thread_follow_history_has_been_cleared)
  }

  override fun showAvailableArchivesList(threadDescriptor: ThreadDescriptor) {
    showAvailableArchives(threadDescriptor)
  }

  override fun onMenuShown() {
    // no-op
  }

  override fun onMenuHidden() {
    // no-op
  }

  override fun onSlideChanged(leftOpen: Boolean) {
    super.onSlideChanged(leftOpen)

    historyNavigationManager.moveNavElementToTop(threadDescriptor)
  }

  companion object {
    private const val TAG = "ViewThreadController"
    private const val ACTION_PIN = 8001
    private const val ACTION_ALBUM = 8002
    private const val ACTION_REPLY = 9000
    private const val ACTION_SEARCH = 9001
    private const val ACTION_RELOAD = 9002
    private const val ACTION_FORCE_RELOAD = 9003
    private const val ACTION_VIEW_REMOVED_POSTS = 9004
    private const val ACTION_OPEN_THREAD_IN_ARCHIVE = 9005
    private const val ACTION_OPEN_BROWSER = 9006
    private const val ACTION_SHARE = 9007
    private const val ACTION_GO_TO_POST = 9008
    private const val ACTION_THREAD_OPTIONS = 9009
    private const val ACTION_SCROLL_TO_TOP = 9010
    private const val ACTION_SCROLL_TO_BOTTOM = 9011

    private const val ACTION_USE_SCROLLING_TEXT_FOR_THREAD_TITLE = 9100
    private const val ACTION_MARK_YOUR_POSTS_ON_SCROLLBAR = 9101
    private const val ACTION_MARK_REPLIES_TO_YOU_ON_SCROLLBAR = 9102
    private const val ACTION_MARK_CROSS_THREAD_REPLIES_ON_SCROLLBAR = 9103
    private const val ACTION_SET_THREAD_MAX_POSTS_CAP = 9104
    private const val ACTION_REMEMBER_THREAD_NAVIGATION_HISTORY = 9105
  }
}
