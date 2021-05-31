package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheListener
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.cache.downloader.CancelableDownload
import com.github.k1rakishou.chan.core.cache.downloader.DownloadRequestExtraInfo
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerToolbar
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.helper.CloseMediaActionHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.FullMediaAppearAnimationHelper
import com.github.k1rakishou.chan.ui.view.CircularChunkedLoadingBar
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.awaitCatching
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.core_logger.Logger
import com.google.android.exoplayer2.upstream.DataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView
import java.io.File
import javax.inject.Inject

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class GifMediaView(
  context: Context,
  initialMediaViewState: GifMediaViewState,
  mediaViewContract: MediaViewContract,
  private val cacheDataSourceFactory: DataSource.Factory,
  private val onThumbnailFullyLoaded: () -> Unit,
  private val isSystemUiHidden: () -> Boolean,
  override val viewableMedia: ViewableMedia.Gif,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<ViewableMedia.Gif, GifMediaView.GifMediaViewState>(
  context = context,
  attributeSet = null,
  cacheDataSourceFactory = cacheDataSourceFactory,
  mediaViewContract = mediaViewContract,
  mediaViewState = initialMediaViewState
) {

  @Inject
  lateinit var fileCacheV2: FileCacheV2

  @Inject
  lateinit var cacheHandler: CacheHandler

  private val movableContainer: FrameLayout
  private val thumbnailMediaView: ThumbnailMediaView
  private val actualGifView: GifImageView
  private val loadingBar: CircularChunkedLoadingBar

  private val closeMediaActionHelper: CloseMediaActionHelper
  private val gestureDetector: GestureDetector
  private val canAutoLoad by lazy { MediaViewerControllerViewModel.canAutoLoad(cacheHandler, viewableMedia) }

  private var fullGifDeferred = CompletableDeferred<File>()
  private var preloadCancelableDownload: CancelableDownload? = null

  private val fullImageMediaViewOptions by lazy { emptyList<FloatingListMenuItem>() }

  override val hasContent: Boolean
    get() = actualGifView.drawable != null
  override val mediaOptions: List<FloatingListMenuItem>
    get() = fullImageMediaViewOptions

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_gif, this)

    movableContainer = findViewById(R.id.movable_container)
    thumbnailMediaView = findViewById(R.id.thumbnail_media_view)
    actualGifView = findViewById(R.id.actual_gif_view)
    loadingBar = findViewById(R.id.loading_bar)

    val toolbar = findViewById<MediaViewerToolbar>(R.id.full_gif_view_toolbar)
    initToolbar(toolbar)

    closeMediaActionHelper = CloseMediaActionHelper(
      context = context,
      movableContainer = movableContainer,
      requestDisallowInterceptTouchEvent = { this.parent.requestDisallowInterceptTouchEvent(true) },
      onAlphaAnimationProgress = { alpha -> mediaViewContract.changeMediaViewerBackgroundAlpha(alpha) },
      closeMediaViewer = { mediaViewContract.closeMediaViewer() }
    )

    gestureDetector = GestureDetector(
      context,
      GestureDetectorListener(
        thumbnailMediaView = thumbnailMediaView,
        actualGifView = actualGifView,
        mediaViewContract = mediaViewContract,
        tryPreloadingFunc = {
          if (viewableMedia.mediaLocation is MediaLocation.Remote && canPreload(forced = true)) {
            preloadCancelableDownload = startFullGifPreloading(viewableMedia.mediaLocation)
            return@GestureDetectorListener true
          }

          return@GestureDetectorListener false
        }
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

  override fun preload() {
    thumbnailMediaView.bind(
      ThumbnailMediaView.ThumbnailMediaViewParameters(
        isOriginalMediaPlayable = true,
        thumbnailLocation = viewableMedia.previewLocation
      ),
      onThumbnailFullyLoaded = onThumbnailFullyLoaded
    )

    if (viewableMedia.mediaLocation is MediaLocation.Remote && canPreload(forced = false)) {
      preloadCancelableDownload = startFullGifPreloading(viewableMedia.mediaLocation)
    }
  }

  override fun bind() {

  }

  override fun show() {
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
          .onSuccess { file ->
            setBigGifFromFile(file)
          }

        loadingBar.setVisibilityFast(GONE)
        onMediaFullyLoaded()
      }

      val gifImageViewDrawable = actualGifView.drawable as? GifDrawable
      if (gifImageViewDrawable != null) {
        if (!gifImageViewDrawable.isPlaying) {
          gifImageViewDrawable.start()
        }
      }
    }
  }

  override fun hide() {
    val gifImageViewDrawable = actualGifView.drawable as? GifDrawable
    if (gifImageViewDrawable != null) {
      if (gifImageViewDrawable.isPlaying) {
        gifImageViewDrawable.pause()
      }
    }
  }

  override fun unbind() {
    thumbnailMediaView.unbind()
    actualGifView.setImageDrawable(null)
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
    fullGifDeferred = CompletableDeferred<File>()

    thumbnailMediaView.setVisibilityFast(View.VISIBLE)
    actualGifView.setVisibilityFast(View.INVISIBLE)
    actualGifView.setImageDrawable(null)

    preloadCancelableDownload = startFullGifPreloading(mediaLocation)
    show()
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  private suspend fun setBigGifFromFile(file: File) {
    coroutineScope {
      val drawable = try {
        GifDrawable(file.absolutePath)
      } catch (e: Throwable) {
        Logger.e(TAG, "Error while trying to set a gif file", e)
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

  private fun startFullGifPreloading(mediaLocationRemote: MediaLocation.Remote): CancelableDownload? {
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
          fullGifDeferred.complete(file)
        }

        override fun onNotFound() {
          BackgroundUtils.ensureMainThread()
          fullGifDeferred.completeExceptionally(ImageNotFoundException(mediaLocationRemote.url))
        }

        override fun onFail(exception: Exception) {
          BackgroundUtils.ensureMainThread()
          fullGifDeferred.completeExceptionally(exception)
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

  private fun canPreload(forced: Boolean): Boolean {
    if (forced) {
      return !fullGifDeferred.isCompleted
        && (preloadCancelableDownload == null || preloadCancelableDownload?.isRunning() == false)
    }

    return canAutoLoad
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
    private val tryPreloadingFunc: () -> Boolean
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

  }

  companion object {
    private const val TAG = "GifMediaView"
  }
}