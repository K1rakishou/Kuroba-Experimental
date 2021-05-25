package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
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
import com.github.k1rakishou.chan.ui.view.ChunkedLoadingBar
import com.github.k1rakishou.chan.ui.view.CustomScaleImageView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.awaitCatching
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class FullImageMediaView(
  context: Context,
  private val mediaViewContract: MediaViewContract,
  override val viewableMedia: ViewableMedia.Image,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<ViewableMedia.Image>(context, null) {
  private val thumbnailMediaView: ThumbnailMediaView
  private val actualImageView: CustomScaleImageView
  private val loadingBar: ChunkedLoadingBar
  private val gestureDetector = GestureDetector(context, GestureDetectorListener(mediaViewContract))

  private var fullImageDeferred = CompletableDeferred<File>()
  private var preloadCancelableDownload: CancelableDownload? = null

  override val hasContent: Boolean
    get() = actualImageView.hasImage()

  @Inject
  lateinit var fileCacheV2: FileCacheV2
  @Inject
  lateinit var cacheHandler: CacheHandler

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_full_image, this)

    thumbnailMediaView = findViewById(R.id.thumbnail_media_view)
    actualImageView = findViewById(R.id.actual_image_view)
    loadingBar = findViewById(R.id.loading_bar)

    thumbnailMediaView.setOnTouchListener { v, event ->
      if (thumbnailMediaView.visibility != View.VISIBLE) {
        return@setOnTouchListener false
      }

      return@setOnTouchListener gestureDetector.onTouchEvent(event)
    }

    actualImageView.setOnTouchListener { v, event ->
      if (actualImageView.visibility != View.VISIBLE) {
        return@setOnTouchListener false
      }

      return@setOnTouchListener gestureDetector.onTouchEvent(event)
    }
  }

  override fun preload() {
    if (hasContent) {
      return
    }

    val previewLocation = viewableMedia.previewLocation
    if (previewLocation != null) {
      thumbnailMediaView.bind(
        ThumbnailMediaView.ThumbnailMediaViewParameters(
          isOriginalMediaPlayable = false,
          thumbnailLocation = previewLocation
        )
      )
    }

    if (
      viewableMedia.mediaLocation is MediaLocation.Remote
      && MediaViewerControllerViewModel.canAutoLoad(cacheHandler, viewableMedia)
      && (preloadCancelableDownload == null || preloadCancelableDownload?.isRunning() == false)
      && !fullImageDeferred.isCompleted
    ) {
      val mediaLocationRemote = viewableMedia.mediaLocation

      val extraInfo = DownloadRequestExtraInfo(
        viewableMedia.viewableMediaMeta.mediaSize ?: -1,
        viewableMedia.viewableMediaMeta.mediaHash
      )

      preloadCancelableDownload = fileCacheV2.enqueueChunkedDownloadFileRequest(
        mediaLocationRemote.url,
        extraInfo,
        object : FileCacheListener() {
          override fun onStart(chunksCount: Int) {
            super.onStart(chunksCount)
            BackgroundUtils.ensureMainThread()

            loadingBar.setVisibilityFast(View.VISIBLE)
            loadingBar.setChunksCount(chunksCount)
          }

          override fun onProgress(chunkIndex: Int, downloaded: Long, total: Long) {
            super.onProgress(chunkIndex, downloaded, total)
            BackgroundUtils.ensureMainThread()

            loadingBar.setChunkProgress(chunkIndex, downloaded.toFloat() / total.toFloat())
          }

          override fun onSuccess(file: File) {
            BackgroundUtils.ensureMainThread()
            fullImageDeferred.complete(file)
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

            preloadCancelableDownload = null
            loadingBar.setVisibilityFast(View.GONE)
          }
        }
      )
    }
  }

  override fun bind() {
    if (hasContent) {
      return
    }

    scope.launch {
      fullImageDeferred.awaitCatching()
        .onFailure { error -> onFullImageLoadingError(error) }
        .onSuccess { file -> setBigImageFromFile(file) }
    }
  }

  override fun show() {

  }

  override fun hide() {
    actualImageView.resetScaleAndCenter()
  }

  override fun unbind() {
    thumbnailMediaView.unbind()

    preloadCancelableDownload?.cancel()
    preloadCancelableDownload = null

    actualImageView.setCallback(null)
    actualImageView.recycle()
  }

  private fun setBigImageFromFile(file: File) {
    actualImageView.setCallback(object : CustomScaleImageView.Callback {
      override fun onReady() {
        // no-op
      }

      override fun onImageLoaded() {
//        if (!hasContent || mode == MultiImageView.Mode.BIGIMAGE) {
//          runAppearAnimation(prevActiveView, findView(CustomScaleImageView::class.java), isSpoiler) {
//            callback?.hideProgress(this@MultiImageView)
//            onModeLoaded(MultiImageView.Mode.BIGIMAGE, image)
//            updateTransparency()
//          }
//        }

        thumbnailMediaView.setVisibilityFast(View.INVISIBLE)
      }

      override fun onError(e: Exception, wasInitial: Boolean) {
        if (e.cause is OutOfMemoryError) {
          Logger.e(TAG, "OOM while trying to set a big image file", e)

          Runtime.getRuntime().gc()
          onFullImageOutOfMemoryError()
        } else {
          onFullImageUnknownError()
        }
      }
    })

    actualImageView.setOnClickListener(null)
    actualImageView.setImage(ImageSource.uri(file.absolutePath).tiling(true))

    actualImageView.setVisibilityFast(View.VISIBLE)
  }

  private fun onFullImageOutOfMemoryError() {
    Logger.e(TAG, "onFullImageOutOfMemoryError()")
    cancellableToast.showToast(context, R.string.image_preview_failed_oom)
  }

  private fun onFullImageUnknownError() {
    Logger.e(TAG, "onFullImageUnknownError()")
    cancellableToast.showToast(context, R.string.image_failed_big_image)
  }

  private fun onFullImageLoadingError(error: Throwable) {
    Logger.e(TAG, "onFullImageLoadingError()", error)

    cancellableToast.showToast(
      context,
      getString(R.string.image_failed_big_image_error, error.errorMessageOrClassName())
    )
  }

  class GestureDetectorListener(
    private val mediaViewContract: MediaViewContract
  ) : GestureDetector.SimpleOnGestureListener() {

    override fun onDown(e: MotionEvent?): Boolean {
      return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
      mediaViewContract.onTapped()
      return super.onSingleTapConfirmed(e)
    }

  }

  companion object {
    private const val TAG = "FullImageMediaView"
  }
}