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
import com.github.k1rakishou.chan.utils.BackgroundUtils
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
        BackgroundUtils.ensureMainThread()

        if (transitionAnimationShown.isCompleted) {
          return@collect
        }

        runAppearAnimation(transitionInfo)
        transitionAnimationShown.complete(Unit)
      }
    }

    mainScope.launch {
      viewModel.mediaViewerState.collect { mediaViewerState ->
        BackgroundUtils.ensureMainThread()

        awaitThumbnailLoadedAndShowViewPager(mediaViewerState)
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

  override fun changeMediaViewerBackgroundAlpha(newAlpha: Float) {
    mediaViewerRootLayout.alpha = newAlpha
  }

  override fun closeMediaViewer() {
    mediaViewerCallbacks.finishActivity()
  }

  override fun onTapped() {
    mediaViewerCallbacks.toggleFullScreenMode()
  }

  fun updateToolbarVisibility(systemUIHidden: Boolean) {
    // TODO(KurobaEx):
  }

  private suspend fun awaitThumbnailLoadedAndShowViewPager(
    mediaViewerState: MediaViewerControllerViewModel.MediaViewerControllerState?
  ) {
    BackgroundUtils.ensureMainThread()

    if (mediaViewerState == null) {
      return
    }

    transitionAnimationShown.awaitSilently(Unit)

    pager.setVisibilityFast(View.INVISIBLE)
    pager.setSwipingEnabled(true)

    val previewThumbnailLocation =
      mediaViewerState.loadedMedia[mediaViewerState.initialPagerIndex].mediaLocation

    val adapter = MediaViewerAdapter(
      context = context,
      mediaViewContract = this@MediaViewerController,
      viewableMediaList = mediaViewerState.loadedMedia,
      previewThumbnailLocation = previewThumbnailLocation
    )

    pager.adapter = adapter
    pager.setCurrentItem(mediaViewerState.initialPagerIndex, false)

    adapter.awaitUntilPreviewThumbnailFullyLoaded()

    pager.setVisibilityFast(View.VISIBLE)
    appearPreviewImage.setVisibilityFast(View.INVISIBLE)

    Logger.d(TAG, "Loaded ${mediaViewerState.loadedMedia.size} media items, " +
      "initialPagerIndex=${mediaViewerState.initialPagerIndex}")
  }

  private suspend fun runAppearAnimation(transitionInfo: ViewableMediaParcelableHolder.TransitionInfo?) {
    BackgroundUtils.ensureMainThread()

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
    fun finishActivity()
    fun toggleFullScreenMode()
  }

  companion object {
    private const val TAG = "MediaViewerController"
    private const val BACKGROUND_COLOR = 0xDD000000L.toInt()
  }
}