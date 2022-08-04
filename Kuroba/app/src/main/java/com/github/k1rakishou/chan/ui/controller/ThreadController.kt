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
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ApplicationVisibility
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityListener
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadFollowHistoryManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.features.filters.FiltersController
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerActivity
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerOptions
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerOpenAlbumHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerScrollerHelper
import com.github.k1rakishou.chan.features.report.Chan4ReportPostController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController.SlideChangeListener
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.helper.AppSettingsUpdateAppRefreshHelper
import com.github.k1rakishou.chan.ui.helper.OpenExternalThreadHelper
import com.github.k1rakishou.chan.ui.helper.ShowPostsInExternalThreadHelper
import com.github.k1rakishou.chan.ui.layout.ThreadLayout
import com.github.k1rakishou.chan.ui.layout.ThreadLayout.ThreadLayoutCallback
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.widget.KurobaSwipeRefreshLayout
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ArchiveDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.filter.FilterType
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import javax.inject.Inject

abstract class ThreadController(
  context: Context,
  val mainControllerCallbacks: MainControllerCallbacks
) : Controller(context),
  ThreadLayoutCallback,
  OnRefreshListener,
  SlideChangeListener,
  ApplicationVisibilityListener,
  ThemeEngine.ThemeChangesListener,
  Toolbar.ToolbarHeightUpdatesCallback,
  AlbumViewController.ThreadControllerCallbacks {

  @Inject
  lateinit var _siteManager: Lazy<SiteManager>
  @Inject
  lateinit var _themeEngine: Lazy<ThemeEngine>
  @Inject
  lateinit var _applicationVisibilityManager: Lazy<ApplicationVisibilityManager>
  @Inject
  lateinit var _chanThreadManager: Lazy<ChanThreadManager>
  @Inject
  lateinit var _threadFollowHistoryManager: Lazy<ThreadFollowHistoryManager>
  @Inject
  lateinit var _archivesManager: Lazy<ArchivesManager>
  @Inject
  lateinit var _globalWindowInsetsManager: Lazy<GlobalWindowInsetsManager>
  @Inject
  lateinit var _chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>
  @Inject
  lateinit var _mediaViewerScrollerHelper: Lazy<MediaViewerScrollerHelper>
  @Inject
  lateinit var _mediaViewerOpenAlbumHelper: Lazy<MediaViewerOpenAlbumHelper>
  @Inject
  lateinit var _appSettingsUpdateAppRefreshHelper: Lazy<AppSettingsUpdateAppRefreshHelper>
  @Inject
  lateinit var _dialogFactory: Lazy<DialogFactory>
  @Inject
  lateinit var _currentOpenedDescriptorStateManager: Lazy<CurrentOpenedDescriptorStateManager>

  protected val siteManager: SiteManager
    get() = _siteManager.get()
  protected val themeEngine: ThemeEngine
    get() = _themeEngine.get()
  protected val chanThreadViewableInfoManager: ChanThreadViewableInfoManager
    get() = _chanThreadViewableInfoManager.get()
  protected val archivesManager: ArchivesManager
    get() = _archivesManager.get()
  protected val dialogFactory: DialogFactory
    get() = _dialogFactory.get()
  protected val chanThreadManager: ChanThreadManager
    get() = _chanThreadManager.get()
  protected val threadFollowHistoryManager: ThreadFollowHistoryManager
    get() = _threadFollowHistoryManager.get()
  protected val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
    get() = _currentOpenedDescriptorStateManager.get()

  private val applicationVisibilityManager: ApplicationVisibilityManager
    get() = _applicationVisibilityManager.get()
  private val globalWindowInsetsManager: GlobalWindowInsetsManager
    get() = _globalWindowInsetsManager.get()
  private val mediaViewerScrollerHelper: MediaViewerScrollerHelper
    get() = _mediaViewerScrollerHelper.get()
  private val mediaViewerOpenAlbumHelper: MediaViewerOpenAlbumHelper
    get() = _mediaViewerOpenAlbumHelper.get()
  private val appSettingsUpdateAppRefreshHelper: AppSettingsUpdateAppRefreshHelper
    get() = _appSettingsUpdateAppRefreshHelper.get()

  protected lateinit var threadLayout: ThreadLayout
  protected lateinit var showPostsInExternalThreadHelper: ShowPostsInExternalThreadHelper
  protected lateinit var openExternalThreadHelper: OpenExternalThreadHelper

  private lateinit var swipeRefreshLayout: KurobaSwipeRefreshLayout
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  val chanDescriptor: ChanDescriptor?
    get() = threadLayout.presenter.currentChanDescriptor

  override val toolbar: Toolbar?
    get() = (navigationController as? ToolbarNavigationController)?.toolbar

  abstract val threadControllerType: ThreadSlideController.ThreadControllerType

  override fun onCreate() {
    super.onCreate()

    threadLayout = inflate(context, R.layout.layout_thread, null) as ThreadLayout
    threadLayout.create(this, threadControllerType, mainControllerCallbacks.navigationViewContractType)
    threadLayout.setDrawerCallbacks(mainControllerCallbacks)

    swipeRefreshLayout = KurobaSwipeRefreshLayout(context)

    swipeRefreshLayout.setOnChildScrollUpCallback { parent, child ->
      return@setOnChildScrollUpCallback threadLayout.canChildScrollUp()
    }

    swipeRefreshLayout.id = R.id.swipe_refresh_layout
    swipeRefreshLayout.addView(threadLayout)
    swipeRefreshLayout.setOnRefreshListener(this)

    view = swipeRefreshLayout

    serializedCoroutineExecutor = SerializedCoroutineExecutor(mainScope)
    applicationVisibilityManager.addListener(this)

    toolbar?.addToolbarHeightUpdatesCallback(this)

    showPostsInExternalThreadHelper = ShowPostsInExternalThreadHelper(
      context = context,
      scope = mainScope,
      postPopupHelper = threadLayout.popupHelper,
      _chanThreadManager = _chanThreadManager,
      presentControllerFunc = { controller -> presentController(controller) },
      showAvailableArchivesListFunc = { postDescriptor, canAutoSelectArchive ->
        showAvailableArchivesList(
          postDescriptor = postDescriptor,
          preview = true,
          canAutoSelectArchive = canAutoSelectArchive
        )
      },
      showToastFunc = { message -> showToast(message) }
    )

    openExternalThreadHelper = OpenExternalThreadHelper(
      postPopupHelper = threadLayout.popupHelper,
      _chanThreadViewableInfoManager = _chanThreadViewableInfoManager,
      _threadFollowHistoryManager = _threadFollowHistoryManager
    )

    mainScope.launch {
      mediaViewerScrollerHelper.mediaViewerScrollEventsFlow
        .collect { scrollToImageEvent ->
          val descriptor = scrollToImageEvent.chanDescriptor
          if (descriptor != chanDescriptor) {
            return@collect
          }

          threadLayout.presenter.scrollToImage(scrollToImageEvent.chanPostImage, true)
        }
    }

    mainScope.launch {
      mediaViewerOpenAlbumHelper.mediaViewerOpenAlbumEventsFlow
        .collect { openAlbumEvent ->
          val descriptor = openAlbumEvent.chanDescriptor
          if (descriptor != chanDescriptor) {
            return@collect
          }

          showAlbum(openAlbumEvent.chanPostImage.imageUrl, threadLayout.displayingPostDescriptors)
        }
    }

    mainScope.launch {
      appSettingsUpdateAppRefreshHelper.settingsUpdatedEvent.collect {
        Logger.d(TAG, "Reloading thread because app settings were updated")
        threadLayout.presenter.quickReloadFromMemoryCache()
      }
    }

    onThemeChanged()
    themeEngine.addListener(this)
  }

  override fun onShow() {
    super.onShow()

    threadLayout.onShown(threadControllerType)
  }

  override fun onHide() {
    super.onHide()

    threadLayout.onHidden(threadControllerType)
  }

  override fun onDestroy() {
    super.onDestroy()

    toolbar?.removeToolbarHeightUpdatesCallback(this)
    threadLayout.destroy()
    applicationVisibilityManager.removeListener(this)
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
  }

  override fun onToolbarHeightKnown(heightChanged: Boolean) {
    val toolbarHeight = toolbar?.toolbarHeight
      ?: return

    swipeRefreshLayout.setProgressViewOffset(
      false,
      toolbarHeight - dp(40f),
      toolbarHeight + dp(64 - 40.toFloat())
    )
  }

  fun passMotionEventIntoDrawer(event: MotionEvent): Boolean {
    return mainControllerCallbacks.passMotionEventIntoDrawer(event)
  }

  fun passMotionEventIntoSlidingPaneLayout(event: MotionEvent): Boolean {
    val threadSlideController = (this.parentController as? ThreadSlideController)
      ?: return false

    return threadSlideController.passMotionEventIntoSlidingPaneLayout(event)
  }

  fun showLoading(animateTransition: Boolean = false) {
    threadLayout.showLoading(animateTransition = animateTransition)
  }

  open suspend fun showSitesNotSetup() {
    threadLayout.presenter.showNoContent()
  }

  fun highlightPost(postDescriptor: PostDescriptor?, blink: Boolean) {
    threadLayout.presenter.highlightPost(postDescriptor, blink)
  }

  override fun onBack(): Boolean {
    return threadLayout.onBack()
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    return threadLayout.sendKeyEvent(event) || super.dispatchKeyEvent(event)
  }

  override fun onApplicationVisibilityChanged(applicationVisibility: ApplicationVisibility) {
    threadLayout.presenter.onForegroundChanged(applicationVisibility.isInForeground())
  }

  override fun onRefresh() {
    threadLayout.refreshFromSwipe()
  }

  override fun openReportController(post: ChanPost) {
    val site = siteManager.bySiteDescriptor(post.boardDescriptor.siteDescriptor)
    if (site == null || navigationController == null) {
      return
    }

    if (site.siteDescriptor().is4chan()) {
      val chan4ReportPostController = Chan4ReportPostController(
        context = context,
        postDescriptor = post.postDescriptor,
        onCaptchaRequired = {
          val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(post.boardDescriptor, 1L)

          threadLayout.showCaptchaController(
            chanDescriptor = threadDescriptor,
            replyMode = ReplyMode.ReplyModeSendWithoutCaptcha,
            autoReply = false,
            afterPostingAttempt = true
          )
        },
        onOpenInWebView = {
          openWebViewReportController(post, site)
        }
      )

      requireNavController().presentController(chan4ReportPostController)
      return
    } else if (site.siteDescriptor().isDvach()) {
      dialogFactory.createSimpleDialogWithInput(
        context = context,
        titleText = getString(R.string.dvach_report_post_title, post.postDescriptor.userReadableString()),
        descriptionText = getString(R.string.dvach_report_post_description),
        inputType = DialogFactory.DialogInputType.String,
        onValueEntered = { reason -> threadLayout.presenter.processDvachPostReport(reason, post, site) }
      )
      return
    }

    openWebViewReportController(post, site)
  }

  private fun openWebViewReportController(post: ChanPost, site: Site) {
    val toolbarHeight = toolbar?.toolbarHeight
      ?: return

    if (!site.siteFeature(Site.SiteFeature.POST_REPORT)) {
      return
    }

    if (site.endpoints().report(post) == null) {
      return
    }

    val reportController = WebViewReportController(context, post, site, toolbarHeight)
    requireNavController().pushController(reportController)
  }

  fun selectPostImage(postImage: ChanPostImage) {
    threadLayout.presenter.selectPostImage(postImage)
  }

  override fun openMediaLinkInMediaViewer(link: String) {
    Logger.d(TAG, "openMediaLinkInMediaViewer($link)")

    MediaViewerActivity.mixedMedia(
      context = context,
      mixedMedia = listOf(MediaLocation.Remote(link))
    )
  }

  override fun showImages(
    chanDescriptor: ChanDescriptor,
    initialImageUrl: String?,
    transitionThumbnailUrl: String
  ) {
    when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        MediaViewerActivity.catalogMedia(
          context = context,
          catalogDescriptor = chanDescriptor,
          initialImageUrl = initialImageUrl,
          transitionThumbnailUrl = transitionThumbnailUrl,
          lastTouchCoordinates = globalWindowInsetsManager.lastTouchCoordinates(),
          mediaViewerOptions = MediaViewerOptions(
            mediaViewerOpenedFromAlbum = false
          )
        )
      }
      is ChanDescriptor.ThreadDescriptor -> {
        MediaViewerActivity.threadMedia(
          context = context,
          threadDescriptor = chanDescriptor,
          postDescriptorList = threadLayout.displayingPostDescriptors,
          initialImageUrl = initialImageUrl,
          transitionThumbnailUrl = transitionThumbnailUrl,
          lastTouchCoordinates = globalWindowInsetsManager.lastTouchCoordinates(),
          mediaViewerOptions = MediaViewerOptions(
            mediaViewerOpenedFromAlbum = false
          )
        )
      }
    }
  }

  override fun showAlbum(initialImageUrl: HttpUrl?, displayingPostDescriptors: List<PostDescriptor>) {
    val descriptor = chanDescriptor
      ?: return

    val albumViewController = AlbumViewController(context, descriptor, displayingPostDescriptors)
    if (!albumViewController.tryCollectingImages(initialImageUrl)) {
      return
    }

    pushController(albumViewController)
  }

  override fun pushController(controller: Controller) {
    if (doubleNavigationController != null) {
      doubleNavigationController!!.pushController(controller)
    } else {
      navigationController!!.pushController(controller)
    }
  }

  override fun onShowPosts() {
    // no-op
  }

  override fun onShowError() {
    // no-op
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

  override fun isAlreadyPresentingController(predicate: (Controller) -> Boolean): Boolean {
    return super.isAlreadyPresenting(predicate)
  }

  override fun hideSwipeRefreshLayout() {
    if (!::swipeRefreshLayout.isInitialized) {
      return
    }

    swipeRefreshLayout.isRefreshing = false
  }

  override fun openFilterForType(type: FilterType, filterText: String, caseSensitive: Boolean) {
    val caseInsensitiveFlag = if (caseSensitive) {
      ""
    } else {
      "i"
    }

    val filter = ChanFilterMutable()
    filter.type = type.flag
    filter.pattern = "/${filterText.trim()}/$caseInsensitiveFlag"
    openFiltersController(filter)
  }

  override fun openFiltersController(chanFilterMutable: ChanFilterMutable) {
    if (chanDescriptor != null) {
      chanFilterMutable.boards.add(chanDescriptor!!.boardDescriptor())
    }

    val filtersController = FiltersController(
      context = context,
      chanFilterMutable = chanFilterMutable,
      mainControllerCallbacks = mainControllerCallbacks
    )

    if (doubleNavigationController != null) {
      doubleNavigationController!!.openControllerWrappedIntoBottomNavAwareController(filtersController)
    } else {
      requireStartActivity().openControllerWrappedIntoBottomNavAwareController(filtersController)
    }

    requireStartActivity().setSettingsMenuItemSelected()
  }

  override fun onLostFocus(wasFocused: ThreadSlideController.ThreadControllerType) {
    if (isDevBuild()) {
      check(wasFocused == threadControllerType) {
        "ThreadControllerTypes do not match! wasFocused=$wasFocused, current=$threadControllerType"
      }
    }

    threadLayout.lostFocus(wasFocused)
    controllerNavigationManager.onControllerSwipedFrom(this)
  }

  override fun onGainedFocus(nowFocused: ThreadSlideController.ThreadControllerType) {
    if (isDevBuild()) {
      check(nowFocused == threadControllerType) {
        "ThreadControllerTypes do not match! nowFocused=$nowFocused, current=$threadControllerType"
      }
    }

    threadLayout.gainedFocus(nowFocused)
    controllerNavigationManager.onControllerSwipedTo(this)
  }

  override fun threadBackPressed(): Boolean {
    return false
  }

  override fun threadBackLongPressed() {
    // no-op
  }

  override fun showAvailableArchivesList(
    postDescriptor: PostDescriptor,
    preview: Boolean,
    canAutoSelectArchive: Boolean
  ) {
    Logger.d(TAG, "showAvailableArchivesList($postDescriptor, $preview, $canAutoSelectArchive)")

    val descriptor = postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    val supportedArchiveDescriptors = archivesManager.getSupportedArchiveDescriptors(descriptor)
      .filter { archiveDescriptor ->
        return@filter siteManager.bySiteDescriptor(archiveDescriptor.siteDescriptor)?.enabled()
          ?: false
      }

    if (supportedArchiveDescriptors.isEmpty()) {
      Logger.d(TAG, "showAvailableArchives($descriptor) supportedThreadDescriptors is empty")

      val message = AppModuleAndroidUtils.getString(
        R.string.thread_presenter_no_archives_found_to_open_thread,
        descriptor.toString()
      )
      showToast(message, Toast.LENGTH_LONG)
      return
    }

    if (canAutoSelectArchive && supportedArchiveDescriptors.size == 1) {
      mainScope.launch { onArchiveSelected(supportedArchiveDescriptors.first(), postDescriptor, preview) }
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

          onArchiveSelected(archiveDescriptor, postDescriptor, preview)
        }
      }
    )

    presentController(floatingListMenuController)
  }

  private suspend fun onArchiveSelected(
    archiveDescriptor: ArchiveDescriptor,
    postDescriptor: PostDescriptor,
    preview: Boolean
  ) {
    val externalArchivePostDescriptor = PostDescriptor.create(
      archiveDescriptor.domain,
      postDescriptor.descriptor.boardCode(),
      postDescriptor.getThreadNo(),
      postDescriptor.postNo
    )

    if (preview) {
      showPostsInExternalThread(
        postDescriptor = externalArchivePostDescriptor,
        isPreviewingCatalogThread = false
      )
    } else {
      openExternalThread(
        postDescriptor = externalArchivePostDescriptor,
        scrollToPost = true
      )
    }
  }

  data class ShowThreadOptions(
    val switchToThreadController: Boolean,
    val pushControllerWithAnimation: Boolean
  )

  data class ShowCatalogOptions(
    val switchToCatalogController: Boolean,
    val withAnimation: Boolean
  )

  companion object {
    private const val TAG = "ThreadController"
  }
}