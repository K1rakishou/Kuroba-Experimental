package com.github.k1rakishou.chan.features.media_viewer

import android.content.Context
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.viewpager.widget.ViewPager
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayoutNoBackground
import com.github.k1rakishou.chan.ui.view.AppearTransitionImageView
import com.github.k1rakishou.chan.ui.view.DisappearTransitionImageView
import com.github.k1rakishou.chan.ui.view.OptionalSwipeViewPager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MediaViewerController(context: Context) : Controller(context), ViewPager.OnPageChangeListener {
  lateinit var mediaViewerRootLayout: TouchBlockingFrameLayoutNoBackground
  lateinit var appearPreviewImage: AppearTransitionImageView
  lateinit var disappearPreviewImage: DisappearTransitionImageView
  lateinit var pager: OptionalSwipeViewPager

  private val adapter = MediaViewerAdapter(context)
  private val viewModel by (context as ComponentActivity).viewModels<MediaViewerControllerViewModel>()

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_media_viewer)

    mediaViewerRootLayout = view.findViewById(R.id.media_viewer_root_layout)
    appearPreviewImage = view.findViewById(R.id.appear_preview_image)
    disappearPreviewImage = view.findViewById(R.id.disappear_preview_image)
    pager = view.findViewById(R.id.pager)

    pager.addOnPageChangeListener(this)
    pager.setVisibilityFast(View.INVISIBLE)
    pager.offscreenPageLimit = 2 // TODO(KurobaEx): move into a separate setting
    pager.adapter = adapter

    mainScope.launch {
      viewModel.mediaViewerState.collect { mediaViewerState ->
        if (mediaViewerState == null) {
          return@collect
        }

        adapter.setViewableMediaList(mediaViewerState.loadedMedia)

        val prevItemIndex = pager.currentItem
        pager.setCurrentItem(mediaViewerState.initialPagerIndex, false)
        pager.setVisibilityFast(View.VISIBLE)
        pager.setSwipingEnabled(true)

        Logger.d(TAG, "Loaded ${mediaViewerState.loadedMedia.size} media items, initialPagerIndex=${mediaViewerState.initialPagerIndex}")

        if (mediaViewerState.initialPagerIndex == prevItemIndex) {
          // onPageSelected won't be called after calling setCurrentItem with item == pager.mCurItem
          // because it's some kind of an internal optimization to not call onPageSelected for item
          // ids that are the same as the internal mCurItem which is 0 upon pager creation.
          // So we need to call it manually. Otherwise if the user click the very first media
          // in a catalog/thread it won't be shown.
          onPageSelected(mediaViewerState.initialPagerIndex)
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    pager.removeOnPageChangeListener(this)
    adapter.onDestroy()
  }

  override fun onPageSelected(position: Int) {
    pager.adapter?.let { adapter ->
      adapter as MediaViewerAdapter

      adapter.doBind(position)
      viewModel.updateLastViewedIndex(position)
    }
  }

  override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
    // no-op
  }

  override fun onPageScrollStateChanged(state: Int) {
    // no-op
  }

  companion object {
    private const val TAG = "MediaViewerController"
  }
}