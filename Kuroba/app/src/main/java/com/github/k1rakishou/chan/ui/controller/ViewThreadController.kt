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
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager.BookmarkChange
import com.github.k1rakishou.chan.core.manager.BookmarksManager.BookmarkChange.BookmarksCreated
import com.github.k1rakishou.chan.core.manager.BookmarksManager.BookmarkChange.BookmarksDeleted
import com.github.k1rakishou.chan.core.manager.BookmarksManager.BookmarkChange.BookmarksInitialized
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.features.drawer.DrawerCallbacks
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController.ReplyAutoCloseListener
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.layout.ThreadLayout.ThreadLayoutCallback
import com.github.k1rakishou.chan.ui.toolbar.CheckableToolbarMenuSubItem
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem.ToobarThreedotMenuCallback
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.shareLink
import com.github.k1rakishou.chan.utils.SharingUtils.getUrlForSharing
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ArchiveDescriptor
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.options.ChanLoadOptions
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
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private var pinItemPinned = false
  private var threadDescriptor: ThreadDescriptor = startingThreadDescriptor

  override val threadControllerType: ThreadSlideController.ThreadControllerType
    get() = ThreadSlideController.ThreadControllerType.Thread

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    threadLayout.setBoardPostViewMode(ChanSettings.BoardPostViewMode.LIST)
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
    updateLeftPaneHighlighting(null)
  }

  protected fun buildMenu() {
    val menuBuilder = navigation.buildMenu(context)
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
        ACTION_PREVIEW_THREAD_IN_ARCHIVE,
        R.string.action_preview_thread_in_archive,
        archivesManager.supports(threadDescriptor)
      ) { showAvailableArchives(threadDescriptor.toOriginalPostDescriptor()) }
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
      .build()
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
    if (chanDescriptor == null) {
      return
    }

    threadLayout.popupHelper.showSearchPopup(chanDescriptor!!)
  }

  private fun replyClicked(item: ToolbarMenuSubItem) {
    threadLayout.openReply(true)
  }

  override fun onReplyViewShouldClose() {
    threadLayout.openReply(false)
  }

  private fun reloadClicked(item: ToolbarMenuSubItem) {
    threadLayout.presenter.resetTicker()
    threadLayout.presenter.normalLoad(
      showLoading = true,
      chanLoadOptions = ChanLoadOptions.clearMemoryCache()
    )
  }

  private fun forceReloadClicked(item: ToolbarMenuSubItem) {
    threadLayout.presenter.resetTicker()
    threadLayout.presenter.normalLoad(
      showLoading = true,
      chanLoadOptions = ChanLoadOptions.clearMemoryAndDatabaseCaches()
    )
  }

  private fun showAvailableArchives(postDescriptor: PostDescriptor) {
    Logger.d(TAG, "showAvailableArchives($postDescriptor)")

    val descriptor = postDescriptor.descriptor as? ThreadDescriptor
      ?: return

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
      globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items,
      itemClickListener = { clickedItem ->
        mainScope.launch {
          val archiveDescriptor = (clickedItem.key as? ArchiveDescriptor)
            ?: return@launch

          val externalArchivePostDescriptor = PostDescriptor.create(
            archiveDescriptor.domain,
            postDescriptor.descriptor.boardCode(),
            postDescriptor.getThreadNo(),
            postDescriptor.postNo
          )

          showPostsInExternalThread(
            postDescriptor = externalArchivePostDescriptor,
            isPreviewingCatalogThread = false
          )
        }
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

    AppModuleAndroidUtils.openLink(url)
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
  }

  override suspend fun showThread(descriptor: ThreadDescriptor, animated: Boolean) {
    mainScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "showThread($descriptor, $animated)")
      loadThread(descriptor)
    }
  }

  override suspend fun showBoard(descriptor: BoardDescriptor, animated: Boolean) {
    mainScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "showBoard($descriptor, $animated)")
      showBoardInternal(descriptor, animated)
    }
  }

  override suspend fun setBoard(descriptor: BoardDescriptor, animated: Boolean) {
    mainScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "setBoard($descriptor, $animated)")
      showBoardInternal(descriptor, animated)
    }
  }

  private suspend fun showBoardInternal(boardDescriptor: BoardDescriptor, animated: Boolean) {
    Logger.d(TAG, "showBoardInternal($boardDescriptor, $animated)")
    historyNavigationManager.moveNavElementToTop(CatalogDescriptor.create(boardDescriptor))

    if (doubleNavigationController != null && doubleNavigationController?.getLeftController() is BrowseController) {
      val browseController = doubleNavigationController!!.getLeftController() as BrowseController
      browseController.setBoard(boardDescriptor)

      // slide layout
      doubleNavigationController!!.switchToController(true, animated)

      return
    }

    if (doubleNavigationController != null
      && doubleNavigationController?.getLeftController() is StyledToolbarNavigationController) {
      // split layout
      val browseController =
        doubleNavigationController!!.getLeftController()!!.childControllers[0] as BrowseController
      browseController.setBoard(boardDescriptor)
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
    }
  }

  suspend fun loadThread(
    threadDescriptor: ThreadDescriptor,
    openingExternalThread: Boolean = false,
    openingPreviousThread: Boolean = false
  ) {
    Logger.d(TAG, "loadThread($threadDescriptor)")
    historyNavigationManager.moveNavElementToTop(threadDescriptor)

    val presenter = threadLayout.presenter
    if (threadDescriptor != presenter.currentChanDescriptor) {
      loadThreadInternal(threadDescriptor, openingExternalThread, openingPreviousThread)
    }
  }

  private suspend fun loadThreadInternal(
    newThreadDescriptor: ThreadDescriptor,
    openingExternalThread: Boolean,
    openingPreviousThread: Boolean
  ) {
    if (!openingExternalThread && !openingPreviousThread) {
      threadFollowHistoryManager.clear()
    }

    val presenter = threadLayout.presenter
    val oldThreadDescriptor = threadLayout.presenter.currentChanDescriptor as? ThreadDescriptor

    presenter.bindChanDescriptor(newThreadDescriptor)
    this.threadDescriptor = newThreadDescriptor

    updateMenuItems()
    updateNavigationTitle(oldThreadDescriptor, newThreadDescriptor)
    requireNavController().requireToolbar().updateTitle(navigation)

    setPinIconState(false)
    updateLeftPaneHighlighting(newThreadDescriptor)
  }

  override suspend fun openExternalThread(postDescriptor: PostDescriptor) {
    val descriptor = chanDescriptor
      ?: return

    openExternalThreadHelper.openExternalThread(descriptor, postDescriptor) { threadDescriptor ->
      mainScope.launch { loadThread(threadDescriptor = threadDescriptor, openingExternalThread = true) }
    }
  }

  override suspend fun showPostsInExternalThread(
    postDescriptor: PostDescriptor,
    isPreviewingCatalogThread: Boolean
  ) {
    showPostsInExternalThreadHelper.showPostsInExternalThread(postDescriptor, isPreviewingCatalogThread)
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
    navigation.findSubItem(ACTION_PREVIEW_THREAD_IN_ARCHIVE)?.let { retrieveDeletedPostsItem ->
      retrieveDeletedPostsItem.visible = threadDescriptor.siteDescriptor().is4chan()
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

  private fun updateLeftPaneHighlighting(chanDescriptor: ChanDescriptor?) {
    if (doubleNavigationController == null) {
      return
    }

    var threadController: ThreadController? = null
    val leftController = doubleNavigationController?.getLeftController()

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
      threadController.selectPost(chanDescriptor.toOriginalPostDescriptor())
    } else {
      threadController.selectPost(null)
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
    val threadDescriptor = threadFollowHistoryManager.removeTop()
      ?: return false

    mainScope.launch(Dispatchers.Main.immediate) {
      loadThread(threadDescriptor, openingPreviousThread = true)
    }

    return true
  }

  override fun threadBackLongPressed() {
    threadFollowHistoryManager.clearAllExcept(threadDescriptor)
    showToast(R.string.thread_follow_history_has_been_cleared)
  }

  override fun showAvailableArchivesList(postDescriptor: PostDescriptor) {
    showAvailableArchives(postDescriptor)
  }

  override fun onMenuShown() {
    // no-op
  }

  override fun onMenuHidden() {
    // no-op
  }

  override fun onLostFocus(wasFocused: ThreadSlideController.ThreadControllerType) {
    super.onLostFocus(wasFocused)
    check(wasFocused == threadControllerType) { "Unexpected controllerType: $wasFocused" }
  }

  override fun onGainedFocus(nowFocused: ThreadSlideController.ThreadControllerType) {
    super.onGainedFocus(nowFocused)
    check(nowFocused == threadControllerType) { "Unexpected controllerType: $nowFocused" }

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
    private const val ACTION_PREVIEW_THREAD_IN_ARCHIVE = 9005
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
  }
}
