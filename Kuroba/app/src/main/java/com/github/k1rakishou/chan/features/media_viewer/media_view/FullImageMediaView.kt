package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.davemorrissey.labs.subscaleview.ImageSource
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.downloader.CancelableDownload
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.helper.CloseMediaActionHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.FullMediaAppearAnimationHelper
import com.github.k1rakishou.chan.ui.view.CircularChunkedLoadingBar
import com.github.k1rakishou.chan.ui.view.CustomScaleImageView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.awaitCatching
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class FullImageMediaView(
  context: Context,
  initialMediaViewState: FullImageState,
  mediaViewContract: MediaViewContract,
  private val onThumbnailFullyLoadedFunc: () -> Unit,
  private val isSystemUiHidden: () -> Boolean,
  override val viewableMedia: ViewableMedia.Image,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<ViewableMedia.Image, FullImageMediaView.FullImageState>(
  context = context,
  attributeSet = null,
  mediaViewContract = mediaViewContract,
  mediaViewState = initialMediaViewState
) {

  private val movableContainer: FrameLayout
  private val thumbnailMediaView: ThumbnailMediaView
  private val actualImageView: CustomScaleImageView
  private val loadingBar: CircularChunkedLoadingBar

  private val gestureDetector: GestureDetector
  private val gestureDetectorListener: GestureDetectorListener
  private val closeMediaActionHelper: CloseMediaActionHelper

  private var fullImageDeferred = CompletableDeferred<FilePath>()
  private var preloadCancelableDownload: CancelableDownload? = null

  override val hasContent: Boolean
    get() = actualImageView.hasImage()

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_full_image, this)
    setWillNotDraw(false)

    movableContainer = findViewById(R.id.movable_container)
    thumbnailMediaView = findViewById(R.id.thumbnail_media_view)
    actualImageView = findViewById(R.id.actual_image_view)
    loadingBar = findViewById(R.id.loading_bar)

    closeMediaActionHelper = CloseMediaActionHelper(
      context = context,
      themeEngine = themeEngine,
      requestDisallowInterceptTouchEvent = { this.parent.requestDisallowInterceptTouchEvent(true) },
      onAlphaAnimationProgress = { alpha -> mediaViewContract.changeMediaViewerBackgroundAlpha(alpha) },
      movableContainerFunc = { movableContainer },
      invalidateFunc = { invalidate() },
      closeMediaViewer = { mediaViewContract.closeMediaViewer() },
      topPaddingFunc = { toolbarHeight() },
      bottomPaddingFunc = { globalWindowInsetsManager.bottom() },
      topGestureInfo = createGestureAction(isTopGesture = true),
      bottomGestureInfo = createGestureAction(isTopGesture = false)
    )

    gestureDetectorListener = GestureDetectorListener(
      thumbnailMediaView = thumbnailMediaView,
      actualImageView = actualImageView,
      mediaViewContract = mediaViewContract,
      tryPreloadingFunc = {
        val canForcePreload = canPreload(forced = true)

        if (viewableMedia.mediaLocation is MediaLocation.Remote && canForcePreload) {
          scope.launch {
            preloadCancelableDownload = startFullMediaPreloading(
              loadingBar = loadingBar,
              mediaLocationRemote = viewableMedia.mediaLocation,
              fullMediaDeferred = fullImageDeferred,
              onEndFunc = { preloadCancelableDownload = null }
            )
          }

          return@GestureDetectorListener true
        } else if (!canForcePreload) {
          mediaViewContract.onTapped()
          return@GestureDetectorListener true
        }

        return@GestureDetectorListener false
      },
      onMediaLongClick = { mediaViewContract.onMediaLongClick(this, viewableMedia) }
    )

    gestureDetector = GestureDetector(context, gestureDetectorListener)

    thumbnailMediaView.setOnTouchListener { v, event ->
      if (thumbnailMediaView.visibility != View.VISIBLE) {
        return@setOnTouchListener false
      }

      // Always return true for thumbnails because otherwise gestures won't work with thumbnails
      gestureDetector.onTouchEvent(event)
      return@setOnTouchListener true
    }

    actualImageView.setOnTouchListener { v, event ->
      if (actualImageView.visibility != View.VISIBLE) {
        return@setOnTouchListener false
      }

      val result = gestureDetector.onTouchEvent(event)

      // Double-tap zoom conflicts with longtap so we need to check whether we double tapped before
      // invoking the longtap callback and then reset the doubletap flag on cancel or up event.
      if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) {
        gestureDetectorListener.onUpOrCanceled()
      }

      return@setOnTouchListener result
    }
  }

  override fun gestureCanBeExecuted(imageGestureActionType: ChanSettings.ImageGestureActionType): Boolean {
    return actualImageView.imageViewportTouchSide.isTouchingAllSides
  }

  override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
    if (ev != null && closeMediaActionHelper.onInterceptTouchEvent(ev)) {
      return true
    }

    return super.onInterceptTouchEvent(ev)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (closeMediaActionHelper.onTouchEvent(event)) {
      return true
    }

    return super.onTouchEvent(event)
  }

  override fun draw(canvas: Canvas) {
    super.draw(canvas)
    closeMediaActionHelper.onDraw(canvas)
  }

  override fun updateTransparency(backgroundColor: Int?) {
    val actualBackgroundColor = backgroundColor ?: Color.TRANSPARENT
    actualImageView.setTileBackgroundColor(actualBackgroundColor)
  }

  override fun preload() {
    thumbnailMediaView.bind(
      ThumbnailMediaView.ThumbnailMediaViewParameters(
        isOriginalMediaPlayable = false,
        viewableMedia = viewableMedia,
      ),
      onThumbnailFullyLoaded = {
        onThumbnailFullyLoadedFunc()
      }
    )

    if (viewableMedia.mediaLocation is MediaLocation.Remote && canPreload(forced = false)) {
      scope.launch {
        preloadCancelableDownload = startFullMediaPreloading(
          loadingBar = loadingBar,
          mediaLocationRemote = viewableMedia.mediaLocation,
          fullMediaDeferred = fullImageDeferred,
          onEndFunc = { preloadCancelableDownload = null }
        )
      }
    } else if (viewableMedia.mediaLocation is MediaLocation.Local) {
      if (viewableMedia.mediaLocation.isUri) {
        fullImageDeferred.complete(FilePath.UriPath(Uri.parse(viewableMedia.mediaLocation.path)))
      } else {
        fullImageDeferred.complete(FilePath.JavaPath(viewableMedia.mediaLocation.path))
      }
    }
  }

  override fun bind() {

  }

  override fun show(isLifecycleChange: Boolean) {
    mediaViewToolbar?.updateWithViewableMedia(pagerPosition, totalPageItemsCount, viewableMedia)
    onSystemUiVisibilityChanged(isSystemUiHidden())
    onUpdateTransparency()

    if (!hasContent) {
      scope.launch {
        fullImageDeferred.awaitCatching()
          .onFailure { error ->
            Logger.e(TAG, "onFullImageLoadingError()", error)

            if (error.isExceptionImportant() && shown) {
              cancellableToast.showToast(
                context,
                getString(R.string.image_failed_big_image_error, error.errorMessageOrClassName())
              )
            }

            actualImageView.setVisibilityFast(View.INVISIBLE)
          }
          .onSuccess { filePath ->
            setBigImageFromFile(filePath)
          }

        loadingBar.setVisibilityFast(GONE)
      }
    }
  }

  override fun hide(isLifecycleChange: Boolean) {
    actualImageView.resetScaleAndCenter()
  }

  override fun unbind() {
    thumbnailMediaView.unbind()

    if (fullImageDeferred.isActive) {
      fullImageDeferred.cancel()
    }

    preloadCancelableDownload?.cancel()
    preloadCancelableDownload = null

    actualImageView.setCallback(null)
    actualImageView.recycle()

    closeMediaActionHelper.onDestroy()
  }

  override suspend fun reloadMedia() {
    if (preloadCancelableDownload != null) {
      return
    }

    val mediaLocation = viewableMedia.mediaLocation
    if (mediaLocation !is MediaLocation.Remote) {
      return
    }

    cacheHandler.get().deleteCacheFileByUrlSuspend(mediaLocation.url.toString())

    fullImageDeferred.cancel()
    fullImageDeferred = CompletableDeferred<FilePath>()

    thumbnailMediaView.setVisibilityFast(View.VISIBLE)
    actualImageView.setVisibilityFast(View.INVISIBLE)
    actualImageView.setCallback(null)
    actualImageView.recycle()

    preloadCancelableDownload = startFullMediaPreloading(
      loadingBar = loadingBar,
      mediaLocationRemote = mediaLocation,
      fullMediaDeferred = fullImageDeferred,
      onEndFunc = { preloadCancelableDownload = null }
    )

    show(isLifecycleChange = false)
  }

  private suspend fun setBigImageFromFile(filePath: FilePath) {
    coroutineScope {
      val animationAwaitable = CompletableDeferred<Unit>()

      actualImageView.setCallback(object : CustomScaleImageView.Callback {
        override fun onReady() {
          // no-op
        }

        override fun onImageLoaded() {
          val animatorSet = FullMediaAppearAnimationHelper.fullMediaAppearAnimation(
            prevActiveView = thumbnailMediaView,
            activeView = actualImageView,
            isSpoiler = viewableMedia.viewableMediaMeta.isSpoiler,
            onAnimationEnd = { animationAwaitable.complete(Unit) }
          )

          this@coroutineScope.coroutineContext[Job.Key]?.invokeOnCompletion {
            if (animatorSet == null) {
              return@invokeOnCompletion
            }

            animatorSet.end()
          }
        }

        override fun onImageLoadError(e: Exception) {
          Logger.e(TAG, "onImageLoadError()", e)
          animationAwaitable.complete(Unit)

          if (!e.isExceptionImportant()) {
            // Cancellation and other stuff
            return
          }

          if (shown) {
            if (e.cause is OutOfMemoryError) {
              cancellableToast.showToast(context, R.string.image_preview_failed_oom)
            } else {
              cancellableToast.showToast(context, R.string.image_failed_big_image)
            }
          }

          actualImageView.setVisibilityFast(View.INVISIBLE)
        }

        override fun onTileLoadError(e: Exception) {
          Logger.e(TAG, "onTileLoadError()", e)
          animationAwaitable.complete(Unit)

          cancellableToast.showToast(
            context,
            getString(R.string.image_tile_load_failed, e.errorMessageOrClassName())
          )
        }

      })

      actualImageView.setOnClickListener(null)

      val imageSource = when (filePath) {
        is FilePath.JavaPath -> ImageSource.uri(filePath.path).tiling(true)
        is FilePath.UriPath -> ImageSource.uri(filePath.uri).tiling(true)
      }

      actualImageView.setImage(imageSource)

      // Trigger the SubsamplingScaleImageView to start loading the full image but don't show it yet.
      actualImageView.alpha = 0f
      actualImageView.setVisibilityFast(View.VISIBLE)

      animationAwaitable.await()
    }
  }

  private fun canPreload(forced: Boolean): Boolean {
    if (forced) {
      return !fullImageDeferred.isCompleted
        && (preloadCancelableDownload == null || preloadCancelableDownload?.isRunning() == false)
    }

    return canAutoLoad()
      && !fullImageDeferred.isCompleted
      && (preloadCancelableDownload == null || preloadCancelableDownload?.isRunning() == false)
  }

  class GestureDetectorListener(
    private val thumbnailMediaView: ThumbnailMediaView,
    private val actualImageView: CustomScaleImageView,
    private val mediaViewContract: MediaViewContract,
    private val tryPreloadingFunc: () -> Boolean,
    private val onMediaLongClick: () -> Unit
  ) : GestureDetector.SimpleOnGestureListener() {
    private var doubleTapped = false

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
      if (actualImageView.visibility == View.VISIBLE) {
        mediaViewContract.onTapped()
        return true
      } else if (thumbnailMediaView.visibility == View.VISIBLE) {
        return tryPreloadingFunc()
      }

      return super.onSingleTapConfirmed(e)
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
      doubleTapped = true
      return super.onDoubleTap(e)
    }

    override fun onLongPress(e: MotionEvent?) {
      if (!doubleTapped) {
        onMediaLongClick()
      }
    }

    fun onUpOrCanceled() {
      doubleTapped = false
    }

  }

  class FullImageState : MediaViewState {
    override fun clone(): MediaViewState {
      return this
    }

    override fun updateFrom(other: MediaViewState?) {

    }
  }

  companion object {
    private const val TAG = "FullImageMediaView"
  }
}