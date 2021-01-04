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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.ApplicationVisibility
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityListener
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.LocalSearchManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.usecase.FilterOutHiddenImagesUseCase
import com.github.k1rakishou.chan.features.drawer.DrawerCallbacks
import com.github.k1rakishou.chan.ui.controller.ImageViewerController.ImageViewerCallback
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController.SlideChangeListener
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController.ToolbarSearchCallback
import com.github.k1rakishou.chan.ui.helper.RefreshUIMessage
import com.github.k1rakishou.chan.ui.layout.ThreadLayout
import com.github.k1rakishou.chan.ui.layout.ThreadLayout.ThreadLayoutCallback
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.filter.FilterType
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import javax.inject.Inject

abstract class ThreadController(
  context: Context,
  var drawerCallbacks: DrawerCallbacks?
) : Controller(context),
  ThreadLayoutCallback,
  ImageViewerCallback,
  OnRefreshListener,
  ToolbarSearchCallback,
  SlideChangeListener,
  ApplicationVisibilityListener,
  ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var localSearchManager: LocalSearchManager
  @Inject
  lateinit var applicationVisibilityManager: ApplicationVisibilityManager
  @Inject
  lateinit var filterOutHiddenImagesUseCase: FilterOutHiddenImagesUseCase

  protected lateinit var threadLayout: ThreadLayout
  private lateinit var swipeRefreshLayout: SwipeRefreshLayout
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  val chanDescriptor: ChanDescriptor?
    get() = threadLayout.presenter.currentChanDescriptor

  override val toolbar: Toolbar?
    get() = (navigationController as? ToolbarNavigationController)?.toolbar

  abstract val threadControllerType: ThreadControllerType

  override fun onCreate() {
    super.onCreate()

    EventBus.getDefault().register(this)
    navigation.handlesToolbarInset = true

    threadLayout = inflate(context, R.layout.layout_thread, null) as ThreadLayout
    threadLayout.create(this)
    threadLayout.setDrawerCallbacks(drawerCallbacks)

    swipeRefreshLayout = object : SwipeRefreshLayout(context) {
      override fun canChildScrollUp(): Boolean {
        return threadLayout.canChildScrollUp()
      }
    }

    swipeRefreshLayout.id = R.id.swipe_refresh_layout
    swipeRefreshLayout.addView(threadLayout)
    swipeRefreshLayout.setOnRefreshListener(this)

    view = swipeRefreshLayout

    serializedCoroutineExecutor = SerializedCoroutineExecutor(mainScope)
    applicationVisibilityManager.addListener(this)

    val toolbar = toolbar
    toolbar?.addToolbarHeightUpdatesCallback {
      val toolbarHeight = toolbar.toolbarHeight

      swipeRefreshLayout.setProgressViewOffset(
        false,
        toolbarHeight - dp(40f),
        toolbarHeight + dp(64 - 40.toFloat())
      )
    }

    onThemeChanged()
    themeEngine.addListener(this)
  }

  override fun onShow() {
    super.onShow()

    threadLayout.gainedFocus(threadControllerType)
  }

  override fun onDestroy() {
    super.onDestroy()

    drawerCallbacks = null
    threadLayout.destroy()
    applicationVisibilityManager.removeListener(this)
    themeEngine.removeListener(this)

    EventBus.getDefault().unregister(this)
  }

  override fun onThemeChanged() {
    swipeRefreshLayout.setBackgroundColor(themeEngine.chanTheme.backColor)
  }

  fun passMotionEventIntoDrawer(event: MotionEvent): Boolean {
    return drawerCallbacks?.passMotionEventIntoDrawer(event) ?: false
  }

  fun showLoading() {
    threadLayout.showLoading()
  }

  open suspend fun showSitesNotSetup() {
    threadLayout.presenter.showNoContent()
  }

  fun selectPost(post: Long) {
    threadLayout.presenter.selectPost(post)
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

  @Subscribe
  fun onEvent(message: RefreshUIMessage?) {
    threadLayout.presenter.normalLoad()
  }

  override fun onRefresh() {
    threadLayout.refreshFromSwipe()
  }

  override fun openReportController(post: ChanPost) {
    val site = siteManager.bySiteDescriptor(post.boardDescriptor.siteDescriptor)
    if (site != null) {
      navigationController!!.pushController(ReportController(context, post, site))
    }
  }

  fun selectPostImage(postImage: ChanPostImage) {
    threadLayout.presenter.selectPostImage(postImage)
  }

  override fun showImages(
    images: List<ChanPostImage>,
    index: Int,
    chanDescriptor: ChanDescriptor,
    thumbnail: ThumbnailView
  ) {
    val isAlreadyPresenting =
      isAlreadyPresenting { controller -> controller is ImageViewerNavigationController }

    // Just ignore the showImages request when the image is not loaded
    if (thumbnail.bitmap != null && !isAlreadyPresenting) {
      val imagerViewer = ImageViewerNavigationController(context)
      presentController(imagerViewer, false)
      imagerViewer.showImages(images, index, chanDescriptor, this)
    }
  }

  override fun getPreviewImageTransitionView(postImage: ChanPostImage): ThumbnailView? {
    return threadLayout.getThumbnail(postImage)
  }

  override fun scrollToImage(postImage: ChanPostImage) {
    threadLayout.presenter.scrollToImage(postImage, true)
  }

  override fun showAlbum(images: List<ChanPostImage>, index: Int) {
    if (threadLayout.presenter.currentChanDescriptor == null) {
      return
    }

    val filteredImages = filterOutHiddenImagesUseCase.execute(images)
    if (filteredImages.isEmpty()) {
      return
    }

    val albumViewController = AlbumViewController(context)
    albumViewController.setImages(chanDescriptor, filteredImages, index, navigation.title)

    if (doubleNavigationController != null) {
      doubleNavigationController!!.pushController(albumViewController)
    } else {
      navigationController!!.pushController(albumViewController)
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

  override fun hideSwipeRefreshLayout() {
    if (!::swipeRefreshLayout.isInitialized) {
      return
    }

    swipeRefreshLayout.isRefreshing = false
  }

  override fun onSearchVisibilityChanged(visible: Boolean) {
    serializedCoroutineExecutor.post {
      threadLayout.presenter.onSearchVisibilityChanged(visible)
    }
  }

  override fun onSearchEntered(entered: String) {
    serializedCoroutineExecutor.post {
      val localSearchType = threadLayout.presenter.currentLocalSearchType
        ?: return@post

      localSearchManager.onSearchEntered(localSearchType, entered)
      threadLayout.presenter.onSearchEntered()
    }
  }

  override fun openFilterForType(type: FilterType, filterText: String?) {
    val filtersController = FiltersController(context)

    if (doubleNavigationController != null) {
      doubleNavigationController!!.openControllerWrappedIntoBottomNavAwareController(filtersController)
    } else {
      requireStartActivity().openControllerWrappedIntoBottomNavAwareController(filtersController)
    }

    requireStartActivity().setSettingsMenuItemSelected()
    val filter = ChanFilterMutable()
    filter.type = type.flag
    filter.pattern = '/'.toString() + (filterText ?: "") + '/'
    filtersController.showFilterDialog(filter)
  }

  override fun onSlideChanged(leftOpen: Boolean) {
    val changedTo = if (leftOpen) {
      ThreadControllerType.Catalog
    } else {
      ThreadControllerType.Thread
    }

    val current = threadControllerType

    if (isDevBuild()) {
      check(changedTo == current) {
        "ThreadControllerTypes do not match! changedTo=$changedTo, current=$current"
      }
    }

    threadLayout.gainedFocus(changedTo)
  }

  override fun threadBackPressed(): Boolean {
    return false
  }

  override fun threadBackLongPressed() {
    // no-op
  }

  override fun showAvailableArchivesList(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    // no-op
  }

  enum class ThreadControllerType {
    Catalog,
    Thread
  }

  companion object {
    private const val TAG = "ThreadController"
  }
}