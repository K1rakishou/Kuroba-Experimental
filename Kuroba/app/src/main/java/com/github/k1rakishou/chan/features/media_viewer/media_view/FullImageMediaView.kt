package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
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
import com.github.k1rakishou.chan.features.media_viewer.ViewableMediaMeta
import com.github.k1rakishou.chan.ui.view.ChunkedLoadingBar
import com.github.k1rakishou.chan.ui.view.CustomScaleImageView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.core_logger.Logger
import okhttp3.HttpUrl
import java.io.File
import javax.inject.Inject

@SuppressLint("ViewConstructor")
class FullImageMediaView(
  context: Context,
  override val viewableMedia: ViewableMedia.Image,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<ViewableMedia.Image>(context, null) {
  private val thumbnailMediaView: ThumbnailMediaView
  private val actualImageView: CustomScaleImageView
  private val loadingBar: ChunkedLoadingBar

  private var preloadCancelableDownload: CancelableDownload? = null
  private var actualLoadCancelableDownload: CancelableDownload? = null
  private var hasContent = false

  @Inject
  lateinit var fileCacheV2: FileCacheV2
  @Inject
  lateinit var cacheHandler: CacheHandler

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_full_image, this)

    actualImageView = findViewById(R.id.actual_image_view)
    thumbnailMediaView = findViewById(R.id.thumbnail_media_view)
    loadingBar = findViewById(R.id.loading_bar)
  }

  override fun preload() {
    val previewLocation = viewableMedia.previewLocation
    if (previewLocation != null) {
      thumbnailMediaView.bind(
        ThumbnailMediaView.ThumbnailMediaViewParameters(
          isOriginalMediaVideo = false,
          thumbnailLocation = previewLocation
        )
      )
    }

    if (
      viewableMedia.mediaLocation is MediaLocation.Remote
      && MediaViewerControllerViewModel.canAutoLoad(cacheHandler, viewableMedia)
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
            loadingBar.setVisibilityFast(View.VISIBLE)
            loadingBar.setChunksCount(chunksCount)
          }

          override fun onProgress(chunkIndex: Int, downloaded: Long, total: Long) {
            super.onProgress(chunkIndex, downloaded, total)
            loadingBar.setChunkProgress(chunkIndex, downloaded.toFloat() / total.toFloat())
          }

          override fun onEnd() {
            super.onEnd()
            loadingBar.setVisibilityFast(View.GONE)
          }
        }
      )
    }
  }

  override fun bind() {
    when (val location = viewableMedia.mediaLocation) {
      is MediaLocation.Local -> TODO()
      is MediaLocation.Remote -> loadRemoteFullImage(location.url, viewableMedia.viewableMediaMeta)
    }
  }

  override fun hide() {

  }

  override fun unbind() {
    thumbnailMediaView.unbind()

    preloadCancelableDownload?.cancel()
    preloadCancelableDownload = null

    actualLoadCancelableDownload?.cancel()
    actualLoadCancelableDownload = null

    actualImageView.setCallback(null)
    hasContent = false
  }

  private fun loadRemoteFullImage(url: HttpUrl, viewableMediaMeta: ViewableMediaMeta) {
    if (actualLoadCancelableDownload != null || hasContent) {
      return
    }

    val extraInfo = DownloadRequestExtraInfo(
      viewableMediaMeta.mediaSize ?: -1,
      viewableMediaMeta.mediaHash
    )

    actualLoadCancelableDownload = fileCacheV2.enqueueChunkedDownloadFileRequest(
      url,
      extraInfo,
      object : FileCacheListener() {
        override fun onStart(chunksCount: Int) {
          BackgroundUtils.ensureMainThread()

          loadingBar.setVisibilityFast(View.VISIBLE)
          loadingBar.setChunksCount(chunksCount)
        }

        override fun onProgress(chunkIndex: Int, downloaded: Long, total: Long) {
          BackgroundUtils.ensureMainThread()

          loadingBar.setChunkProgress(chunkIndex, downloaded.toFloat() / total.toFloat())
        }

        override fun onSuccess(file: File) {
          BackgroundUtils.ensureMainThread()
          setBigImageFromFile(file)

          hasContent = true

//          callback?.onDownloaded(postImage)
        }

        override fun onNotFound() {
          BackgroundUtils.ensureMainThread()
//          onNotFoundError()
        }

        override fun onFail(exception: Exception) {
          BackgroundUtils.ensureMainThread()
//          onError(exception)
        }

        override fun onEnd() {
          BackgroundUtils.ensureMainThread()
          preloadCancelableDownload = null

          loadingBar.setVisibilityFast(View.GONE)
        }
      })
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
          onOutOfMemoryError()
        } else {
          onBigImageError(wasInitial)
        }
      }
    })

//    image.setOnTouchListener { _, motionEvent -> gestureDetector.onTouchEvent(motionEvent) }

    actualImageView.setOnClickListener(null)
    actualImageView.setImage(ImageSource.uri(file.absolutePath).tiling(true))

    actualImageView.setVisibilityFast(View.VISIBLE)
  }

  private fun onOutOfMemoryError() {
    cancellableToast.showToast(context, R.string.image_preview_failed_oom)
//    callback?.hideProgress(this@MultiImageView)
  }

  private fun onBigImageError(wasInitial: Boolean) {
    if (wasInitial) {
      cancellableToast.showToast(context, R.string.image_failed_big_image)
//      callback?.hideProgress(this@MultiImageView)
    }
  }

  companion object {
    private const val TAG = "FullImageMediaView"
  }
}