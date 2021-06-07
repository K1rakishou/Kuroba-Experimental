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
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheListener
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.cache.downloader.CancelableDownload
import com.github.k1rakishou.chan.core.cache.downloader.DownloadRequestExtraInfo
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.helper.CloseMediaActionHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.FullMediaAppearAnimationHelper
import com.github.k1rakishou.chan.ui.view.CircularChunkedLoadingBar
import com.github.k1rakishou.chan.ui.view.CustomScaleImageView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.awaitCatching
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

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

  @Inject
  lateinit var fileCacheV2: FileCacheV2
  @Inject
  lateinit var cacheHandler: CacheHandler

  private val movableContainer: FrameLayout
  private val thumbnailMediaView: ThumbnailMediaView
  private val actualImageView: CustomScaleImageView
  private val loadingBar: CircularChunkedLoadingBar

  private val gestureDetector: GestureDetector
  private val gestureDetectorListener: GestureDetectorListener
  private val closeMediaActionHelper: CloseMediaActionHelper
  private val canAutoLoad by lazy { MediaViewerControllerViewModel.canAutoLoad(cacheHandler, viewableMedia) }

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
      topGestureInfo = CloseMediaActionHelper.GestureInfo(
        gestureLabelText = getString(R.string.download),
        onGestureTriggeredFunc = { mediaViewToolbar?.downloadMedia() },
        gestureCanBeExecuted = { mediaViewToolbar?.isDownloadAllowed() ?: false }
      ),
      bottomGestureInfo = CloseMediaActionHelper.GestureInfo(
        gestureLabelText = getString(R.string.close),
        onGestureTriggeredFunc = { mediaViewContract.closeMediaViewer() },
        gestureCanBeExecuted = { true }
      )
    )

    gestureDetectorListener = GestureDetectorListener(
      thumbnailMediaView = thumbnailMediaView,
      actualImageView = actualImageView,
      mediaViewContract = mediaViewContract,
      tryPreloadingFunc = {
        if (viewableMedia.mediaLocation is MediaLocation.Remote && canPreload(forced = true)) {
          preloadCancelableDownload = startFullImagePreloading(viewableMedia.mediaLocation)
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
      preloadCancelableDownload = startFullImagePreloading(viewableMedia.mediaLocation)
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

  override fun show() {
    mediaViewToolbar?.updateWithViewableMedia(pagerPosition, totalPageItemsCount, viewableMedia)
    onSystemUiVisibilityChanged(isSystemUiHidden())
    onUpdateTransparency()

    if (!hasContent) {
      scope.launch {
        fullImageDeferred.awaitCatching()
          .onFailure { error ->
            Logger.e(TAG, "onFullImageLoadingError()", error)

            if (error.isExceptionImportant()) {
              cancellableToast.showToast(
                context,
                getString(R.string.image_failed_big_image_error, error.errorMessageOrClassName())
              )
            }

            actualImageView.setVisibilityFast(View.INVISIBLE)
            thumbnailMediaView.setError(error.errorMessageOrClassName())
          }
          .onSuccess { filePath ->
            setBigImageFromFile(filePath)
          }

        loadingBar.setVisibilityFast(GONE)
      }
    }
  }

  override fun hide() {
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

  override suspend fun onReloadButtonClick() {
    if (preloadCancelableDownload != null) {
      return
    }

    val mediaLocation = viewableMedia.mediaLocation
    if (mediaLocation !is MediaLocation.Remote) {
      return
    }

    cacheHandler.deleteCacheFileByUrl(mediaLocation.url.toString())

    fullImageDeferred.cancel()
    fullImageDeferred = CompletableDeferred<FilePath>()

    thumbnailMediaView.setVisibilityFast(View.VISIBLE)
    actualImageView.setVisibilityFast(View.INVISIBLE)
    actualImageView.setCallback(null)
    actualImageView.recycle()

    preloadCancelableDownload = startFullImagePreloading(mediaLocation)
    show()
  }

  private fun startFullImagePreloading(mediaLocationRemote: MediaLocation.Remote): CancelableDownload? {
    val extraInfo = DownloadRequestExtraInfo(
      viewableMedia.viewableMediaMeta.mediaSize ?: -1,
      viewableMedia.viewableMediaMeta.mediaHash
    )

    return fileCacheV2.enqueueChunkedDownloadFileRequest(
      mediaLocationRemote.url,
      extraInfo,
      object : FileCacheListener() {
        override fun onStart(chunksCount: Int) {
          super.onStart(chunksCount)
          BackgroundUtils.ensureMainThread()

          loadingBar.setVisibilityFast(VISIBLE)
          loadingBar.setChunksCount(chunksCount)
        }

        override fun onProgress(chunkIndex: Int, downloaded: Long, total: Long) {
          super.onProgress(chunkIndex, downloaded, total)
          BackgroundUtils.ensureMainThread()

          loadingBar.setChunkProgress(chunkIndex, downloaded.toFloat() / total.toFloat())
        }

        override fun onSuccess(file: File) {
          BackgroundUtils.ensureMainThread()
          fullImageDeferred.complete(FilePath.JavaPath(file.absolutePath))
        }

        override fun onNotFound() {
          BackgroundUtils.ensureMainThread()
          fullImageDeferred.completeExceptionally(ImageNotFoundException(mediaLocationRemote.url))
        }

        override fun onFail(exception: Exception) {
          BackgroundUtils.ensureMainThread()
          fullImageDeferred.completeExceptionally(exception)
        }

        override fun onEnd() {
          super.onEnd()
          BackgroundUtils.ensureMainThread()

          if (!shown) {
            loadingBar.setVisibilityFast(GONE)
          }

          preloadCancelableDownload = null
        }
      }
    )
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

        override fun onError(e: Exception, wasInitial: Boolean) {
          Logger.e(TAG, "onFullImageUnknownError()", e)
          animationAwaitable.complete(Unit)

          if (!e.isExceptionImportant()) {
            // Cancellation and other stuff
            return
          }

          if (e.cause is OutOfMemoryError) {
            cancellableToast.showToast(context, R.string.image_preview_failed_oom)
          } else {
            cancellableToast.showToast(context, R.string.image_failed_big_image)
          }

          actualImageView.setVisibilityFast(View.INVISIBLE)
          thumbnailMediaView.setError(e.errorMessageOrClassName())
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

    return canAutoLoad
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

  sealed class FilePath {
    data class JavaPath(val path: String) : FilePath()
    data class UriPath(val uri: Uri) : FilePath()
  }

  companion object {
    private const val TAG = "FullImageMediaView"
  }
}