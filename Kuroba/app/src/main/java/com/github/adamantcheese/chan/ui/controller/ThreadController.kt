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
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.github.adamantcheese.chan.Chan.ForegroundChangedMessage
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.base.RendezvousCoroutineExecutor
import com.github.adamantcheese.chan.core.manager.SiteManager
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.features.drawer.DrawerCallbacks
import com.github.adamantcheese.chan.ui.controller.ImageViewerController.ImageViewerCallback
import com.github.adamantcheese.chan.ui.controller.ThreadSlideController.SlideChangeListener
import com.github.adamantcheese.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.adamantcheese.chan.ui.controller.navigation.ToolbarNavigationController.ToolbarSearchCallback
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage
import com.github.adamantcheese.chan.ui.layout.ThreadLayout
import com.github.adamantcheese.chan.ui.layout.ThreadLayout.ThreadLayoutCallback
import com.github.adamantcheese.chan.ui.toolbar.Toolbar
import com.github.adamantcheese.chan.ui.view.ThumbnailView
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.filter.ChanFilterMutable
import com.github.adamantcheese.model.data.filter.FilterType
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import javax.inject.Inject

abstract class ThreadController(
  context: Context
) : Controller(context),
  ThreadLayoutCallback,
  ImageViewerCallback,
  OnRefreshListener,
  ToolbarSearchCallback,
  SlideChangeListener {

  @Inject
  lateinit var siteManager: SiteManager

  protected lateinit var threadLayout: ThreadLayout
  private lateinit var swipeRefreshLayout: SwipeRefreshLayout
  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(mainScope)

  var drawerCallbacks: DrawerCallbacks? = null
    set(value) {
      threadLayout.setDrawerCallbacks(value)
      field = value
    }

  val chanDescriptor: ChanDescriptor?
    get() = threadLayout.presenter.chanDescriptor

  override val toolbar: Toolbar?
    get() = (navigationController as? ToolbarNavigationController)?.toolbar


  override fun onCreate() {
    super.onCreate()
    EventBus.getDefault().register(this)
    navigation.handlesToolbarInset = true

    threadLayout = AndroidUtils.inflate(context, R.layout.layout_thread, null) as ThreadLayout
    threadLayout.create(this)

    swipeRefreshLayout = object : SwipeRefreshLayout(context) {
      override fun canChildScrollUp(): Boolean {
        return threadLayout.canChildScrollUp()
      }
    }
    swipeRefreshLayout.setId(R.id.swipe_refresh_layout)
    swipeRefreshLayout.addView(threadLayout)
    swipeRefreshLayout.setOnRefreshListener(this)
    view = swipeRefreshLayout

    val toolbar = toolbar

    toolbar?.addToolbarHeightUpdatesCallback {
      val toolbarHeight = toolbar.toolbarHeight

      swipeRefreshLayout.setProgressViewOffset(
        false,
        toolbarHeight - AndroidUtils.dp(40f),
        toolbarHeight + AndroidUtils.dp(64 - 40.toFloat())
      )
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    drawerCallbacks = null
    threadLayout.destroy()
    EventBus.getDefault().unregister(this)
  }

  fun passMotionEventIntoDrawer(event: MotionEvent?): Boolean {
    return if (drawerCallbacks == null) {
      false
    } else {
      drawerCallbacks!!.passMotionEventIntoDrawer(event!!)
    }
  }

  fun showLoading() {
    threadLayout.showLoading()
  }

  open fun showSitesNotSetup() {
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

  @Subscribe
  fun onEvent(message: ForegroundChangedMessage) {
    rendezvousCoroutineExecutor.post {
      threadLayout.presenter.onForegroundChanged(message.inForeground)
    }
  }

  @Subscribe
  fun onEvent(message: RefreshUIMessage?) {
    threadLayout.presenter.requestData()
  }

  override fun onRefresh() {
    threadLayout.refreshFromSwipe()
  }

  override fun openReportController(post: Post) {
    val site = siteManager.bySiteDescriptor(post.boardDescriptor.siteDescriptor)
    if (site != null) {
      navigationController!!.pushController(ReportController(context, post, site))
    }
  }

  fun selectPostImage(postImage: PostImage?) {
    threadLayout.presenter.selectPostImage(postImage!!)
  }

  override fun showImages(
    images: List<PostImage>,
    index: Int,
    chanDescriptor: ChanDescriptor,
    thumbnail: ThumbnailView
  ) {
    val isAlreadyPresenting = isAlreadyPresenting { controller -> controller is ImageViewerNavigationController }

    // Just ignore the showImages request when the image is not loaded
    if (thumbnail.bitmap != null && !isAlreadyPresenting) {
      val imagerViewer = ImageViewerNavigationController(context)
      presentController(imagerViewer, false)
      imagerViewer.showImages(images, index, chanDescriptor, this)
    }
  }

  override fun getPreviewImageTransitionView(postImage: PostImage): ThumbnailView {
    return threadLayout.getThumbnail(postImage)!!
  }

  override fun scrollToImage(postImage: PostImage) {
    threadLayout.presenter.scrollToImage(postImage, true)
  }

  override fun showAlbum(images: List<PostImage>, index: Int) {
    if (threadLayout.presenter.chanThread != null) {
      val albumViewController = AlbumViewController(context)
      albumViewController.setImages(chanDescriptor, images, index, navigation.title)

      if (doubleNavigationController != null) {
        doubleNavigationController!!.pushController(albumViewController)
      } else {
        navigationController!!.pushController(albumViewController)
      }
    }
  }

  override fun onShowPosts() {
    // no-op
  }

  override fun hideSwipeRefreshLayout() {
    if (!::swipeRefreshLayout.isInitialized) {
      return
    }

    swipeRefreshLayout.isRefreshing = false
  }

  override fun onSearchVisibilityChanged(visible: Boolean) {
    rendezvousCoroutineExecutor.post {
      threadLayout.presenter.onSearchVisibilityChanged(visible)
    }
  }

  override fun onSearchEntered(entered: String) {
    rendezvousCoroutineExecutor.post {
      threadLayout.presenter.onSearchEntered(entered)
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
    threadLayout.gainedFocus()
  }

  override fun threadBackPressed(): Boolean {
    return false
  }

  companion object {
    private const val TAG = "ThreadController"
  }
}