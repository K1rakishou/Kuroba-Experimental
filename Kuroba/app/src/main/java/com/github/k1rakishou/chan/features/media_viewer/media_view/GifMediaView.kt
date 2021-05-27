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
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.helper.CloseMediaActionHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.FullMediaAppearAnimationHelper
import com.github.k1rakishou.chan.ui.view.ChunkedLoadingBar
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
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView
import java.io.File
import javax.inject.Inject

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class GifMediaView(
  context: Context,
  private val mediaViewContract: MediaViewContract,
  private val onThumbnailFullyLoaded: () -> Unit,
  override val viewableMedia: ViewableMedia.Gif,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<ViewableMedia.Gif>(context, null) {
  private val movableContainer: FrameLayout
  private val thumbnailMediaView: ThumbnailMediaView
  private val actualGifView: GifImageView
  private val loadingBar: ChunkedLoadingBar

  private val gestureDetector = GestureDetector(context, FullImageMediaView.GestureDetectorListener(mediaViewContract))
  private val closeMediaActionHelper: CloseMediaActionHelper
  private val canAutoLoad by lazy { MediaViewerControllerViewModel.canAutoLoad(cacheHandler, viewableMedia) }

  private var fullGifDeferred = CompletableDeferred<File>()
  private var preloadCancelableDownload: CancelableDownload? = null

  override val hasContent: Boolean
    get() = actualGifView.drawable != null

  @Inject
  lateinit var fileCacheV2: FileCacheV2

  @Inject
  lateinit var cacheHandler: CacheHandler

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_gif, this)

    movableContainer = findViewById(R.id.movable_container)
    thumbnailMediaView = findViewById(R.id.thumbnail_media_view)
    actualGifView = findViewById(R.id.actual_gif_view)
    loadingBar = findViewById(R.id.loading_bar)

    closeMediaActionHelper = CloseMediaActionHelper(
      context = context,
      movableContainer = movableContainer,
      requestDisallowInterceptTouchEvent = { this.parent.requestDisallowInterceptTouchEvent(true) },
      onAlphaAnimationProgress = { alpha -> mediaViewContract.changeMediaViewerBackgroundAlpha(alpha) },
      closeMediaViewer = { mediaViewContract.closeMediaViewer() }
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
    val previewLocation = viewableMedia.previewLocation
    if (previewLocation != null) {
      thumbnailMediaView.bind(
        ThumbnailMediaView.ThumbnailMediaViewParameters(
          isOriginalMediaPlayable = true,
          thumbnailLocation = previewLocation
        ),
        onThumbnailFullyLoaded = onThumbnailFullyLoaded
      )
    }

    if (viewableMedia.mediaLocation is MediaLocation.Remote && canPreload()) {
      preloadCancelableDownload = startFullGifPreloading(viewableMedia.mediaLocation)
    }
  }

  override fun bind() {
    if (hasContent) {
      return
    }

    scope.launch {
      fullGifDeferred.awaitCatching()
        .onFailure { error ->
          Logger.e(TAG, "onFullGifLoadingError()", error)

          if (error.isExceptionImportant()) {
            cancellableToast.showToast(
              context,
              getString(R.string.image_failed_gif_error, error.errorMessageOrClassName())
            )
          }
        }
        .onSuccess { file -> setBigGifFromFile(file) }
    }
  }

  override fun show() {

  }

  override fun hide() {

  }

  override fun unbind() {
    thumbnailMediaView.unbind()
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  private suspend fun setBigGifFromFile(file: File) {
    coroutineScope {
      val animationAwaitable = CompletableDeferred<Unit>()

      val drawable = try {
        GifDrawable(file.absolutePath)
      } catch (e: Throwable) {
        Logger.e(TAG, "Error while trying to set a gif file", e)
        return@coroutineScope
      }

      actualGifView.setImageDrawable(drawable)
      actualGifView.setOnClickListener(null)

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

      loadingBar.setVisibilityFast(GONE)
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

          loadingBar.setVisibilityFast(GONE)
        }

        override fun onFail(exception: Exception) {
          BackgroundUtils.ensureMainThread()
          fullGifDeferred.completeExceptionally(exception)

          loadingBar.setVisibilityFast(GONE)
        }

        override fun onEnd() {
          super.onEnd()
          BackgroundUtils.ensureMainThread()

          preloadCancelableDownload = null
        }
      }
    )
  }

  private fun canPreload(): Boolean {
    return canAutoLoad
      && !fullGifDeferred.isCompleted
      && (preloadCancelableDownload == null || preloadCancelableDownload?.isRunning() == false)
  }

  companion object {
    private const val TAG = "GifMediaView"
  }
}