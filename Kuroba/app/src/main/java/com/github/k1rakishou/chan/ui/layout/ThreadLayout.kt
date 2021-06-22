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
package com.github.k1rakishou.chan.ui.layout

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.BoardPostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressEvent
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BottomNavBarVisibilityStateManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter.ThreadPresenterCallback
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.loader.ChanLoaderException
import com.github.k1rakishou.chan.features.bypass.BypassMode
import com.github.k1rakishou.chan.features.bypass.CookieResult
import com.github.k1rakishou.chan.features.bypass.SiteAntiSpamCheckBypassController
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.features.reencoding.ImageOptionsHelper
import com.github.k1rakishou.chan.features.reencoding.ImageOptionsHelper.ImageReencodingHelperCallback
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.controller.PostLinksController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper.PostPopupHelperCallback
import com.github.k1rakishou.chan.ui.helper.RemovedPostsHelper
import com.github.k1rakishou.chan.ui.helper.RemovedPostsHelper.RemovedPostsCallbacks
import com.github.k1rakishou.chan.ui.layout.ThreadListLayout.ThreadListLayoutCallback
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.view.HidingFloatingActionButton
import com.github.k1rakishou.chan.ui.view.LoadView
import com.github.k1rakishou.chan.ui.view.NavigationViewContract
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.widget.SnackbarWrapper
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getQuantityString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.filter.FilterType
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostHide
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * Wrapper around ThreadListLayout, so that it cleanly manages between a loading state
 * and the recycler view.
 */
class ThreadLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : CoordinatorLayout(context, attrs, defStyle),
  ThreadPresenterCallback,
  PostPopupHelperCallback,
  ImageReencodingHelperCallback,
  RemovedPostsCallbacks,
  View.OnClickListener,
  ThreadListLayoutCallback,
  CoroutineScope,
  ThemeEngine.ThemeChangesListener {

  private enum class Visible {
    EMPTY, LOADING, THREAD, ERROR
  }

  @Inject
  lateinit var presenter: ThreadPresenter
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var postHideManager: PostHideManager
  @Inject
  lateinit var bottomNavBarVisibilityStateManager: BottomNavBarVisibilityStateManager
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var chanThreadManager: ChanThreadManager
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var chanLoadProgressNotifier: ChanLoadProgressNotifier

  private lateinit var callback: ThreadLayoutCallback
  private lateinit var progressLayout: View
  private lateinit var progressStepText: ColorizableTextView
  private lateinit var loadView: LoadView
  private lateinit var replyButton: HidingFloatingActionButton
  private lateinit var threadListLayout: ThreadListLayout
  private lateinit var errorLayout: LinearLayout
  private lateinit var errorText: TextView
  private lateinit var errorRetryButton: ColorizableButton
  private lateinit var openThreadInArchiveButton: ColorizableButton
  private lateinit var postPopupHelper: PostPopupHelper
  private lateinit var imageReencodingHelper: ImageOptionsHelper
  private lateinit var removedPostsHelper: RemovedPostsHelper
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  var threadControllerType: ThreadSlideController.ThreadControllerType? = null
    private set
  private var drawerCallbacks: MainControllerCallbacks? = null
  private var newPostsNotification: SnackbarWrapper? = null
  private var replyButtonEnabled = false
  private var refreshedFromSwipe = false
  private var deletingDialog: ProgressDialog? = null
  private var visible: Visible? = null
  private var searchLinkPopupOpenJob: Job? = null

  private val scrollToBottomDebouncer = Debouncer(false)
  private val job = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("ThreadLayout")

  override val toolbar: Toolbar?
    get() = callback.toolbar

  override val chanDescriptor: ChanDescriptor?
    get() = presenter.currentChanDescriptor

  override val displayingPostDescriptors: List<PostDescriptor>
    get() = if (postPopupHelper.isOpen) {
      postPopupHelper.getDisplayingPostDescriptors()
    } else {
      threadListLayout.displayingPostDescriptors
    }

  override val currentPosition: IntArray?
    get() = threadListLayout.indexAndTop

  val popupHelper: PostPopupHelper
    get() = postPopupHelper

  val presenterOrNull: ThreadPresenter?
    get() {
      if (!::presenter.isInitialized) {
        return null
      }

      return presenter
    }

  init {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(context)
        .inject(this)
    }
  }

  fun setDrawerCallbacks(drawerCallbacks: MainControllerCallbacks?) {
    this.drawerCallbacks = drawerCallbacks
  }

  fun create(
    callback: ThreadLayoutCallback,
    threadControllerType: ThreadSlideController.ThreadControllerType,
    navigationViewContractType: NavigationViewContract.Type
  ) {
    this.callback = callback
    this.serializedCoroutineExecutor = SerializedCoroutineExecutor(this)
    this.threadControllerType = threadControllerType

    Logger.d(TAG, "ThreadLayout.create(threadControllerType=$threadControllerType)")

    // View binding
    loadView = findViewById(R.id.loadview)
    replyButton = findViewById(R.id.reply_button)
    replyButton.setThreadControllerType(threadControllerType)

    // Inflate ThreadListLayout
    threadListLayout = inflate(context, R.layout.layout_thread_list, this, false) as ThreadListLayout

    // Inflate error layout
    errorLayout = inflate(context, R.layout.layout_thread_error, this, false) as LinearLayout
    errorText = errorLayout.findViewById(R.id.text)
    errorRetryButton = errorLayout.findViewById(R.id.retry_button)
    openThreadInArchiveButton = errorLayout.findViewById(R.id.open_in_archive_button)

    // Inflate thread loading layout
    progressLayout = inflate(context, R.layout.layout_thread_progress, this, false)
    progressStepText = progressLayout.findViewById(R.id.loading_step)

    // View setup
    presenter.create(context, this)
    threadListLayout.onCreate(presenter, this, navigationViewContractType)
    postPopupHelper = PostPopupHelper(context, presenter, chanThreadManager, this)
    imageReencodingHelper = ImageOptionsHelper(context, this)
    removedPostsHelper = RemovedPostsHelper(context, presenter, this)
    errorText.typeface = themeEngine.chanTheme.mainFont
    errorRetryButton.setOnClickListener(this)
    openThreadInArchiveButton.setOnClickListener(this)

    // Setup
    replyButtonEnabled = ChanSettings.enableReplyFab.get()

    if (!replyButtonEnabled) {
      AndroidUtils.removeFromParentView(replyButton)
    } else {
      replyButton.setOnClickListener(this)
      replyButton.setToolbar(callback.toolbar!!)
    }

    launch {
      chanLoadProgressNotifier.progressEventsFlow.collect { chanLoadProgressEvent ->
        if (chanDescriptor != chanLoadProgressEvent.chanDescriptor) {
          return@collect
        }

        handleLoadProgressEvent(chanLoadProgressEvent)
      }
    }

    themeEngine.addListener(this)
  }

  fun destroy() {
    Logger.d(TAG, "ThreadLayout.destroy(threadControllerType=$threadControllerType)")

    drawerCallbacks = null
    threadControllerType = null

    themeEngine.removeListener(this)
    presenter.unbindChanDescriptor(true)
    threadListLayout.onDestroy()
    job.cancelChildren()
  }

  override fun onThemeChanged() {
    if (!presenter.isBound) {
      return
    }

    presenter.quickReload(
      showLoading = false,
      chanCacheUpdateOptions = ChanCacheUpdateOptions.DoNotUpdateCache
    )
  }

  override fun onClick(v: View) {
    if (v === errorRetryButton) {
      presenter.normalLoad(showLoading = true)
    } else if (v === openThreadInArchiveButton) {
      val threadDescriptor = presenter.currentChanDescriptor
      if (threadDescriptor is ChanDescriptor.ThreadDescriptor) {
        callback.showAvailableArchivesList(
          postDescriptor = threadDescriptor.toOriginalPostDescriptor(),
          preview = false
        )
      }
    } else if (v === replyButton) {
      // Give some time for the keyboard to show up because we need keyboards' insets for proper
      // recycler view paddings
      replyButton.postDelayed({ openReplyInternal(true) }, OPEN_REPLY_DELAY_MS)
    }
  }

  private fun openReplyInternal(openReplyLayout: Boolean): Boolean {
    if (openReplyLayout && !canOpenReplyLayout()) {
      showToast(context, R.string.post_posting_is_not_supported)
      return false
    }

    threadListLayout.openReply(openReplyLayout)
    return true
  }

  private fun canOpenReplyLayout(): Boolean {
    val supportsPosting = presenter.currentChanDescriptor?.siteDescriptor()?.let { siteDescriptor ->
      return@let siteManager.bySiteDescriptor(siteDescriptor)?.siteFeature(Site.SiteFeature.POSTING)
    } ?: false

    if (!supportsPosting) {
      return false
    }

    return true
  }

  fun canChildScrollUp(): Boolean {
    if (visible != Visible.THREAD) {
      return true
    }

    return threadListLayout.canChildScrollUp()
  }

  fun onBack(): Boolean {
    return threadListLayout.onBack()
  }

  fun sendKeyEvent(event: KeyEvent): Boolean {
    return threadListLayout.sendKeyEvent(event)
  }

  fun refreshFromSwipe() {
    refreshedFromSwipe = true

    presenter.resetTicker()
    presenter.normalLoad(showLoading = true)
  }

  fun lostFocus(wasFocused: ThreadSlideController.ThreadControllerType) {
    replyButton.lostFocus(wasFocused)
    threadListLayout.lostFocus(wasFocused)
  }

  fun gainedFocus(nowFocused: ThreadSlideController.ThreadControllerType) {
    replyButton.gainedFocus(nowFocused)
    threadListLayout.gainedFocus(nowFocused, visible == Visible.THREAD)
  }

  fun onShown(nowFocused: ThreadSlideController.ThreadControllerType) {
    threadListLayout.onShown(nowFocused, visible == Visible.THREAD)
  }

  fun setBoardPostViewMode(boardPostViewMode: BoardPostViewMode) {
    threadListLayout.setBoardPostViewMode(boardPostViewMode)
  }

  override fun showImageReencodingWindow(fileUuid: UUID, supportsReencode: Boolean) {
    presenter.showImageReencodingWindow(fileUuid, supportsReencode)
  }

  override fun threadBackPressed(): Boolean {
    return callback.threadBackPressed()
  }

  override fun threadBackLongPressed() {
    callback.threadBackLongPressed()
  }

  override fun presentController(controller: Controller) {
    callback.presentController(controller, animated = false)
  }

  override fun unpresentController(predicate: (Controller) -> Boolean) {
    callback.unpresentController(predicate)
  }

  @OptIn(ExperimentalTime::class)
  override suspend fun showPostsForChanDescriptor(
    descriptor: ChanDescriptor?,
    filter: PostsFilter
  ) {
    if (descriptor == null) {
      Logger.d(TAG, "showPostsForChanDescriptor() descriptor==null")
      return
    }

    val initial = visible != Visible.THREAD

    val (showPostsResult, totalDuration) = measureTimedValue {
      threadListLayout.showPosts(loadView.width, descriptor, filter, initial)
    }
    val applyFilterDuration = showPostsResult.applyFilterDuration
    val setThreadPostsDuration = showPostsResult.setThreadPostsDuration

    Logger.d(TAG, "showPostsForChanDescriptor() showPosts($descriptor) -> ${showPostsResult.result} " +
      "applyFilterDuration=${applyFilterDuration}, setThreadPostsDuration=${setThreadPostsDuration}, " +
      "totalDuration=${totalDuration}")

    if (!showPostsResult.result) {
      switchVisible(Visible.EMPTY)
      return
    }

    switchVisible(Visible.THREAD)
    callback.onShowPosts()
  }

  override fun postClicked(postDescriptor: PostDescriptor) {
    if (postPopupHelper.isOpen) {
      postPopupHelper.postClicked(postDescriptor)
    }
  }

  override fun showError(error: ChanLoaderException) {
    if (hasSupportedActiveArchives() && !error.isCloudFlareError()) {
      openThreadInArchiveButton.setVisibilityFast(View.VISIBLE)
    } else {
      openThreadInArchiveButton.setVisibilityFast(View.GONE)
    }

    val errorMessage = getString(error.errorMessage)

    if (visible == Visible.THREAD) {
      // Hide the button so the user can see the full error message
      replyButton.hide()
      threadListLayout.showError(errorMessage)
    } else {
      switchVisible(Visible.ERROR)
      errorText.text = errorMessage
    }

    callback.onShowError()

    if (error.isCloudFlareError()) {
      openCloudFlareBypassControllerAndHandleResult(error)
    }
  }

  private fun openCloudFlareBypassControllerAndHandleResult(error: ChanLoaderException) {
    val presenting = callback
      .isAlreadyPresentingController { controller -> controller is SiteAntiSpamCheckBypassController }

    if (presenting) {
      return
    }

    val controller = SiteAntiSpamCheckBypassController(
      context = context,
      bypassMode = BypassMode.BypassCloudflare,
      urlToOpen = error.getOriginalRequestHost(),
      onResult = { cookieResult ->
        when (cookieResult) {
          is CookieResult.CookieValue -> {
            showToast(context, "Successfully passed CloudFlare checks!")
            presenter.normalLoad()

            return@SiteAntiSpamCheckBypassController
          }
          is CookieResult.Error -> {
            showToast(
              context,
              "Failed to pass CloudFlare checks, reason: ${cookieResult.exception.errorMessageOrClassName()}"
            )
          }
          CookieResult.Timeout -> {
            showToast(context, "Failed to pass CloudFlare checks, reason: Timeout when trying to bypass 2ch antispam system")
          }
          CookieResult.Canceled -> {
            showToast(context, "Failed to pass CloudFlare checks, reason: Canceled")
          }
        }
      }
    )

    callback.presentController(controller, animated = true)
  }

  private fun hasSupportedActiveArchives(): Boolean {
    val threadDescriptor = presenterOrNull?.currentChanDescriptor?.threadDescriptorOrNull()
      ?: return false

    val archiveSiteDescriptors = archivesManager.getSupportedArchiveDescriptors(threadDescriptor)
      .map { archiveDescriptor -> archiveDescriptor.siteDescriptor }

    val hasActiveSites = archiveSiteDescriptors
      .any { siteDescriptor -> siteManager.isSiteActive(siteDescriptor) }

    if (!hasActiveSites) {
      return false
    }

    return true
  }

  override fun showLoading() {
    switchVisible(Visible.LOADING)
  }

  override fun showEmpty() {
    switchVisible(Visible.EMPTY)
  }

  override fun showPostInfo(info: String) {
    dialogFactory.createSimpleInformationDialog(
      context = context,
      titleText = getString(R.string.post_info_title),
      descriptionText = info
    )
  }

  @Suppress("MoveLambdaOutsideParentheses")
  override fun showPostLinkables(post: ChanPost) {
    val linkables = post.postComment.linkables
      .filter { postLinkable -> postLinkable.type == PostLinkable.Type.LINK }

    if (linkables.isEmpty()) {
      showToast(context, context.getString(R.string.no_links_found))
      return
    }

    val postLinksController = PostLinksController(
      post,
      { postLinkable -> presenter.onPostLinkableClicked(post, postLinkable) },
      context
    )

    callback.presentController(postLinksController, animated = true)
  }

  override fun clipboardPost(post: ChanPost) {
    AndroidUtils.setClipboardContent("Post text", post.postComment.comment().toString())
    showToast(context, R.string.post_text_copied)
  }

  override fun openLink(link: String) {
    if (!ChanSettings.openLinkConfirmation.get()) {
      AppModuleAndroidUtils.openLink(link)
      return
    }

    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.open_link_confirmation,
      descriptionText = link,
      onPositiveButtonClickListener = { AppModuleAndroidUtils.openLink(link) }
    )
  }

  override fun openReportView(post: ChanPost) {
    callback.openReportController(post)
  }

  override suspend fun showThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    Logger.d(TAG, "showThread($threadDescriptor)")

    callback.showThread(threadDescriptor, true)
  }

  override suspend fun showPostInExternalThread(postDescriptor: PostDescriptor) {
    Logger.d(TAG, "showPostInExternalThread($postDescriptor)")

    callback.showPostsInExternalThread(
      postDescriptor = postDescriptor,
      isPreviewingCatalogThread = false
    )
  }

  override suspend fun previewCatalogThread(postDescriptor: PostDescriptor) {
    Logger.d(TAG, "showPostInExternalThread($postDescriptor)")

    callback.showPostsInExternalThread(
      postDescriptor = postDescriptor,
      isPreviewingCatalogThread = true
    )
  }

  override suspend fun openExternalThread(postDescriptor: PostDescriptor) {
    Logger.d(TAG, "openExternalThread($postDescriptor)")

    callback.openExternalThread(postDescriptor)
  }

  override suspend fun showBoard(boardDescriptor: BoardDescriptor, animated: Boolean) {
    Logger.d(TAG, "showBoard($boardDescriptor, $animated)")

    callback.showBoard(boardDescriptor, animated)
  }

  override suspend fun setBoard(boardDescriptor: BoardDescriptor, animated: Boolean) {
    Logger.d(TAG, "setBoard($boardDescriptor, $animated)")

    callback.setBoard(boardDescriptor, animated)
  }

  override suspend fun setBoardWithSearchQuery(
    boardDescriptor: BoardDescriptor,
    searchQuery: String,
    animated: Boolean
  ) {
    Logger.d(TAG, "setBoardWithSearchQuery($boardDescriptor, $searchQuery, $animated)")

    searchLinkPopupOpenJob?.cancel()
    searchLinkPopupOpenJob = null

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      searchLinkPopupOpenJob = coroutineScope {
        launch {
          delay(500L)

          if (!isActive) {
            return@launch
          }

          val catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(boardDescriptor)
          postPopupHelper.showSearchPopup(catalogDescriptor, searchQuery)

          searchLinkPopupOpenJob = null
        }
      }
    } else {
      val catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(boardDescriptor)
      postPopupHelper.showSearchPopup(catalogDescriptor, searchQuery)
    }

    callback.setBoard(boardDescriptor, animated)
  }

  override fun showPostsPopup(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    postViewMode: PostCellData.PostViewMode,
    postDescriptor: PostDescriptor,
    posts: List<ChanPost>
  ) {
    if (this.focusedChild != null) {
      val currentFocus = this.focusedChild
      AndroidUtils.hideKeyboard(currentFocus)
      currentFocus.clearFocus()
    }

    postPopupHelper.showRepliesPopup(threadDescriptor, postViewMode, postDescriptor, posts)
  }

  override fun hidePostsPopup() {
    postPopupHelper.popAll()
  }

  override fun showImages(chanDescriptor: ChanDescriptor, initialImageUrl: String?, transitionThumbnailUrl: String) {
    if (this.focusedChild != null) {
      val currentFocus = this.focusedChild
      AndroidUtils.hideKeyboard(currentFocus)
      currentFocus.clearFocus()
    }

    if (postPopupHelper.isOpen) {
      postPopupHelper.onImageIsAboutToShowUp()
    }

    callback.showImages(chanDescriptor, initialImageUrl, transitionThumbnailUrl)
  }

  override fun showAlbum(initialImageUrl: HttpUrl?) {
    callback.showAlbum(initialImageUrl)
  }

  override fun scrollTo(displayPosition: Int, smooth: Boolean) {
    if (postPopupHelper.isOpen) {
      postPopupHelper.scrollTo(displayPosition, smooth)
    } else if (visible == Visible.THREAD) {
      threadListLayout.scrollTo(displayPosition)
    }
  }

  override fun smoothScrollNewPosts(displayPosition: Int) {
    threadListLayout.smoothScrollNewPosts(displayPosition)
  }

  override fun highlightPost(postDescriptor: PostDescriptor) {
    threadListLayout.highlightPost(postDescriptor)
  }

  override fun highlightPostId(id: String) {
    threadListLayout.highlightPostId(id)
  }

  override fun highlightPostTripcode(tripcode: CharSequence?) {
    threadListLayout.highlightPostTripcode(tripcode)
  }

  override fun filterPostTripcode(tripcode: CharSequence?) {
    callback.openFilterForType(FilterType.TRIPCODE, tripcode.toString())
  }

  override fun selectPost(postDescriptor: PostDescriptor?) {
    threadListLayout.selectPost(postDescriptor)
  }

  override fun quote(post: ChanPost, withText: Boolean) {
    if (!canOpenReplyLayout()) {
      showToast(context, R.string.post_posting_is_not_supported)
      return
    }

    val descriptor = chanDescriptor
      ?: return

    bottomNavBarVisibilityStateManager.replyViewStateChanged(
      descriptor.isCatalogDescriptor(),
      true
    )

    postDelayed({
      openReplyInternal(true)
      threadListLayout.replyPresenter.quote(post, withText)
    }, OPEN_REPLY_DELAY_MS)
  }

  override fun quote(postDescriptor: PostDescriptor, text: CharSequence) {
    if (!canOpenReplyLayout()) {
      showToast(context, R.string.post_posting_is_not_supported)
      return
    }

    val descriptor = chanDescriptor
      ?: return

    bottomNavBarVisibilityStateManager.replyViewStateChanged(
      descriptor.isCatalogDescriptor(),
      true
    )

    postDelayed({
      openReplyInternal(true)
      threadListLayout.replyPresenter.quote(postDescriptor, text)
    }, OPEN_REPLY_DELAY_MS)
  }

  @Suppress("MoveLambdaOutsideParentheses")
  override fun confirmPostDelete(post: ChanPost) {
    @SuppressLint("InflateParams")
    val view = inflate(context, R.layout.dialog_post_delete, null)
    val checkBox = view.findViewById<CheckBox>(R.id.image_only)

    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.delete_confirm,
      customView = view,
      onPositiveButtonClickListener = { presenter.deletePostConfirmed(post, checkBox.isChecked) }
    )
  }

  override fun showDeleting() {
    if (deletingDialog == null) {
      deletingDialog = ProgressDialog.show(context, null, getString(R.string.delete_wait))
    }
  }

  override fun hideDeleting(message: String) {
    if (deletingDialog != null) {
      deletingDialog!!.dismiss()
      deletingDialog = null

      dialogFactory.createSimpleConfirmationDialog(
        context = context,
        descriptionText = message
      )
    }
  }

  @Suppress("MoveLambdaOutsideParentheses")
  override fun hideThread(post: ChanPost, threadNo: Long, hide: Boolean) {
    serializedCoroutineExecutor.post {
      // hideRepliesToThisPost is false here because we don't have posts in the catalog mode so there
      // is no point in hiding replies to a thread
      val postHide = ChanPostHide(
        postDescriptor = post.postDescriptor,
        onlyHide = hide,
        applyToWholeThread = true,
        applyToReplies = false
      )

      postHideManager.create(postHide)
      presenter.refreshUI()

      val snackbarStringId = if (hide) {
        R.string.thread_hidden
      } else {
        R.string.thread_removed
      }

      SnackbarWrapper.create(
        globalWindowInsetsManager,
        themeEngine.chanTheme,
        this,
        snackbarStringId,
        Snackbar.LENGTH_LONG
      ).apply {
        setAction(R.string.undo, {
          serializedCoroutineExecutor.post {
            postFilterManager.remove(post.postDescriptor)
            postHideManager.remove(postHide.postDescriptor)
            presenter.refreshUI()
          }
        })
        show()
      }
    }
  }

  override fun hideOrRemovePosts(
    hide: Boolean,
    wholeChain: Boolean,
    postDescriptors: Set<PostDescriptor>,
    threadNo: Long
  ) {
    serializedCoroutineExecutor.post {
      val hideList: MutableList<ChanPostHide> = ArrayList()
      for (postDescriptor in postDescriptors) {
        // Do not add the OP post to the hideList since we don't want to hide an OP post
        // while being in a thread (it just doesn't make any sense)
        if (!postDescriptor.isOP()) {
          hideList.add(
            ChanPostHide(
              postDescriptor = postDescriptor,
              onlyHide = hide,
              applyToWholeThread = false,
              applyToReplies = wholeChain
            )
          )
        }
      }

      postHideManager.createMany(hideList)
      presenter.refreshUI()

      val formattedString = if (hide) {
        getQuantityString(R.plurals.post_hidden, postDescriptors.size, postDescriptors.size)
      } else {
        getQuantityString(R.plurals.post_removed, postDescriptors.size, postDescriptors.size)
      }

      SnackbarWrapper.create(
        globalWindowInsetsManager,
        themeEngine.chanTheme,
        this,
        formattedString,
        Snackbar.LENGTH_LONG
      ).apply {
        setAction(R.string.undo) {
          serializedCoroutineExecutor.post {
            postFilterManager.removeMany(postDescriptors)

            postHideManager.removeManyChanPostHides(hideList.map { postHide -> postHide.postDescriptor })
            presenter.refreshUI()
          }
        }

        show()
      }
    }
  }

  override fun unhideOrUnremovePost(post: ChanPost) {
    serializedCoroutineExecutor.post {
      postFilterManager.remove(post.postDescriptor)

      postHideManager.removeManyChanPostHides(listOf(post.postDescriptor))
      presenter.refreshUI()
    }
  }

  override fun viewRemovedPostsForTheThread(
    threadPosts: List<PostDescriptor>,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ) {
    removedPostsHelper.showPosts(threadPosts, threadDescriptor)
  }

  override fun onRestoreRemovedPostsClicked(
    chanDescriptor: ChanDescriptor,
    selectedPosts: List<PostDescriptor>
  ) {
    serializedCoroutineExecutor.post {
      postHideManager.removeManyChanPostHides(selectedPosts)
      presenter.refreshUI()

      SnackbarWrapper.create(
        globalWindowInsetsManager,
        themeEngine.chanTheme,
        this,
        getString(R.string.restored_n_posts, selectedPosts.size),
        Snackbar.LENGTH_LONG
      ).apply { show() }
    }
  }

  override suspend fun onPostUpdated(updatedPost: ChanPost, results: List<LoaderResult>) {
    BackgroundUtils.ensureMainThread()

    if (postPopupHelper.isOpen) {
      postPopupHelper.onPostUpdated(updatedPost)
    }

    threadListLayout.onPostUpdated(updatedPost)
  }

  override fun presentController(
    controller: Controller,
    animate: Boolean
  ) {
    callback.presentController(controller, animate)
  }

  override fun showToolbar() {
    val currentToolbar = toolbar
      ?: return

    scrollToBottomDebouncer.post({
      currentToolbar.collapseShow(true)
    }, SCROLL_TO_BOTTOM_DEBOUNCE_TIMEOUT_MS)
  }

  override fun showAvailableArchivesList(postDescriptor: PostDescriptor, preview: Boolean) {
    callback.showAvailableArchivesList(postDescriptor, preview)
  }

  override fun currentSpanCount(): Int {
    return threadListLayout.currentSpanCount
  }

  override fun getTopPostRepliesDataOrNull(): PostPopupHelper.PostPopupData? {
    if (!::postPopupHelper.isInitialized) {
      return null
    }

    return postPopupHelper.topOrNull()
  }

  override fun openFiltersController(chanFilterMutable: ChanFilterMutable) {
    callback.openFiltersController(chanFilterMutable)
  }

  override fun showNewPostsNotification(show: Boolean, newPostsCount: Int) {
    if (!show) {
      dismissSnackbar()
      return
    }

    val descriptor = presenter.currentChanDescriptor
      ?: return

    if (threadListLayout.scrolledToBottom() || !BackgroundUtils.isInForeground()) {
      dismissSnackbar()
      return
    }

    val isReplyLayoutVisible = when (descriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        bottomNavBarVisibilityStateManager.isThreadReplyLayoutVisible()
      }
      is ChanDescriptor.CatalogDescriptor -> {
        bottomNavBarVisibilityStateManager.isCatalogReplyLayoutVisible()
      }
    }

    if (!isReplyLayoutVisible) {
      val text = getQuantityString(R.plurals.thread_new_posts, newPostsCount, newPostsCount)
      dismissSnackbar()

      newPostsNotification = SnackbarWrapper.create(
        globalWindowInsetsManager,
        themeEngine.chanTheme,
        this,
        text,
        Snackbar.LENGTH_LONG
      ).apply {
        setAction(R.string.thread_new_posts_goto) {
          presenter.onNewPostsViewClicked()
          dismissSnackbar()
        }

        show()
      }
    }
  }

  private fun dismissSnackbar() {
    newPostsNotification?.dismiss()
    newPostsNotification = null
  }

  override fun onDetachedFromWindow() {
    dismissSnackbar()
    super.onDetachedFromWindow()
  }

  override fun showImageReencodingWindow(
    fileUuid: UUID,
    chanDescriptor: ChanDescriptor,
    supportsReencode: Boolean
  ) {
    if (this.focusedChild != null) {
      val currentFocus = this.focusedChild
      AndroidUtils.hideKeyboard(currentFocus)
      currentFocus.clearFocus()
    }

    globalWindowInsetsManager.runWhenKeyboardIsHidden {
      imageReencodingHelper.showController(fileUuid, chanDescriptor, supportsReencode)
    }
  }

  fun isReplyLayoutOpen(): Boolean = threadListLayout.replyOpen

  fun getThumbnail(postImage: ChanPostImage?): ThumbnailView? {
    return if (postPopupHelper.isOpen) {
      postPopupHelper.getThumbnail(postImage)
    } else {
      threadListLayout.getThumbnail(postImage)
    }
  }

  fun openReply(open: Boolean) {
    openReplyInternal(open)
  }

  override fun showReplyButton(show: Boolean) {
    if (replyButton.isFabVisible() == show || !replyButtonEnabled) {
      return
    }

    if (show) {
      replyButton.show()
    } else {
      replyButton.hide()
    }
  }

  private fun switchVisible(visible: Visible) {
    if (this.visible == visible) {
      return
    }

    if (this.visible != null) {
      if (this.visible == Visible.THREAD) {
        threadListLayout.cleanup()
        postPopupHelper.popAll()

        showReplyButton(false)
        dismissSnackbar()
      }
    }

    this.visible = visible
    this.replyButton.setThreadVisibilityState(visible == Visible.THREAD)

    when (visible) {
      Visible.EMPTY -> {
        loadView.setView(inflateEmptyView())
        showReplyButton(false)
      }
      Visible.LOADING -> {
        val view = loadView.setView(progressLayout)

        if (refreshedFromSwipe) {
          refreshedFromSwipe = false
          view.visibility = View.GONE
        } else {
          view.visibility = View.VISIBLE
        }

        showReplyButton(false)
        progressStepText.text = ""
      }
      Visible.THREAD -> {
        callback.hideSwipeRefreshLayout()
        loadView.setView(threadListLayout)
        showReplyButton(true)
      }
      Visible.ERROR -> {
        callback.hideSwipeRefreshLayout()
        loadView.setView(errorLayout)
        showReplyButton(false)
      }
    }
  }

  @SuppressLint("InflateParams")
  private fun inflateEmptyView(): View {
    val view = inflate(context, R.layout.layout_empty_setup, null)
    val tv = view.findViewById<TextView>(R.id.feature)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      // This unicode symbol crashes app on APIs below 23
      tv.setText(R.string.thread_empty_setup_feature)
    }

    return view
  }

  override fun presentRepliesController(controller: Controller) {
    callback.presentController(controller, true)
  }

  override fun presentReencodeOptionsController(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    callback.presentController(controller, true)
  }

  override fun onImageOptionsComplete() {
    BackgroundUtils.ensureMainThread()
    threadListLayout.onImageOptionsComplete()
  }

  override fun presentRemovedPostsController(controller: Controller) {
    callback.presentController(controller, true)
  }

  @Suppress("MoveLambdaOutsideParentheses")
  override fun showHideOrRemoveWholeChainDialog(hide: Boolean, post: ChanPost, threadNo: Long) {
    val positiveButtonText = if (hide) {
      getString(R.string.thread_layout_hide_whole_chain)
    } else {
      getString(R.string.thread_layout_remove_whole_chain)
    }

    val negativeButtonText = if (hide) {
      getString(R.string.thread_layout_hide_post)
    } else {
      getString(R.string.thread_layout_remove_post)
    }

    val message = if (hide) {
      getString(R.string.thread_layout_hide_whole_chain_as_well)
    } else {
      getString(R.string.thread_layout_remove_whole_chain_as_well)
    }

    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleText = message,
      negativeButtonText = negativeButtonText,
      onNegativeButtonClickListener = { presenter.hideOrRemovePosts(hide, false, post, threadNo) },
      positiveButtonText = positiveButtonText,
      onPositiveButtonClickListener = { presenter.hideOrRemovePosts(hide, true, post, threadNo) }
    )
  }

  private fun handleLoadProgressEvent(chanLoadProgressEvent: ChanLoadProgressEvent) {
    Logger.d(TAG, "handleLoadProgressEvent() $chanLoadProgressEvent")

    progressStepText.text = when (chanLoadProgressEvent) {
      is ChanLoadProgressEvent.Begin -> {
        getString(R.string.thread_layout_load_progress_preparing)
      }
      is ChanLoadProgressEvent.LoadingJson -> {
        getString(R.string.thread_layout_load_progress_requesting_json)
      }
      is ChanLoadProgressEvent.ReadingJson -> {
        getString(R.string.thread_layout_load_progress_reading_json_response)
      }
      is ChanLoadProgressEvent.ProcessingFilters -> {
        getString(R.string.thread_layout_load_progress_processing_filters, chanLoadProgressEvent.filtersCount)
      }
      is ChanLoadProgressEvent.ParsingPosts -> {
        getString(R.string.thread_layout_load_progress_parsing_posts, chanLoadProgressEvent.postsToParseCount)
      }
      is ChanLoadProgressEvent.PersistingPosts -> {
        getString(R.string.thread_layout_load_progress_persisting_posts, chanLoadProgressEvent.postsCount)
      }
      is ChanLoadProgressEvent.ApplyingFilters -> {
        getString(
          R.string.thread_layout_load_progress_applying_filters,
          chanLoadProgressEvent.postHidesCount,
          chanLoadProgressEvent.postFiltersCount
        )
      }
      is ChanLoadProgressEvent.RefreshingPosts -> {
        getString(R.string.thread_layout_load_progress_diffing_results)
      }
      is ChanLoadProgressEvent.End -> {
        getString(R.string.thread_layout_load_progress_done)
      }
    }
  }

  interface ThreadLayoutCallback {
    val toolbar: Toolbar?

    suspend fun showThread(descriptor: ChanDescriptor.ThreadDescriptor, animated: Boolean)
    suspend fun showPostsInExternalThread(postDescriptor: PostDescriptor, isPreviewingCatalogThread: Boolean)
    suspend fun openExternalThread(postDescriptor: PostDescriptor)
    suspend fun showBoard(descriptor: BoardDescriptor, animated: Boolean)
    suspend fun setBoard(descriptor: BoardDescriptor, animated: Boolean)

    fun showImages(chanDescriptor: ChanDescriptor, initialImageUrl: String?, transitionThumbnailUrl: String)
    fun showAlbum(initialImageUrl: HttpUrl?)
    fun onShowPosts()
    fun onShowError()
    fun presentController(controller: Controller, animated: Boolean)
    fun unpresentController(predicate: (Controller) -> Boolean)
    fun isAlreadyPresentingController(predicate: (Controller) -> Boolean): Boolean
    fun openReportController(post: ChanPost)
    fun hideSwipeRefreshLayout()
    fun openFilterForType(type: FilterType, filterText: String?)
    fun openFiltersController(chanFilterMutable: ChanFilterMutable)
    fun threadBackPressed(): Boolean
    fun threadBackLongPressed()
    fun showAvailableArchivesList(postDescriptor: PostDescriptor, preview: Boolean)
  }

  companion object {
    const val TAG = "ThreadLayout"

    private const val SCROLL_TO_BOTTOM_DEBOUNCE_TIMEOUT_MS = 150L
    private const val OPEN_REPLY_DELAY_MS = 100L
  }
}