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

import android.content.Context
import android.text.InputType
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.util.Pair
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.R.string.action_reload
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.core.manager.BookmarksManager.BookmarkChange
import com.github.adamantcheese.chan.core.manager.BookmarksManager.BookmarkChange.*
import com.github.adamantcheese.chan.core.manager.HistoryNavigationManager
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.ui.controller.ThreadSlideController.ReplyAutoCloseListener
import com.github.adamantcheese.chan.ui.controller.floating_menu.FloatingListMenuController
import com.github.adamantcheese.chan.ui.controller.navigation.NavigationController
import com.github.adamantcheese.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.adamantcheese.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.adamantcheese.chan.ui.helper.HintPopup
import com.github.adamantcheese.chan.ui.helper.PostHelper
import com.github.adamantcheese.chan.ui.layout.ThreadLayout.ThreadLayoutCallback
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.ui.toolbar.*
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem.ToobarThreedotMenuCallback
import com.github.adamantcheese.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.DialogUtils.createSimpleDialogWithInput
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.SharingUtils.getUrlForSharing
import com.github.adamantcheese.chan.utils.plusAssign
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

open class ViewThreadController(
  context: Context,
  private var threadDescriptor: ThreadDescriptor
) : ThreadController(context), ThreadLayoutCallback, ToobarThreedotMenuCallback, ReplyAutoCloseListener {
  @Inject
  lateinit var themeHelper: ThemeHelper
  @Inject
  lateinit var historyNavigationManager: HistoryNavigationManager
  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var archivesManager: ArchivesManager

  private var pinItemPinned = false

  // pairs of the current ThreadDescriptor and the thread we're going to's ThreadDescriptor
  private val threadFollowerpool: Deque<Pair<ThreadDescriptor, ThreadDescriptor>> = ArrayDeque()
  private var hintPopup: HintPopup? = null

  override fun onCreate() {
    super.onCreate()
    Chan.inject(this)

    threadLayout.setPostViewMode(ChanSettings.PostViewMode.LIST)
    view.setBackgroundColor(AndroidUtils.getAttrColor(context, R.attr.backcolor))
    navigation.hasDrawer = true
    navigation.scrollableTitle = ChanSettings.scrollingTextForThreadTitles.get()

    buildMenu()

    compositeDisposable += bookmarksManager.listenForBookmarksChanges()
      .onBackpressureLatest()
      .filter { bookmarkChange: BookmarkChange? -> bookmarkChange !is BookmarksInitialized }
      .debounce(250, TimeUnit.MILLISECONDS)
      .subscribe(
        { bookmarkChange -> updatePinIconStateIfNeeded(bookmarkChange) },
        { error -> Logger.e(TAG, "Error while listening for bookmarks changes", error) }
      )

    mainScope.launch { loadThread(threadDescriptor) }
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
    val menuBuilder = navigation.buildMenu(ToolbarMenuType.ThreadListMenu)

    if (!ChanSettings.textOnly.get()) {
      menuBuilder
        .withItem(ACTION_ALBUM, R.drawable.ic_image_white_24dp) { item -> albumClicked(item) }
    }

    menuBuilder.withItem(ACTION_PIN, R.drawable.ic_bookmark_border_white_24dp) { item -> pinClicked(item) }
    val menuOverflowBuilder = menuBuilder.withOverflow(navigationController, this)

    if (!ChanSettings.enableReplyFab.get()) {
      menuOverflowBuilder
        .withSubItem(ACTION_REPLY, R.string.action_reply) { item -> replyClicked(item) }
    }

    menuOverflowBuilder
      .withSubItem(
        ACTION_SEARCH,
        R.string.action_search) { item -> searchClicked(item) }
      .withSubItem(
        ACTION_RELOAD,
        action_reload) { item -> reloadClicked(item) }
      .withSubItem(
        ACTION_FORCE_RELOAD,
        R.string.action_force_reload,
        AndroidUtils.getFlavorType() == AndroidUtils.FlavorType.Dev
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
        AndroidUtils.getFlavorType() == AndroidUtils.FlavorType.Dev
      ) { item -> onGoToPostClicked(item) }
      .withScrollBarLabelsOptions()
      .withThreadViewOptions()
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

  private fun NavigationItem.MenuOverflowBuilder.withThreadViewOptions(): NavigationItem.MenuOverflowBuilder {
    return withNestedOverflow(
      ACTION_THREAD_VIEW_OPTIONS,
      R.string.action_thread_view_options,
      true
    )
      .addNestedCheckableItem(
        ACTION_USE_SCROLLING_TEXT_FOR_THREAD_TITLE,
        R.string.action_use_scrolling_text_for_thread_title,
        true,
        ChanSettings.scrollingTextForThreadTitles.get(),
        ACTION_USE_SCROLLING_TEXT_FOR_THREAD_TITLE
      ) { item -> onThreadViewOptionClicked(item) }
      .build()
  }

  private fun NavigationItem.MenuOverflowBuilder.withScrollBarLabelsOptions(): NavigationItem.MenuOverflowBuilder {
    return withNestedOverflow(
      ACTION_SHOW_SCROLLBAR_LABELING_OPTIONS,
      R.string.action_scrollbar_post_highlights,
      true
    )
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
      .addNestedCheckableItem(
        ACTION_MARK_ARCHIVED_POSTS_ON_SCROLLBAR,
        R.string.action_mark_archived_posts_on_scrollbar,
        true,
        ChanSettings.markArchivedPostsOnScrollbar.get(),
        ACTION_MARK_ARCHIVED_POSTS_ON_SCROLLBAR
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
    threadLayout.presenter.requestData()
  }

  private fun forceReloadClicked(item: ToolbarMenuSubItem) {
    threadLayout.presenter.forceRequestData()
  }

  private fun showAvailableArchives(descriptor: ThreadDescriptor) {
    Logger.d(TAG, "showAvailableArchives($descriptor)")
    
    val supportedArchiveDescriptors = archivesManager.getSupportedArchiveDescriptors(descriptor)
    if (supportedArchiveDescriptors.isEmpty()) {
      Logger.d(TAG, "showAvailableArchives($descriptor) supportedThreadDescriptors is empty")
      return
    }

    val items = mutableListOf<FloatingListMenuItem>()

    supportedArchiveDescriptors.forEach { archiveDescriptor ->
      val siteEnabled = siteManager.bySiteDescriptor(archiveDescriptor.siteDescriptor)?.enabled()
        ?: false

      if (!siteEnabled) {
        return@forEach
      }

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
    if (threadLayout.presenter.chanThread == null) {
      AndroidUtils.showToast(context, R.string.cannot_open_in_browser_already_deleted)
      return
    }

    val url = getUrlForSharing(siteManager, threadDescriptor)
    if (url == null) {
      AndroidUtils.showToast(context, R.string.cannot_open_in_browser_already_deleted)
      return
    }

    AndroidUtils.openLinkInBrowser(context, url, themeHelper.theme)
  }

  private fun shareClicked(item: ToolbarMenuSubItem) {
    if (threadLayout.presenter.chanThread == null) {
      AndroidUtils.showToast(context, R.string.cannot_shared_thread_already_deleted)
      return
    }

    val url = getUrlForSharing(siteManager, threadDescriptor)
    if (url == null) {
      AndroidUtils.showToast(context, R.string.cannot_shared_thread_already_deleted)
      return
    }

    AndroidUtils.shareLink(url)
  }

  private fun onGoToPostClicked(item: ToolbarMenuSubItem) {
    createSimpleDialogWithInput(
      context,
      R.string.view_thread_controller_enter_post_id,
      null,
      { input: String ->
        try {
          val postNo = input.toInt()
          threadLayout.presenter.scrollToPostByPostNo(postNo.toLong())
        } catch (e: NumberFormatException) {
          //ignored
        }
      },
      InputType.TYPE_CLASS_NUMBER
    ).show()
  }

  private fun onThreadViewOptionClicked(item: ToolbarMenuSubItem) {
    val clickedItemId = item.value as Int?
      ?: return

    if (clickedItemId == ACTION_USE_SCROLLING_TEXT_FOR_THREAD_TITLE) {
      val useScrollingTextForThreadView = !ChanSettings.scrollingTextForThreadTitles.get()
      ChanSettings.scrollingTextForThreadTitles.set(useScrollingTextForThreadView)

      item as CheckableToolbarMenuSubItem
      item.isCurrentlySelected = useScrollingTextForThreadView

      AndroidUtils.showToast(context, R.string.restart_the_app)
    } else {
      throw IllegalStateException("Unknown clickedItemId $clickedItemId")
    }
  }

  private fun onScrollbarLabelingOptionClicked(item: ToolbarMenuSubItem) {
    val clickedItemId = item.value as Int?
      ?: return

    when (clickedItemId) {
      ACTION_MARK_REPLIES_TO_YOU_ON_SCROLLBAR -> {
        val markReplies = !ChanSettings.markRepliesToYourPostOnScrollbar.get()
        ChanSettings.markRepliesToYourPostOnScrollbar.set(markReplies)

        item as CheckableToolbarMenuSubItem
        item.isCurrentlySelected = markReplies
      }
      ACTION_MARK_CROSS_THREAD_REPLIES_ON_SCROLLBAR -> {
        val markCrossThreadQuotes = !ChanSettings.markCrossThreadQuotesOnScrollbar.get()
        ChanSettings.markCrossThreadQuotesOnScrollbar.set(markCrossThreadQuotes)

        item as CheckableToolbarMenuSubItem
        item.isCurrentlySelected = markCrossThreadQuotes
      }
      ACTION_MARK_YOUR_POSTS_ON_SCROLLBAR -> {
        val markYourPostsOnScrollbar = !ChanSettings.markYourPostsOnScrollbar.get()
        ChanSettings.markYourPostsOnScrollbar.set(markYourPostsOnScrollbar)

        item as CheckableToolbarMenuSubItem
        item.isCurrentlySelected = markYourPostsOnScrollbar
      }
      ACTION_MARK_ARCHIVED_POSTS_ON_SCROLLBAR -> {
        val markArchivedPostsOnScrollbar = !ChanSettings.markArchivedPostsOnScrollbar.get()
        ChanSettings.markArchivedPostsOnScrollbar.set(markArchivedPostsOnScrollbar)

        item as CheckableToolbarMenuSubItem
        item.isCurrentlySelected = markArchivedPostsOnScrollbar
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

  override suspend fun showThread(descriptor: ThreadDescriptor) {
    mainScope.launch {
      Logger.d(TAG, "showThread($descriptor)")
      loadThread(descriptor)
    }
  }

  override suspend fun showExternalThread(threadToOpenDescriptor: ThreadDescriptor) {
    Logger.d(TAG, "showExternalThread($threadToOpenDescriptor)")

    val fullThreadName = threadToOpenDescriptor.siteName() + "/" +
      threadToOpenDescriptor.boardCode() + "/" +
      threadToOpenDescriptor.threadNo

    AlertDialog.Builder(context)
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.ok) { _, _ -> showExternalThreadInternal(threadToOpenDescriptor) }
      .setTitle(R.string.open_thread_confirmation)
      .setMessage(fullThreadName)
      .show()
  }

  override suspend fun showBoard(descriptor: BoardDescriptor) {
    mainScope.launch {
      Logger.d(TAG, "showBoard($descriptor)")
      showBoardInternal(descriptor, null)
    }
  }

  override suspend fun showBoardAndSearch(descriptor: BoardDescriptor, searchQuery: String?) {
    mainScope.launch {
      Logger.d(TAG, "showBoardAndSearch($descriptor, $searchQuery)")
      showBoardInternal(descriptor, searchQuery)
    }
  }

  private fun showExternalThreadInternal(threadToOpenDescriptor: ThreadDescriptor) {
    mainScope.launch {
      Logger.d(TAG, "showExternalThreadInternal($threadToOpenDescriptor)")

      threadFollowerpool.addFirst(Pair(threadDescriptor, threadToOpenDescriptor))
      loadThread(threadToOpenDescriptor)
    }
  }

  private suspend fun showBoardInternal(boardDescriptor: BoardDescriptor, searchQuery: String?) {
    Logger.d(TAG, "showBoardInternal($boardDescriptor, $searchQuery)")
    historyNavigationManager.moveNavElementToTop(CatalogDescriptor(boardDescriptor))

    if (doubleNavigationController != null && doubleNavigationController?.leftController is BrowseController) {
      val browseController = doubleNavigationController!!.leftController as BrowseController
      browseController.setBoard(boardDescriptor)
      if (searchQuery != null) {
        browseController.searchQuery = searchQuery
      }

      // slide layout
      doubleNavigationController!!.switchToController(true)

      return
    }

    if (doubleNavigationController != null && doubleNavigationController?.leftController is StyledToolbarNavigationController) {
      // split layout
      val browseController = doubleNavigationController!!.leftController.childControllers[0] as BrowseController
      browseController.setBoard(boardDescriptor)

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
      requireNavController().popController(false)

      // search after we're at the browse controller
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
    if (threadDescriptor != presenter.chanDescriptor) {
      loadThreadInternal(threadDescriptor)
    }
  }

  private suspend fun loadThreadInternal(threadDescriptor: ThreadDescriptor) {
    val presenter = threadLayout.presenter

    presenter.bindChanDescriptor(threadDescriptor)
    this.threadDescriptor = threadDescriptor

    updateMenuItems()
    updateNavigationTitle(threadDescriptor)
    requireNavController().requireToolbar().updateTitle(navigation)

    setPinIconState(false)
    updateLeftPaneHighlighting(threadDescriptor)

    presenter.requestInitialData()
    showHints()
  }

  private fun updateNavigationTitle(threadDescriptor: ThreadDescriptor?) {
    val chanThread = threadLayout.presenter.chanThread

    if (chanThread != null && chanThread.chanDescriptor == threadDescriptor) {
      navigation.title = PostHelper.getTitle(chanThread.op, threadDescriptor)
    } else {
      navigation.title = "Loading thread..."
    }
  }

  private fun updateMenuItems() {
    updateRetrievePostsFromArchivesMenuItem()
  }

  private fun updateRetrievePostsFromArchivesMenuItem() {
    val retrieveDeletedPostsItem = navigation.findSubItem(ACTION_OPEN_THREAD_IN_ARCHIVE)
      ?: return
    retrieveDeletedPostsItem.visible = threadDescriptor.siteDescriptor().is4chan()
  }

  private fun showHints() {
    val counter = ChanSettings.threadOpenCounter.increase()
    if (counter == 2) {
      view.postDelayed({
        val view: View? = navigation.findItem(ToolbarMenu.OVERFLOW_ID).view
        if (view != null) {
          dismissHintPopup()
          hintPopup = HintPopup.show(context, view, AndroidUtils.getString(R.string.thread_up_down_hint), -AndroidUtils.dp(1f), 0)
        }
      }, 600)
    } else if (counter == 3) {
      view.postDelayed({
        val view: View? = navigation.findItem(ACTION_PIN).view
        if (view != null) {
          dismissHintPopup()
          hintPopup = HintPopup.show(context, view, AndroidUtils.getString(R.string.thread_pin_hint), -AndroidUtils.dp(1f), 0)
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

    updateNavigationTitle(threadDescriptor)
    setPinIconState(false)
    requireNavController().requireToolbar().updateTitle(navigation)
    requireNavController().requireToolbar().updateViewForItem(navigation)
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
    val presenter = threadLayout.presenter
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

    val outline = context.getDrawable(R.drawable.ic_bookmark_border_white_24dp)
    val white = context.getDrawable(R.drawable.ic_bookmark_white_24dp)
    val drawable = if (pinned) white else outline

    menuItem.setImage(drawable, animated)
  }

  override fun threadBackPressed(): Boolean {
    // clear the pool if the current thread isn't a part of this crosspost chain
    // ie a new thread is loaded and a new chain is started; this will never throw null pointer exceptions
    if (!threadFollowerpool.isEmpty() && threadFollowerpool.peekFirst().second != threadDescriptor) {
      threadFollowerpool.clear()
    }

    // if the thread is new, it'll be empty here, so we'll get back-to-catalog functionality
    if (threadFollowerpool.isEmpty()) {
      return false
    }

    val threadDescriptor = threadFollowerpool.removeFirst().first
      ?: return false

    mainScope.launch { loadThread(threadDescriptor) }

    return true
  }

  override fun showAvailableArchivesList(descriptor: ThreadDescriptor) {
    showAvailableArchives(descriptor)
  }

  override fun getPostForPostImage(postImage: PostImage): Post? {
    return threadLayout.presenter.getPostFromPostImage(postImage)
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
    private const val ACTION_SHOW_SCROLLBAR_LABELING_OPTIONS = 9009
    private const val ACTION_THREAD_VIEW_OPTIONS = 9010
    private const val ACTION_SCROLL_TO_TOP = 9011
    private const val ACTION_SCROLL_TO_BOTTOM = 9012
    private const val ACTION_MARK_YOUR_POSTS_ON_SCROLLBAR = 9100
    private const val ACTION_MARK_REPLIES_TO_YOU_ON_SCROLLBAR = 9101
    private const val ACTION_MARK_CROSS_THREAD_REPLIES_ON_SCROLLBAR = 9102
    private const val ACTION_MARK_ARCHIVED_POSTS_ON_SCROLLBAR = 9103
    private const val ACTION_USE_SCROLLING_TEXT_FOR_THREAD_TITLE = 9200
  }
}
