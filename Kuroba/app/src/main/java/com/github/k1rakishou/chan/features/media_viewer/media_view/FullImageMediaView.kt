package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
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
  private val mediaViewContract: MediaViewContract,
  private val onThumbnailFullyLoaded: () -> Unit,
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
          thumbnailLocation = previewLocation,
        ),
        onThumbnailFullyLoaded = onThumbnailFullyLoaded
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
        .onFailure { error ->
          Logger.e(TAG, "onFullImageLoadingError()", error)

          if (error.isExceptionImportant()) {
            cancellableToast.showToast(
              context,
              getString(R.string.image_failed_big_image_error, error.errorMessageOrClassName())
            )
          }
        }
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

  private suspend fun setBigImageFromFile(file: File) {
    coroutineScope {
      val animationAwaitable = CompletableDeferred<Unit>()

      actualImageView.setCallback(object : CustomScaleImageView.Callback {
        override fun onReady() {
          // no-op
        }

        override fun onImageLoaded() {
          val animatorSet = runAppearAnimation(
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

          if (!e.isExceptionImportant()) {
            return
          }

          if (e.cause is OutOfMemoryError) {
            Runtime.getRuntime().gc()

            cancellableToast.showToast(context, R.string.image_preview_failed_oom)
          } else {
            cancellableToast.showToast(context, R.string.image_failed_big_image)
          }
        }
      })

      actualImageView.setOnClickListener(null)
      actualImageView.setImage(ImageSource.uri(file.absolutePath).tiling(true))

      // Trigger the SubsamplingScaleImageView to start loading the full image but don't show it yet.
      actualImageView.alpha = 0f
      actualImageView.setVisibilityFast(View.VISIBLE)

      animationAwaitable.await()
    }
  }

  private fun runAppearAnimation(
    prevActiveView: View?,
    activeView: View?,
    isSpoiler: Boolean,
    onAnimationEnd: () -> Unit
  ): AnimatorSet? {
    if (activeView == null) {
      onAnimationEnd()
      return null
    }

    if (isSpoiler || prevActiveView == null) {
      activeView.alpha = 1f
      onAnimationEnd()
      return null
    }

    val appearanceAnimation = ValueAnimator.ofFloat(0f, 1f)

    appearanceAnimation.addUpdateListener { animation: ValueAnimator ->
      val alpha = animation.animatedValue as Float
      activeView.alpha = alpha
    }

    val animatorSet = AnimatorSet()
    animatorSet.addListener(object : AnimatorListenerAdapter() {
      override fun onAnimationStart(animation: Animator) {
        super.onAnimationStart(animation)

        prevActiveView.alpha = 1f
        activeView.alpha = 0f
        activeView.setVisibilityFast(View.VISIBLE)
      }

      override fun onAnimationEnd(animation: Animator) {
        super.onAnimationEnd(animation)

        prevActiveView.setVisibilityFast(View.INVISIBLE)
        activeView.alpha = 1f

        onAnimationEnd()
      }

      override fun onAnimationCancel(animation: Animator?) {
        super.onAnimationCancel(animation)

        prevActiveView.setVisibilityFast(View.INVISIBLE)
        activeView.alpha = 1f
      }
    })

    animatorSet.play(appearanceAnimation)
    animatorSet.interpolator = interpolator
    animatorSet.duration = 200
    animatorSet.start()

    return animatorSet
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
    private val interpolator = DecelerateInterpolator(3f)
  }
}