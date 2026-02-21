package com.github.k1rakishou.chan.ui.layout

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.BoardPostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.concurrency.Debouncer
import com.github.k1rakishou.chan.core.concurrency.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressEvent
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter.ThreadPresenterCallback
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.loader.ChanLoaderException
import com.github.k1rakishou.chan.features.create_sound_media.CreateSoundMediaController
import com.github.k1rakishou.chan.features.reencoding.ImageOptionsHelper
import com.github.k1rakishou.chan.features.reencoding.ImageOptionsHelper.ImageReencodingHelperCallback
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarState
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.compose.ThreadSearchNavigationButtonsView
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarCollection
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarContainerView
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarManager
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.compose.snackbar.asSnackbarId
import com.github.k1rakishou.chan.ui.compose.snackbar.manager.SnackbarManagerFactory
import com.github.k1rakishou.chan.ui.compose.snackbar.snackbarButton
import com.github.k1rakishou.chan.ui.compose.snackbar.snackbarText
import com.github.k1rakishou.chan.ui.controller.PostLinksController
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper.PostPopupHelperCallback
import com.github.k1rakishou.chan.ui.helper.RemovedPostsHelper
import com.github.k1rakishou.chan.ui.helper.RemovedPostsHelper.RemovedPostsCallbacks
import com.github.k1rakishou.chan.ui.layout.ThreadListLayout.ThreadListLayoutCallback
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.ui.view.HidingFloatingActionButton
import com.github.k1rakishou.chan.ui.view.LoadView
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getQuantityString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.awaitUntilGloballyLaidOutAndGetSize
import com.github.k1rakishou.chan.utils.setBackgroundColorFast
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.filter.FilterType
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostHide
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.persist_state.IndexAndTop
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
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
  ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var presenter: ThreadPresenter
  @Inject
  lateinit var themeEngineLazy: Lazy<ThemeEngine>
  @Inject
  lateinit var postFilterManagerLazy: Lazy<PostFilterManager>
  @Inject
  lateinit var siteManagerLazy: Lazy<SiteManager>
  @Inject
  lateinit var postHideManagerLazy: Lazy<PostHideManager>
  @Inject
  lateinit var archivesManagerLazy: Lazy<ArchivesManager>
  @Inject
  lateinit var dialogFactoryLazy: Lazy<DialogFactory>
  @Inject
  lateinit var chanThreadManagerLazy: Lazy<ChanThreadManager>
  @Inject
  lateinit var globalWindowInsetsManagerLazy: Lazy<GlobalWindowInsetsManager>
  @Inject
  lateinit var chanLoadProgressNotifierLazy: Lazy<ChanLoadProgressNotifier>
  @Inject
  lateinit var globalUiStateHolderLazy: Lazy<GlobalUiStateHolder>
  @Inject
  lateinit var snackbarManagerFactoryLazy: Lazy<SnackbarManagerFactory>
  @Inject
  lateinit var appResourcesLazy: Lazy<AppResources>

  private val themeEngine: ThemeEngine
    get() = themeEngineLazy.get()
  private val postFilterManager: PostFilterManager
    get() = postFilterManagerLazy.get()
  private val siteManager: SiteManager
    get() = siteManagerLazy.get()
  private val postHideManager: PostHideManager
    get() = postHideManagerLazy.get()
  private val archivesManager: ArchivesManager
    get() = archivesManagerLazy.get()
  private val dialogFactory: DialogFactory
    get() = dialogFactoryLazy.get()
  private val chanThreadManager: ChanThreadManager
    get() = chanThreadManagerLazy.get()
  private val globalWindowInsetsManager: GlobalWindowInsetsManager
    get() = globalWindowInsetsManagerLazy.get()
  private val chanLoadProgressNotifier: ChanLoadProgressNotifier
    get() = chanLoadProgressNotifierLazy.get()
  private val globalUiStateHolder: GlobalUiStateHolder
    get() = globalUiStateHolderLazy.get()
  private val snackbarManagerFactory: SnackbarManagerFactory
    get() = snackbarManagerFactoryLazy.get()
  private val appResources: AppResources
    get() = appResourcesLazy.get()

  private lateinit var callback: ThreadLayoutCallback
  private lateinit var progressLayout: View
  private lateinit var progressStepText: ColorizableTextView
  private lateinit var loadView: LoadView
  private lateinit var replyButton: HidingFloatingActionButton
  private lateinit var threadSearchNavigationButtonsView: ThreadSearchNavigationButtonsView
  private lateinit var threadListLayout: ThreadListLayout
  private lateinit var errorLayout: LinearLayout
  private lateinit var errorText: TextView
  private lateinit var errorRetryButton: ColorizableButton
  private lateinit var openThreadInArchiveButton: ColorizableButton
  private lateinit var postPopupHelper: PostPopupHelper
  private lateinit var imageReencodingHelper: ImageOptionsHelper
  private lateinit var removedPostsHelper: RemovedPostsHelper
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor
  private lateinit var snackbarManager: SnackbarManager

  var threadControllerType: ThreadControllerType? = null
    private set
  private var controllerKey: ControllerKey? = null
  private var refreshedFromSwipe = false
  private var deletingDialog: ProgressDialog? = null
  private var threadLayoutState: State? = null
  private var searchLinkPopupOpenJob: Job? = null

  private val scrollToBottomDebouncer = Debouncer(false)
  private val job = SupervisorJob()
  private val coroutineScope = CoroutineScope(job + Dispatchers.Main + CoroutineName("ThreadLayout"))
  private val snackbarCollection = SnackbarCollection()

  override val kurobaToolbarState: KurobaToolbarState
    get() = callback.kurobaToolbarState

  override val chanDescriptor: ChanDescriptor?
    get() = presenter.currentChanDescriptor

  override val displayingPostDescriptors: List<PostDescriptor>
    get() = if (postPopupHelper.isOpen) {
      postPopupHelper.getDisplayingPostDescriptors()
    } else {
      threadListLayout.displayingPostDescriptors
    }

  override val displayingPostDescriptorsInThread: List<PostDescriptor>
    get() = threadListLayout.displayingPostDescriptors

  override val currentPosition: IndexAndTop?
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

  fun create(
    callback: ThreadLayoutCallback,
    threadControllerType: ThreadControllerType,
    controllerKey: ControllerKey
  ) {
    this.callback = callback
    this.serializedCoroutineExecutor = SerializedCoroutineExecutor(coroutineScope)
    this.threadControllerType = threadControllerType
    this.controllerKey = controllerKey
    this.snackbarManager = when (threadControllerType) {
      ThreadControllerType.Catalog -> {
        snackbarManagerFactory.snackbarManager(
          SnackbarScope.PostList(layoutAnchor = SnackbarScope.LayoutAnchor.Catalog)
        )
      }
      ThreadControllerType.Thread -> {
        snackbarManagerFactory.snackbarManager(
          SnackbarScope.PostList(layoutAnchor = SnackbarScope.LayoutAnchor.Thread)
        )
      }
    }

    Logger.d(TAG, "ThreadLayout.create(threadControllerType: ${threadControllerType}, controllerKey: ${controllerKey})")

    // View binding
    loadView = findViewById(com.github.k1rakishou.chan.R.id.loadview)
    replyButton = findViewById(com.github.k1rakishou.chan.R.id.reply_button)
    replyButton.setThreadControllerType(threadControllerType, controllerKey)
    replyButton.setSnackbarScope(threadControllerType.asSnackbarScope())


    val snackbarContainerView = findViewById<SnackbarContainerView>(
      com.github.k1rakishou.chan.R.id.snackbar_container_view
    )

    when (threadControllerType) {
      ThreadControllerType.Catalog -> {
        snackbarContainerView.init(
          SnackbarScope.PostList(layoutAnchor = SnackbarScope.LayoutAnchor.Catalog)
        )
      }
      ThreadControllerType.Thread -> {
        snackbarContainerView.init(
          SnackbarScope.PostList(layoutAnchor = SnackbarScope.LayoutAnchor.Thread)
        )
      }
    }

    threadSearchNavigationButtonsView = findViewById(com.github.k1rakishou.chan.R.id.thread_search_navigation_buttons)

    // Inflate ThreadListLayout
    threadListLayout = inflate(context, R.layout.layout_thread_list, this, false) as ThreadListLayout

    // Inflate error layout
    errorLayout = inflate(context, com.github.k1rakishou.chan.R.layout.layout_thread_error, this, false) as LinearLayout
    errorText = errorLayout.findViewById(com.github.k1rakishou.chan.R.id.text)
    errorRetryButton = errorLayout.findViewById(com.github.k1rakishou.chan.R.id.retry_button)
    openThreadInArchiveButton = errorLayout.findViewById(com.github.k1rakishou.chan.R.id.open_in_archive_button)

    // Inflate thread loading layout
    progressLayout = inflate(context, com.github.k1rakishou.chan.R.layout.layout_thread_progress, this, false)
    progressStepText = progressLayout.findViewById(com.github.k1rakishou.chan.R.id.loading_step)

    // View setup
    presenter.create(context, this)
    threadListLayout.onCreate(presenter, this, threadControllerType)
    postPopupHelper = PostPopupHelper(context, presenter, chanThreadManagerLazy, this)
    imageReencodingHelper = ImageOptionsHelper(context, this)
    removedPostsHelper = RemovedPostsHelper(context, presenter, this)
    errorText.typeface = themeEngine.chanTheme.mainFont
    errorRetryButton.setOnClickListener(this)
    openThreadInArchiveButton.setOnClickListener(this)

    replyButton.setOnClickListener(this)

    coroutineScope.launch {
      chanLoadProgressNotifier.progressEventsFlow
        .filter { threadLayoutState == ThreadLayout.State.LOADING }
        .sample(16)
        .collect { chanLoadProgressEvent ->
          if (chanDescriptor != chanLoadProgressEvent.chanDescriptor) {
            return@collect
          }

          handleLoadProgressEvent(chanLoadProgressEvent)
        }
    }

    coroutineScope.launch {
      presenter.currentChanDescriptorFlow
        .onEach { currentChanDescriptor ->
          threadSearchNavigationButtonsView.updateParameters(
            parameters = ThreadSearchNavigationButtonsView.Parameters(
              chanDescriptor = currentChanDescriptor,
              controllerKey = controllerKey
            )
          )
        }
        .collect()
    }

    themeEngine.addListener(this)
  }

  fun destroy() {
    Logger.d(TAG, "ThreadLayout.destroy(threadControllerType=$threadControllerType)")

    threadControllerType = null

    themeEngine.removeListener(this)
    presenter.unbindChanDescriptor(true)
    threadListLayout.onDestroy()
    job.cancelChildren()
  }

  override fun onThemeChanged() {
    setBackgroundColorFast(themeEngine.chanTheme.backColor)

    if (!presenter.isBound) {
      return
    }

    presenter.quickReloadFromMemoryCache()
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
      if (presenter.currentChanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
        showToast(context, R.string.post_posting_is_not_supported_composite_catalog)
      } else {
        showToast(context, R.string.post_posting_is_not_supported)
      }

      return false
    }

    if (openReplyLayout) {
      threadListLayout.openReplyLayout()
    } else {
      threadListLayout.closeReplyLayout()
    }

    return true
  }

  private fun canOpenReplyLayout(): Boolean {
    val chanDescriptor = presenter.currentChanDescriptor

    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return false
    }

    val supportsPosting = chanDescriptor?.siteDescriptor()?.let { siteDescriptor ->
      return@let siteManager.bySiteDescriptorAndActive(siteDescriptor)?.siteFeature(Site.SiteFeature.POSTING)
    } ?: false

    if (!supportsPosting) {
      return false
    }

    return true
  }

  fun onPickLocalMediaButtonClicked() {
    threadListLayout.onPickLocalMediaButtonClicked()
  }

  fun onPickRemoteMediaButtonClicked() {
    threadListLayout.onPickRemoteMediaButtonClicked()
  }

  fun onSearchRemoteMediaButtonClicked(chanDescriptor: ChanDescriptor) {
    threadListLayout.onSearchRemoteMediaButtonClicked(chanDescriptor)
  }

  fun canChildScrollUp(): Boolean {
    if (threadLayoutState != State.CONTENT) {
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
    presenter.normalLoad(
      showLoading = true,
      chanCacheUpdateOptions = ChanCacheUpdateOptions.UpdateCache
    )
  }

  fun lostFocus(wasFocused: ThreadControllerType) {
    threadListLayout.lostFocus(wasFocused)
  }

  fun gainedFocus(nowFocused: ThreadControllerType) {
    threadListLayout.gainedFocus(nowFocused, threadLayoutState == State.CONTENT)
  }

  fun onShown(nowFocused: ThreadControllerType) {
    threadListLayout.onShown(nowFocused, threadLayoutState == State.CONTENT)
  }

  fun onHidden(nowFocused: ThreadControllerType) {
    threadListLayout.onHidden(nowFocused, threadLayoutState == State.CONTENT)
  }

  fun setBoardPostViewMode(boardPostViewMode: BoardPostViewMode) {
    threadListLayout.setBoardPostViewMode(boardPostViewMode)
  }

  fun showThreadSearchNavigationButtonsView() {
    threadSearchNavigationButtonsView.show()
  }

  fun hideThreadSearchNavigationButtonsView() {
    threadSearchNavigationButtonsView.hide()
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
    val alreadyPresenting = callback.isAlreadyPresentingController { c -> c.javaClass == controller.javaClass }
    if (alreadyPresenting) {
      return
    }

    callback.presentController(controller, animated = false)
  }

  override fun pushController(controller: Controller) {
    callback.pushController(controller)
  }

  override fun unpresentController(predicate: (Controller) -> Boolean) {
    callback.unpresentController(predicate)
  }

  override suspend fun showPostsForChanDescriptor(
    descriptor: ChanDescriptor?,
    filter: PostsFilter,
    refreshPostPopupHelperPosts: Boolean,
    additionalPostsToReparse: MutableSet<PostDescriptor>
  ) {
    if (descriptor == null) {
      Logger.d(TAG, "showPostsForChanDescriptor() descriptor==null")
      return
    }

    val initial = threadLayoutState != State.CONTENT
    val (width, _) = loadView.awaitUntilGloballyLaidOutAndGetSize(waitForWidth = true)

    if (refreshPostPopupHelperPosts && postPopupHelper.isOpen) {
      postPopupHelper.updateAllPosts(descriptor)
    }

    val (showPostsResult, totalDuration) = measureTimedValue {
      threadListLayout.showPosts(width, descriptor, filter, initial, additionalPostsToReparse)
    }

    val applyFilterDuration = showPostsResult.applyFilterDuration
    val setThreadPostsDuration = showPostsResult.setThreadPostsDuration

    Logger.d(TAG, "showPostsForChanDescriptor() showPosts($descriptor) -> ${showPostsResult.result} " +
      "applyFilterDuration=${applyFilterDuration}, setThreadPostsDuration=${setThreadPostsDuration}, " +
      "totalDuration=${totalDuration}")

    if (!showPostsResult.result) {
      switchThreadLayoutState(State.EMPTY)
      return
    }

    switchThreadLayoutState(State.CONTENT)
    callback.onShowPosts(descriptor)
  }

  override fun postClicked(postDescriptor: PostDescriptor) {
    if (postPopupHelper.isOpen) {
      postPopupHelper.postClicked(postDescriptor)
    }
  }

  override fun hideError(chanDescriptor: ChanDescriptor) {
    threadListLayout.showError(null)
  }

  override fun showError(chanDescriptor: ChanDescriptor, error: ChanLoaderException) {
    if (hasSupportedActiveArchives() && !error.isFirewallError()) {
      openThreadInArchiveButton.setVisibilityFast(View.VISIBLE)
    } else {
      openThreadInArchiveButton.setVisibilityFast(View.GONE)
    }

    val errorMessage = error.errorMessage

    if (threadLayoutState == State.CONTENT) {
      threadListLayout.showError(errorMessage)
    } else {
      switchThreadLayoutState(State.ERROR)
      errorText.text = errorMessage

      if (error.isRecoverableError()) {
        errorRetryButton.setVisibilityFast(View.VISIBLE)
      } else {
        errorRetryButton.setVisibilityFast(View.GONE)
      }
    }

    callback.onShowError()
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
    showLoading(animateTransition = true)
  }

  override fun showLoading(animateTransition: Boolean) {
    callback.onShowLoading()
    switchThreadLayoutState(State.LOADING, animateTransition = animateTransition)
  }

  override fun showEmpty() {
    switchThreadLayoutState(State.EMPTY)
  }

  override fun showPostInfo(info: String) {
    dialogFactory.createSimpleInformationDialog(
      context = context,
      titleText = getString(R.string.post_info_title),
      descriptionText = info
    )
  }

  override fun showPostLinkables(post: ChanPost, inPopup: Boolean) {
    val linkables = post.postComment.linkables
      .filter { postLinkable -> postLinkable.type == PostLinkable.Type.LINK }

    if (linkables.isEmpty()) {
      showToast(context, context.getString(R.string.no_links_found))
      return
    }

    val postLinksController = PostLinksController(
      post = post,
      onPostLinkClicked = { postLinkable -> presenter.onPostLinkableClicked(post, postLinkable, inPopup) },
      context = context
    )

    callback.presentController(postLinksController, animated = true)
  }

  override fun clipboardPost(post: ChanPost) {
    AndroidUtils.setClipboardContent("Post text", post.postComment.comment().toString())
    showToast(context, R.string.post_text_copied)
  }

  override fun openLink(link: String) {
    val extension = MimeTypeMap.getFileExtensionFromUrl(link)
    if (extension.isNotEmpty()) {
      val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
      if (mimeType != null && (mimeType.startsWith("image") || mimeType.startsWith("video"))) {
        openMediaLinkInternal(link)
        return
      }
    }

    openRegularLinkInternal(link)
  }

  private fun openRegularLinkInternal(link: String) {
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

  private fun openMediaLinkInternal(link: String) {
    if (!ChanSettings.openLinkConfirmation.get()) {
      callback.openMediaLinkInMediaViewer(link)
      return
    }

    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.open_link_in_internal_media_viewer,
      descriptionTextId = R.string.open_link_in_internal_media_viewer_descriptor,
      positiveButtonText = getString(R.string.yes),
      onPositiveButtonClickListener = { callback.openMediaLinkInMediaViewer(link) },
      negativeButtonText = getString(R.string.no),
      onNegativeButtonClickListener = { AppModuleAndroidUtils.openLink(link) },
      neutralButtonText = getString(R.string.cancel)
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

  override suspend fun openExternalThread(postDescriptor: PostDescriptor, scrollToPost: Boolean) {
    Logger.d(TAG, "openExternalThread($postDescriptor, $scrollToPost)")

    callback.openExternalThread(postDescriptor, scrollToPost)
  }

  override suspend fun showCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean) {
    Logger.d(TAG, "showCatalog($catalogDescriptor, $animated)")

    callback.showCatalog(catalogDescriptor, animated)
  }

  override suspend fun setCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean) {
    Logger.d(TAG, "showCatalog($catalogDescriptor, $animated)")

    callback.setCatalog(catalogDescriptor, animated)
  }

  override suspend fun setCatalogWithSearchQuery(
    catalogDescriptor: ChanDescriptor.ICatalogDescriptor,
    searchQuery: String,
    animated: Boolean
  ) {
    Logger.d(TAG, "setCatalogWithSearchQuery($catalogDescriptor, $searchQuery, $animated)")

    searchLinkPopupOpenJob?.cancel()
    searchLinkPopupOpenJob = null

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      searchLinkPopupOpenJob = coroutineScope {
        launch {
          delay(500L)

          if (!isActive) {
            return@launch
          }

          postPopupHelper.showSearchPopup(catalogDescriptor as ChanDescriptor, searchQuery)
          searchLinkPopupOpenJob = null
        }
      }
    } else {
      postPopupHelper.showSearchPopup(catalogDescriptor as ChanDescriptor, searchQuery)
    }

    callback.setCatalog(catalogDescriptor, animated)
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

  override fun scrollTo(displayPosition: Int) {
    if (postPopupHelper.isOpen) {
      postPopupHelper.scrollTo(displayPosition)
    } else if (threadLayoutState == State.CONTENT) {
      threadListLayout.scrollTo(displayPosition)
    }
  }

  override fun smoothScrollNewPosts(displayPosition: Int) {
    threadListLayout.smoothScrollNewPosts(displayPosition)
  }

  override fun filterPostTripcode(tripcode: CharSequence) {
    callback.openFilterForType(
      type = FilterType.TRIPCODE,
      filterText = tripcode.toString(),
      caseSensitive = false
    )
  }

  override fun filterPostName(posterName: CharSequence) {
    callback.openFilterForType(
      type = FilterType.NAME,
      filterText = posterName.toString(),
      caseSensitive = false
    )
  }

  override fun filterPosterId(postererId: String) {
    callback.openFilterForType(
      type = FilterType.ID,
      filterText = postererId,
      caseSensitive = false
    )
  }

  override fun quote(post: ChanPost, withText: Boolean) {
    if (!canOpenReplyLayout()) {
      showToast(context, R.string.post_posting_is_not_supported)
      return
    }

    postDelayed({
      openReplyInternal(true)
      threadListLayout.replyLayoutViewCallbacks.quote(post, withText)
    }, OPEN_REPLY_DELAY_MS)
  }

  override fun quote(postDescriptor: PostDescriptor, text: CharSequence) {
    if (!canOpenReplyLayout()) {
      showToast(context, R.string.post_posting_is_not_supported)
      return
    }

    postDelayed({
      openReplyInternal(true)
      threadListLayout.replyLayoutViewCallbacks.quote(postDescriptor, text)
    }, OPEN_REPLY_DELAY_MS)
  }

  override fun confirmPostDelete(post: ChanPost) {
    val view = inflate(context, R.layout.dialog_post_delete, null)
    val checkBox = view.findViewById<CheckBox>(com.github.k1rakishou.chan.R.id.image_only)

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
  override fun hideThread(post: ChanPost, hide: Boolean) {
    serializedCoroutineExecutor.post {
      // hideRepliesToThisPost is false here because we don't have posts in the catalog mode so there
      // is no point in hiding replies to a thread
      val postHide = ChanPostHide(
        filterInfo = null,
        postDescriptor = post.postDescriptor,
        onlyHide = hide,
        applyToWholeThread = true,
        applyToReplies = false,
        manuallyRestored = false
      )

      postHideManager.create(postHide)
      presenter.refreshUI()

      snackbarCollection += snackbarManager.snackbar(
        snackbarId = "hide_thread".asSnackbarId(),
        text = snackbarText(
          text = appResources.string(
            if (hide) {
              R.string.thread_hidden
            } else {
              R.string.thread_removed
            }
          )
        ),
        button = snackbarButton<Unit>(
          coroutineScope = coroutineScope,
          text = appResources.string(R.string.undo),
          onClick = {
            serializedCoroutineExecutor.post {
              postFilterManager.remove(post.postDescriptor)
              postHideManager.remove(postHide.postDescriptor)
              presenter.refreshUI()
            }
          }
        )
      )
    }
  }

  override fun hideOrRemovePosts(
    hide: Boolean,
    wholeChain: Boolean,
    postDescriptors: Set<PostDescriptor>
  ) {
    serializedCoroutineExecutor.post {
      val hideList = mutableListOf<ChanPostHide>()
      val resultPostDescriptors = mutableListOf<PostDescriptor>()

      for (postDescriptor in postDescriptors) {
        // Do not add the OP post to the hideList since we don't want to hide an OP post
        // while being in a thread (it just doesn't make any sense)
        if (postDescriptor.isOP()) {
          continue
        }

        hideList += ChanPostHide(
          filterInfo = null,
          postDescriptor = postDescriptor,
          onlyHide = hide,
          applyToWholeThread = false,
          applyToReplies = wholeChain,
          manuallyRestored = false
        )

        resultPostDescriptors += postDescriptor
      }

      postHideManager.createOrUpdateMany(hideList)
      presenter.reparsePostsWithReplies(resultPostDescriptors)

      val formattedString = if (hide) {
        getQuantityString(R.plurals.post_hidden, postDescriptors.size, postDescriptors.size)
      } else {
        getQuantityString(R.plurals.post_removed, postDescriptors.size, postDescriptors.size)
      }

      snackbarCollection += snackbarManager.snackbar(
        snackbarId = "hide_or_remove_posts".asSnackbarId(),
        text = snackbarText(
          text = formattedString
        ),
        button = snackbarButton<Unit>(
          coroutineScope = coroutineScope,
          text = appResources.string(R.string.undo),
          onClick = {
            serializedCoroutineExecutor.post {
              presenter.reparsePostsWithReplies(resultPostDescriptors) { totalPostsWithReplies ->
                postFilterManager.removeMany(totalPostsWithReplies)
                postHideManager.removeManyChanPostHides(totalPostsWithReplies)
              }
            }
          }
        )
      )
    }
  }

  override fun unhideOrUnremovePost(post: ChanPost) {
    serializedCoroutineExecutor.post {
      presenter.reparsePostsWithReplies(listOf(post.postDescriptor)) { totalPostsWithReplies ->
        postFilterManager.removeMany(totalPostsWithReplies)

        postHideManager.update(
          postDescriptor = post.postDescriptor,
          updater = { postDescriptor, oldChanPostHide ->
            if (oldChanPostHide == null) {
              return@update ChanPostHide(
                filterInfo = null,
                postDescriptor = postDescriptor,
                onlyHide = true,
                applyToWholeThread = false,
                applyToReplies = false,
                manuallyRestored = true
              )
            }

            return@update oldChanPostHide.copy(
              filterInfo = null,
              manuallyRestored = true
            )
          }
        )
      }
    }
  }

  override fun viewHiddenOrRemovedPosts(chanDescriptor: ChanDescriptor) {
    removedPostsHelper.showPosts(chanDescriptor)
  }

  override fun onRestoreRemovedPostsClicked(
    chanDescriptor: ChanDescriptor,
    selectedPosts: List<PostDescriptor>
  ) {
    serializedCoroutineExecutor.post {
      presenter.reparsePostsWithReplies(selectedPosts) { totalPostsWithReplies ->
        postFilterManager.removeMany(totalPostsWithReplies)
        postHideManager.removeManyChanPostHides(selectedPosts)
      }

      snackbarCollection += snackbarManager.snackbar(
        snackbarId = "restore_removed_posts".asSnackbarId(),
        text = snackbarText(
          text = appResources.string(R.string.restored_n_posts, selectedPosts.size)
        )
      )
    }
  }

  override suspend fun onPostsUpdated(updatedPosts: List<ChanPost>) {
    BackgroundUtils.ensureMainThread()

    if (postPopupHelper.isOpen) {
      postPopupHelper.onPostsUpdated(updatedPosts)
    }

    threadListLayout.onPostsUpdated(updatedPosts)
  }

  override fun isAlreadyPresentingController(predicate: (Controller) -> Boolean): Boolean {
    return callback.isAlreadyPresentingController(predicate)
  }

  override fun presentController(
    controller: Controller,
    animate: Boolean
  ) {
    callback.presentController(controller, animate)
  }

  override fun showToolbar() {
    scrollToBottomDebouncer.post(SCROLL_TO_BOTTOM_DEBOUNCE_TIMEOUT_MS) { kurobaToolbarState.showToolbar() }
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
    if (postPopupHelper.isOpen) {
      postPopupHelper.popAll()
    }

    callback.openFiltersController(chanFilterMutable)
  }

  override fun showNewPostsNotification(show: Boolean, newPostsCount: Int, deletedPostsCount: Int) {
    val snackbarId = "show_new_posts".asSnackbarId()

    if (!show || !canShowSnackBar()) {
      snackbarManager.dismissSnackbar(snackbarId)
      return
    }

    coroutineScope.launch {
      // Wait for the posts to appear and do nothing if it failed
      val success = awaitUntilThreadLayoutState(desiredState = ThreadLayout.State.CONTENT, maxWaitTimeMillis = 1000L)
      if (!success) {
        return@launch
      }

      // Wait a little bit more, so that last seen indicator is processed
      delay(250L)

      val ticksCounter = presenter.ticksCounter()
      if (ticksCounter <= 1 && threadListLayout.isLastSeenIndicatorVisible()) {
        // This is the first thread load and last seen indicator is within the viewport (visible). We don't want to show
        // the "XXX new posts" snackbar in this case.
        snackbarManager.dismissSnackbar(snackbarId)
        return@launch
      }

      val text = when {
        newPostsCount <= 0 && deletedPostsCount <= 0 -> return@launch
        newPostsCount > 0 && deletedPostsCount <= 0 -> {
          getQuantityString(R.plurals.thread_new_posts, newPostsCount, newPostsCount)
        }
        newPostsCount <= 0 && deletedPostsCount > 0 -> {
          getQuantityString(R.plurals.thread_deleted_posts, deletedPostsCount, deletedPostsCount)
        }
        else -> {
          val newPosts = getQuantityString(R.plurals.thread_new_posts, newPostsCount, newPostsCount)
          val deletedPosts = getQuantityString(R.plurals.thread_deleted_posts, deletedPostsCount, deletedPostsCount)

          "${newPosts}, ${deletedPosts}"
        }
      }

      snackbarCollection += snackbarManager.snackbar(
        snackbarId = snackbarId,
        text = snackbarText(text),
        button = snackbarButton<Unit>(
          coroutineScope = coroutineScope,
          text = appResources.string(R.string.thread_new_posts_goto),
          // Show action only if we are showing new posts and we are not already at the bottom of the thread
          show = !threadListLayout.scrolledToBottom() && newPostsCount > 0,
          onClick = { presenter.onNewPostsViewClicked() }
        )
      )
    }
  }

  override fun showThreadStatusNotification(
    show: Boolean,
    nowSticky: Boolean?,
    nowArchived: Boolean?,
    nowDeleted: Boolean?,
    nowClosed: Boolean?
  ) {
    val snackbarId = "show_thread_status_notification".asSnackbarId()

    if (!show || !canShowSnackBar()) {
      snackbarManager.dismissSnackbar(snackbarId)
      return
    }

    val text = getThreadStatusText(nowSticky, nowDeleted, nowClosed, nowArchived)
    if (text.isNullOrEmpty()) {
      return
    }

    snackbarCollection += snackbarManager.snackbar(
      snackbarId = snackbarId,
      text = snackbarText(text)
    )
  }

  override fun canShowSnackBar(): Boolean {
    val descriptor = presenter.currentChanDescriptor
      ?: return false

    if (!BackgroundUtils.isInForeground()) {
      return false
    }

    val isReplyLayoutVisible = when (descriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        globalUiStateHolder.replyLayout.state(ThreadControllerType.Thread).isOpenedOrExpanded()
      }
      is ChanDescriptor.CompositeCatalogDescriptor,
      is ChanDescriptor.CatalogDescriptor -> {
        globalUiStateHolder.replyLayout.state(ThreadControllerType.Catalog).isOpenedOrExpanded()
      }
    }

    if (isReplyLayoutVisible) {
      return false
    }

    return true
  }

  override fun showImageReencodingWindow(
    fileUuid: UUID,
    chanDescriptor: ChanDescriptor,
    supportsReencode: Boolean
  ) {
    globalWindowInsetsManager.runWhenKeyboardIsHidden(focusedChild) {
      imageReencodingHelper.showController(fileUuid, chanDescriptor, supportsReencode)
    }
  }

  fun isReplyLayoutOpen(): Boolean = threadListLayout.isReplyLayoutOpened()

  fun getThumbnail(postImage: ChanPostImage?): ThumbnailView? {
    return if (postPopupHelper.isOpen) {
      postPopupHelper.getThumbnail(postImage)
    } else {
      threadListLayout.getThumbnail(postImage)
    }
  }

  fun openOrCloseReply(open: Boolean) {
    openReplyInternal(open)
  }

  override fun showCaptchaController(
    chanDescriptor: ChanDescriptor,
    replyMode: ReplyMode,
    autoReply: Boolean,
    afterPostingAttempt: Boolean,
    onFinished: ((Boolean) -> Unit)?
  ) {
    threadListLayout.showCaptcha(chanDescriptor, replyMode, autoReply, afterPostingAttempt, onFinished)
  }

  override suspend fun CoroutineScope.awaitUntilThreadLayoutState(
    desiredState: State,
    maxWaitTimeMillis: Long
  ): Boolean {
    val maxTime = System.currentTimeMillis() + maxWaitTimeMillis

    while (threadLayoutState != desiredState) {
      awaitFrame()

      if (!isActive || System.currentTimeMillis() > maxTime) {
        return false
      }
    }

    return true
  }

  private fun switchThreadLayoutState(newState: State, animateTransition: Boolean = true) {
    val currentState = threadLayoutState
    if (currentState == newState) {
      return
    }

    if (currentState != null && currentState == State.CONTENT) {
      threadListLayout.cleanup()
      postPopupHelper.popAll()

      snackbarCollection.dismissAll(snackbarManager)
    }

    if (newState != State.CONTENT) {
      threadListLayout.showToolbarIfNeeded()
    }

    threadLayoutState = newState

    threadControllerType?.let { controllerType ->
      globalUiStateHolder.updateThreadLayoutState {
        updateThreadLayoutState(controllerType, newState)
      }
    }

    callback.onThreadLayoutStateChanged(newState)

    when (newState) {
      State.EMPTY -> {
        loadView.setView(inflateEmptyView(), animateTransition)
      }
      State.LOADING -> {
        val view = loadView.setView(progressLayout, animateTransition)

        if (refreshedFromSwipe) {
          refreshedFromSwipe = false
          view.visibility = View.GONE
        } else {
          view.visibility = View.VISIBLE
        }

        progressStepText.text = ""
      }
      State.CONTENT -> {
        callback.hideSwipeRefreshLayout()
        loadView.setView(threadListLayout, animateTransition)
      }
      State.ERROR -> {
        callback.hideSwipeRefreshLayout()
        loadView.setView(errorLayout, animateTransition)
      }
    }
  }

  @SuppressLint("InflateParams")
  private fun inflateEmptyView(): View {
    val view = inflate(context, R.layout.layout_empty_setup, null)
    val tv = view.findViewById<TextView>(com.github.k1rakishou.chan.R.id.feature)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      // This unicode symbol crashes app on APIs below 23
      tv.setText(R.string.thread_empty_setup_feature)
    }

    return view
  }

  override fun presentRepliesController(controller: Controller) {
    callback.presentController(controller, true)
  }

  override fun highlightPost(postDescriptor: PostDescriptor?, blink: Boolean) {
    threadListLayout.highlightPost(postDescriptor, blink)
  }

  override fun scrollToPost(postDescriptor: PostDescriptor) {
    presenter.scrollToPost(postDescriptor)
  }

  override fun presentReencodeOptionsController(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    callback.presentController(controller, true)
  }

  override fun onImageOptionsApplied(fileUuid: UUID) {
    BackgroundUtils.ensureMainThread()
    threadListLayout.onImageOptionsApplied(fileUuid)
  }

  override fun pushCreateSoundMediaController(controller: CreateSoundMediaController) {
    threadListLayout.pushController(controller)
  }

  override fun presentRemovedPostsController(controller: Controller) {
    callback.presentController(controller, true)
  }

  override fun showHideOrRemoveWholeChainDialog(hide: Boolean, hasReplies: Boolean, post: ChanPost) {
    val action = if (hide) {
      getString(R.string.thread_layout_hide_action)
    } else {
      getString(R.string.thread_layout_remove_action)
    }

    val positiveButtonText = if (hasReplies) {
      getString(R.string.thread_layout_hide_or_remove_whole_chain_action, action.capitalize(Locale.ENGLISH))
    } else {
      getString(R.string.thread_layout_hide_or_remove_future_replies_action, action.capitalize(Locale.ENGLISH))
    }

    val negativeButtonText = if (hide) {
      getString(R.string.thread_layout_hide_post)
    } else {
      getString(R.string.thread_layout_remove_post)
    }

    val message = if (hasReplies) {
      getString(R.string.thread_layout_hide_or_remove_whole_chain_as_well, action)
    } else {
      getString(R.string.thread_layout_hide_or_remove_future_replies, action)
    }

    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleText = message,
      negativeButtonText = negativeButtonText,
      onNegativeButtonClickListener = { presenter.hideOrRemovePosts(hide, false, post) },
      positiveButtonText = positiveButtonText,
      onPositiveButtonClickListener = { presenter.hideOrRemovePosts(hide, true, post) }
    )
  }

  private fun handleLoadProgressEvent(chanLoadProgressEvent: ChanLoadProgressEvent) {
    Logger.verbose(TAG) { "handleLoadProgressEvent() $chanLoadProgressEvent" }

    progressStepText.text = when (chanLoadProgressEvent) {
      is ChanLoadProgressEvent.Begin -> {
        appResources.string(R.string.thread_layout_load_progress_preparing)
      }
      is ChanLoadProgressEvent.Loading -> {
        appResources.string(R.string.thread_layout_load_progress_requesting_server_data)
      }
      is ChanLoadProgressEvent.Reading -> {
        if (chanLoadProgressEvent.totalPostsRead > 0) {
          appResources.string(
            R.string.thread_layout_load_progress_reading_data_response_posts,
            chanLoadProgressEvent.totalPostsRead
          )
        } else {
          appResources.string(R.string.thread_layout_load_progress_reading_data_response)
        }
      }
      is ChanLoadProgressEvent.ProcessingFilters -> {
        if (chanLoadProgressEvent.processedPosts > 0 && chanLoadProgressEvent.totalPosts > 0) {
          appResources.string(
            R.string.thread_layout_load_progress_processing_filters_posts,
            chanLoadProgressEvent.filtersCount,
            chanLoadProgressEvent.processedPosts,
            chanLoadProgressEvent.totalPosts
          )
        } else {
          appResources.string(
            R.string.thread_layout_load_progress_processing_filters,
            chanLoadProgressEvent.filtersCount
          )
        }
      }
      is ChanLoadProgressEvent.ParsingPosts -> {
        if (chanLoadProgressEvent.parsedPosts > 0) {
          appResources.string(
            R.string.thread_layout_load_progress_parsing_posts_posts,
            chanLoadProgressEvent.parsedPosts,
            chanLoadProgressEvent.totalPosts
          )
        } else {
          appResources.string(R.string.thread_layout_load_progress_parsing_posts, chanLoadProgressEvent.totalPosts)
        }
      }
      is ChanLoadProgressEvent.PersistingPosts -> {
        appResources.string(R.string.thread_layout_load_progress_persisting_posts, chanLoadProgressEvent.postsCount)
      }
      is ChanLoadProgressEvent.ApplyingFilters -> {
        if (chanLoadProgressEvent.processedPosts > 0) {
          appResources.string(
            R.string.thread_layout_load_progress_applying_filters_posts,
            chanLoadProgressEvent.processedPosts,
            chanLoadProgressEvent.totalPosts,
            chanLoadProgressEvent.postHidesCount,
            chanLoadProgressEvent.postFiltersCount
          )
        } else {
          appResources.string(
            R.string.thread_layout_load_progress_applying_filters,
            chanLoadProgressEvent.postHidesCount,
            chanLoadProgressEvent.postFiltersCount
          )
        }
      }
      is ChanLoadProgressEvent.RefreshingPosts -> {
        if (chanLoadProgressEvent.processedPosts > 0) {
          appResources.string(
            R.string.thread_layout_load_progress_diffing_results_posts,
            chanLoadProgressEvent.processedPosts,
            chanLoadProgressEvent.totalPosts
          )
        } else {
          appResources.string(R.string.thread_layout_load_progress_diffing_results)
        }
      }
      is ChanLoadProgressEvent.End -> {
        appResources.string(R.string.thread_layout_load_progress_done)
      }
    }
  }

  private fun getThreadStatusText(
    nowSticky: Boolean?,
    nowDeleted: Boolean?,
    nowClosed: Boolean?,
    nowArchived: Boolean?
  ): String? {
    when {
      nowSticky != null && nowSticky -> {
        if (nowSticky) {
          return getString(R.string.thread_is_now_pinned)
        } else {
          return getString(R.string.thread_is_no_longer_pinned)
        }
      }
      nowDeleted == true || nowClosed == true || nowArchived == true -> {
        return buildString {
          append(getString(R.string.thread_is_now_template))
          append(" ")

          var addSeparator = false

          if (nowDeleted == true) {
            append(getString(R.string.thread_deleted))
            addSeparator = true
          }

          if (nowClosed == true) {
            if (addSeparator) {
              append(" ")
              append(getString(R.string.thread_is_now_template_separator))
              append(" ")
            }

            append(getString(R.string.thread_closed))
            addSeparator = true
          }

          if (nowArchived == true) {
            if (addSeparator) {
              append(" ")
              append(getString(R.string.thread_is_now_template_separator))
              append(" ")
            }

            append(getString(R.string.thread_archived))
          }
        }
      }
      else -> {
        return null
      }
    }
  }

  enum class State {
    EMPTY,
    LOADING,
    CONTENT,
    ERROR;

    fun isNotInContentState(): Boolean = this != CONTENT
  }

  interface ThreadLayoutCallback {
    val kurobaToolbarState: KurobaToolbarState
    val threadControllerType: ThreadControllerType

    suspend fun showThread(descriptor: ChanDescriptor.ThreadDescriptor, animated: Boolean)
    suspend fun showThreadWithoutFocusing(descriptor: ChanDescriptor.ThreadDescriptor, animated: Boolean)
    suspend fun showPostsInExternalThread(postDescriptor: PostDescriptor, isPreviewingCatalogThread: Boolean)
    suspend fun openExternalThread(postDescriptor: PostDescriptor, scrollToPost: Boolean)
    suspend fun showCatalogWithoutFocusing(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean)
    suspend fun showCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean)
    suspend fun setCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean)

    fun pushController(controller: Controller)
    fun openMediaLinkInMediaViewer(link: String)
    fun showImages(chanDescriptor: ChanDescriptor, initialImageUrl: String?, transitionThumbnailUrl: String)
    fun showAlbum(initialImageUrl: HttpUrl?)
    fun onShowLoading()
    fun onShowPosts(chanDescriptor: ChanDescriptor)
    fun onShowError()
    fun presentController(controller: Controller, animated: Boolean)
    fun unpresentController(predicate: (Controller) -> Boolean)
    fun isAlreadyPresentingController(predicate: (Controller) -> Boolean): Boolean
    fun openReportController(post: ChanPost)
    fun hideSwipeRefreshLayout()
    fun openFilterForType(type: FilterType, filterText: String, caseSensitive: Boolean = true)
    fun openFiltersController(chanFilterMutable: ChanFilterMutable)
    fun threadBackPressed(): Boolean
    fun threadBackLongPressed()
    fun onThreadLayoutStateChanged(state: State)

    fun showAvailableArchivesList(
      postDescriptor: PostDescriptor,
      preview: Boolean,
      canAutoSelectArchive: Boolean = true
    )
  }

  companion object {
    const val TAG = "ThreadLayout"

    private const val SCROLL_TO_BOTTOM_DEBOUNCE_TIMEOUT_MS = 150L
    private const val OPEN_REPLY_DELAY_MS = 100L
  }
}