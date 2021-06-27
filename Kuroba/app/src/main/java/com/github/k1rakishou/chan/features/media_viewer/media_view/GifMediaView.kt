package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.downloader.CancelableDownload
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.helper.CloseMediaActionHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.FullMediaAppearAnimationHelper
import com.github.k1rakishou.chan.ui.view.CircularChunkedLoadingBar
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.awaitCatching
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.file.FileDescriptorMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView
import java.io.File
import java.io.IOException

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class GifMediaView(
  context: Context,
  initialMediaViewState: GifMediaViewState,
  mediaViewContract: MediaViewContract,
  private val onThumbnailFullyLoadedFunc: () -> Unit,
  private val isSystemUiHidden: () -> Boolean,
  override val viewableMedia: ViewableMedia.Gif,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<ViewableMedia.Gif, GifMediaView.GifMediaViewState>(
  context = context,
  attributeSet = null,
  mediaViewContract = mediaViewContract,
  mediaViewState = initialMediaViewState
) {

  private val movableContainer: FrameLayout
  private val thumbnailMediaView: ThumbnailMediaView
  private val actualGifView: GifImageView
  private val loadingBar: CircularChunkedLoadingBar

  private val closeMediaActionHelper: CloseMediaActionHelper
  private val gestureDetector: GestureDetector

  private var fullGifDeferred = CompletableDeferred<FilePath>()
  private var preloadCancelableDownload: CancelableDownload? = null

  override val hasContent: Boolean
    get() = actualGifView.drawable != null

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_gif, this)
    setWillNotDraw(false)

    movableContainer = findViewById(R.id.movable_container)
    thumbnailMediaView = findViewById(R.id.thumbnail_media_view)
    actualGifView = findViewById(R.id.actual_gif_view)
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

    gestureDetector = GestureDetector(
      context,
      GestureDetectorListener(
        thumbnailMediaView = thumbnailMediaView,
        actualGifView = actualGifView,
        mediaViewContract = mediaViewContract,
        tryPreloadingFunc = {
          if (viewableMedia.mediaLocation is MediaLocation.Remote && canPreload(forced = true)) {
            scope.launch {
              preloadCancelableDownload = startFullMediaPreloading(
                loadingBar = loadingBar,
                mediaLocationRemote = viewableMedia.mediaLocation,
                fullMediaDeferred = fullGifDeferred,
                onEndFunc = { preloadCancelableDownload = null }
              )
            }

            return@GestureDetectorListener true
          }

          return@GestureDetectorListener false
        },
        onMediaLongClick = { mediaViewContract.onMediaLongClick(this, viewableMedia) }
      )
    )

    thumbnailMediaView.setOnTouchListener { v, event ->
      if (thumbnailMediaView.visibility != View.VISIBLE) {
        return@setOnTouchListener false
      }

      // Always return true for thumbnails because otherwise gestures won't work with thumbnails
      gestureDetector.onTouchEvent(event)
      return@setOnTouchListener true
    }

    actualGifView.setOnTouchListener { v, event ->
      if (actualGifView.visibility != View.VISIBLE) {
        return@setOnTouchListener false
      }

      return@setOnTouchListener gestureDetector.onTouchEvent(event)
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
    if (backgroundColor == null) {
      actualGifView.drawable?.colorFilter = null
    } else {
      actualGifView.drawable?.setColorFilter(
        backgroundColor,
        PorterDuff.Mode.DST_OVER
      )
    }
  }

  override fun preload() {
    thumbnailMediaView.bind(
      ThumbnailMediaView.ThumbnailMediaViewParameters(
        isOriginalMediaPlayable = true,
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
          fullMediaDeferred = fullGifDeferred,
          onEndFunc = { preloadCancelableDownload = null }
        )
      }
    }  else if (viewableMedia.mediaLocation is MediaLocation.Local) {
      fullGifDeferred.complete(FilePath.JavaPath(viewableMedia.mediaLocation.path))
    }
  }

  override fun bind() {

  }

  override fun show(isLifecycleChange: Boolean) {
    mediaViewToolbar?.updateWithViewableMedia(pagerPosition, totalPageItemsCount, viewableMedia)
    onSystemUiVisibilityChanged(isSystemUiHidden())

    scope.launch {
      if (!hasContent) {
        fullGifDeferred.awaitCatching()
          .onFailure { error ->
            Logger.e(TAG, "onFullGifLoadingError()", error)

            if (error.isExceptionImportant()) {
              cancellableToast.showToast(
                context,
                AppModuleAndroidUtils.getString(R.string.image_failed_gif_error, error.errorMessageOrClassName())
              )
            }

            actualGifView.setVisibilityFast(View.INVISIBLE)
            thumbnailMediaView.setError(error.errorMessageOrClassName())
          }
          .onSuccess { filePath ->
            setBigGifFromFile(filePath)
          }

        loadingBar.setVisibilityFast(GONE)
      }

      onUpdateTransparency()

      val gifImageViewDrawable = actualGifView.drawable as? GifDrawable
      if (gifImageViewDrawable != null && !gifImageViewDrawable.isPlaying) {
        gifImageViewDrawable.start()
      }
    }
  }

  override fun hide(isLifecycleChange: Boolean) {
    val gifImageViewDrawable = actualGifView.drawable as? GifDrawable
    if (gifImageViewDrawable != null && gifImageViewDrawable.isPlaying) {
      gifImageViewDrawable.pause()
    }
  }

  override fun unbind() {
    closeMediaActionHelper.onDestroy()

    if (fullGifDeferred.isActive) {
      fullGifDeferred.cancel()
    }

    preloadCancelableDownload?.cancel()
    preloadCancelableDownload = null

    thumbnailMediaView.unbind()
    actualGifView.setImageDrawable(null)
  }

  override suspend fun reloadMedia() {
    if (preloadCancelableDownload != null) {
      return
    }

    val mediaLocation = viewableMedia.mediaLocation
    if (mediaLocation !is MediaLocation.Remote) {
      return
    }

    cacheHandler.deleteCacheFileByUrlSuspend(mediaLocation.url.toString())

    fullGifDeferred.cancel()
    fullGifDeferred = CompletableDeferred<FilePath>()

    thumbnailMediaView.setVisibilityFast(View.VISIBLE)
    actualGifView.setVisibilityFast(View.INVISIBLE)
    actualGifView.setImageDrawable(null)

    preloadCancelableDownload = startFullMediaPreloading(
      loadingBar = loadingBar,
      mediaLocationRemote = mediaLocation,
      fullMediaDeferred = fullGifDeferred,
      onEndFunc = { preloadCancelableDownload = null }
    )

    show(isLifecycleChange = false)
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  private suspend fun setBigGifFromFile(filePath: FilePath) {
    coroutineScope {
      val drawable = try {
        createGifDrawableSafe(filePath)
      } catch (e: Throwable) {
        Logger.e(TAG, "Error while trying to set a gif file", e)

        if (e is GifIsTooBigException) {
          cancellableToast.showToast(context, "Failed to draw Gif. Error: ${e.message}")
          return@coroutineScope
        }

        thumbnailMediaView.setError(e.errorMessageOrClassName())
        return@coroutineScope
      }

      actualGifView.setImageDrawable(drawable)
      actualGifView.setOnClickListener(null)

      val animationAwaitable = CompletableDeferred<Unit>()

      val animatorSet = FullMediaAppearAnimationHelper.fullMediaAppearAnimation(
        prevActiveView = thumbnailMediaView,
        activeView = actualGifView,
        isSpoiler = viewableMedia.viewableMediaMeta.isSpoiler,
        onAnimationEnd = { animationAwaitable.complete(Unit) }
      )

      this@coroutineScope.coroutineContext[Job.Key]?.invokeOnCompletion {
        if (animatorSet == null) {
          return@invokeOnCompletion
        }

        animatorSet.end()
      }

      animationAwaitable.await()
    }
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  private suspend fun createGifDrawableSafe(filePath: FilePath): GifDrawable {
    return withContext(Dispatchers.IO) {
      val gifDrawable = when (filePath) {
        is FilePath.JavaPath -> {
          GifDrawable(File(filePath.path))
        }
        is FilePath.UriPath -> {
          fileManager.fromUri(filePath.uri)
            ?.let { file ->
              return@let fileManager.withFileDescriptor(file, FileDescriptorMode.Read) { fd ->
                return@withFileDescriptor GifDrawable(fd)
              }
            }
            ?: throw IOException("Failed to open get input stream for file ${filePath.uri}")
        }
      }

      if (gifDrawable.allocationByteCount > MAX_GIF_SIZE) {
        throw GifIsTooBigException(gifDrawable.allocationByteCount)
      }

      return@withContext gifDrawable
    }
  }

  private fun canPreload(forced: Boolean): Boolean {
    if (forced) {
      return !fullGifDeferred.isCompleted
        && (preloadCancelableDownload == null || preloadCancelableDownload?.isRunning() == false)
    }

    return canAutoLoad()
      && !fullGifDeferred.isCompleted
      && (preloadCancelableDownload == null || preloadCancelableDownload?.isRunning() == false)
  }

  class GifMediaViewState : MediaViewState {
    override fun clone(): MediaViewState {
      return this
    }

    override fun updateFrom(other: MediaViewState?) {
    }
  }

  class GestureDetectorListener(
    private val thumbnailMediaView: ThumbnailMediaView,
    private val actualGifView: GifImageView,
    private val mediaViewContract: MediaViewContract,
    private val tryPreloadingFunc: () -> Boolean,
    private val onMediaLongClick: () -> Unit
  ) : GestureDetector.SimpleOnGestureListener() {

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
      if (actualGifView.visibility == View.VISIBLE) {
        mediaViewContract.onTapped()
        return true
      } else if (thumbnailMediaView.visibility == View.VISIBLE) {
        return tryPreloadingFunc()
      }

      return super.onSingleTapConfirmed(e)
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
      if (e == null || actualGifView.visibility != View.VISIBLE) {
        return false
      }

      val gifImageViewDrawable = actualGifView.drawable as? GifDrawable
      if (gifImageViewDrawable != null) {
        if (gifImageViewDrawable.isPlaying) {
          gifImageViewDrawable.pause()
        } else {
          gifImageViewDrawable.start()
        }
      }

      return super.onDoubleTap(e)
    }

    override fun onLongPress(e: MotionEvent?) {
      onMediaLongClick()
    }
  }

  private class GifIsTooBigException(sizeInBytes: Long) : Exception("Gif is too big! (${sizeInBytes / (1024 * 1024)} MB)")

  companion object {
    private const val TAG = "GifMediaView"
    // 99 MB. Max - 1 just in case. The max allowed by Android Canvas is 100 MB.
    private const val MAX_GIF_SIZE = 99 * 1024 * 1024
  }
}