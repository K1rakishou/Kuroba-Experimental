package com.github.k1rakishou.chan.features.media_viewer

import android.content.Context
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.viewpager.widget.ViewPager
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaViewContract
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayoutNoBackground
import com.github.k1rakishou.chan.ui.view.AppearTransitionImageView
import com.github.k1rakishou.chan.ui.view.DisappearTransitionImageView
import com.github.k1rakishou.chan.ui.view.OptionalSwipeViewPager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.awaitSilently
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

class MediaViewerController(
  context: Context,
  private val mediaViewerCallbacks: MediaViewerCallbacks
) : Controller(context), ViewPager.OnPageChangeListener, MediaViewContract {
  private lateinit var mediaViewerRootLayout: TouchBlockingFrameLayoutNoBackground
  private lateinit var appearPreviewImage: AppearTransitionImageView
  private lateinit var disappearPreviewImage: DisappearTransitionImageView
  private lateinit var pager: OptionalSwipeViewPager

  private val viewModel by (context as ComponentActivity).viewModels<MediaViewerControllerViewModel>()
  private val transitionAnimationShown = CompletableDeferred<Unit>()

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2

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
    pager.offscreenPageLimit = 2 // TODO(KurobaEx): move into a separate setting

    mainScope.launch(context = Dispatchers.Main.immediate) {
      viewModel.transitionInfoFlow.collect { transitionInfo ->
        if (transitionAnimationShown.isCompleted) {
          return@collect
        }

        runAppearAnimation(transitionInfo)
        transitionAnimationShown.complete(Unit)
      }
    }

    mainScope.launch {
      viewModel.mediaViewerState.collect { mediaViewerState ->
        if (mediaViewerState == null) {
          return@collect
        }

        transitionAnimationShown.awaitSilently(Unit)

        pager.setVisibilityFast(View.INVISIBLE)
        appearPreviewImage.setVisibilityFast(View.INVISIBLE)

        pager.setSwipingEnabled(true)
        pager.adapter = MediaViewerAdapter(context, this@MediaViewerController, mediaViewerState.loadedMedia)
        pager.setCurrentItem(mediaViewerState.initialPagerIndex, false)

        Logger.d(TAG, "Loaded ${mediaViewerState.loadedMedia.size} media items, " +
          "initialPagerIndex=${mediaViewerState.initialPagerIndex}")

        // TODO(KurobaEx): Get rid of image flickering by waiting until the same thumbnail view as the
        //  transition image is fully loaded (only thumbnail is fine no need to wait until the full
        //  image/video/gif is loaded) and only then switch the visibility. Right now we don't wait
        //  and make it visible right away BUT at this point the thumbnail is not loaded yet so
        //  for a split second the pager will be empty after that second it will actually load the
        //  thumbnail image (from the cache). This is what causes flickering.
        pager.setVisibilityFast(View.VISIBLE)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    pager.removeOnPageChangeListener(this)
    (pager.adapter as? MediaViewerAdapter)?.onDestroy()
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

  override fun onTapped() {
    mediaViewerCallbacks.toggleFullScreenMode()
  }

  fun updateToolbarVisibility(systemUIHidden: Boolean) {
    // TODO(KurobaEx):
  }

  suspend fun runAppearAnimation(transitionInfo: ViewableMediaParcelableHolder.TransitionInfo?) {
    if (transitionInfo == null) {
      mediaViewerRootLayout.setBackgroundColor(BACKGROUND_COLOR)
      return
    }

    val resultBitmap = imageLoaderV2.loadFromNetworkSuspend(
      context,
      transitionInfo.transitionThumbnailUrl,
      ImageLoaderV2.ImageSize.MeasurableImageSize.create(appearPreviewImage)
    )

    if (resultBitmap == null) {
      return
    }

    appearPreviewImage.setWindowLocation(transitionInfo.lastTouchPosX, transitionInfo.lastTouchPosY)
    appearPreviewImage.setBitmap(resultBitmap)

    suspendCancellableCoroutine<Unit> { continuation ->
      appearPreviewImage.runAppearAnimation(
        view,
        BACKGROUND_COLOR,
        { currentBackgroundColor -> mediaViewerRootLayout.setBackgroundColor(currentBackgroundColor) },
        { continuation.resume(Unit) }
      )
    }
  }

  interface MediaViewerCallbacks {
    fun toggleFullScreenMode()
  }

  companion object {
    private const val TAG = "MediaViewerController"
    private const val BACKGROUND_COLOR = 0xDD000000L.toInt()
  }
}