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
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.downloader.CancelableDownload
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.helper.CloseMediaActionHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.FullMediaAppearAnimationHelper
import com.github.k1rakishou.chan.features.media_viewer.strip.MediaViewerActionStrip
import com.github.k1rakishou.chan.features.media_viewer.strip.MediaViewerBottomActionStrip
import com.github.k1rakishou.chan.ui.view.CircularChunkedLoadingBar
import com.github.k1rakishou.chan.ui.view.CustomScaleImageView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.awaitCatching
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.core_logger.Logger
import com.google.android.exoplayer2.upstream.DataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class FullImageMediaView(
  context: Context,
  initialMediaViewState: FullImageState,
  mediaViewContract: MediaViewContract,
  private val onThumbnailFullyLoadedFunc: () -> Unit,
  private val isSystemUiHidden: () -> Boolean,
  cachedHttpDataSourceFactory: DataSource.Factory,
  fileDataSourceFactory: DataSource.Factory,
  contentDataSourceFactory: DataSource.Factory,
  override val viewableMedia: ViewableMedia.Image,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<ViewableMedia.Image, FullImageMediaView.FullImageState>(
  context = context,
  attributeSet = null,
  mediaViewContract = mediaViewContract,
  cachedHttpDataSourceFactory = cachedHttpDataSourceFactory,
  fileDataSourceFactory = fileDataSourceFactory,
  contentDataSourceFactory = contentDataSourceFactory,
  mediaViewState = initialMediaViewState
) {
  private val movableContainer: FrameLayout
  private val thumbnailMediaView: ThumbnailMediaView
  private val actualImageView: CustomScaleImageView
  private val loadingBar: CircularChunkedLoadingBar
  private val actionStrip: MediaViewerActionStrip

  private val gestureDetector: GestureDetector
  private val gestureDetectorListener: GestureDetectorListener
  private val closeMediaActionHelper: CloseMediaActionHelper

  private var fullImageDeferred = CompletableDeferred<MediaPreloadResult>()
  private var preloadCancelableDownload: CancelableDownload? = null

  override val hasContent: Boolean
    get() = actualImageView.hasImage()
  override val mediaViewerActionStrip: MediaViewerActionStrip
    get() = actionStrip

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_full_image, this)
    setWillNotDraw(false)

    movableContainer = findViewById(R.id.movable_container)
    thumbnailMediaView = findViewById(R.id.thumbnail_media_view)
    actualImageView = findViewById(R.id.actual_image_view)
    loadingBar = findViewById(R.id.loading_bar)

    if (AppModuleAndroidUtils.isTablet()) {
      actionStrip = findViewById<MediaViewerBottomActionStrip?>(R.id.left_action_strip)
    } else {
      actionStrip = findViewById<MediaViewerBottomActionStrip?>(R.id.bottom_action_strip)
    }

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
              forced = true,
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

  override fun onInsetsChanged() {

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
          forced = false,
          loadingBar = loadingBar,
          mediaLocationRemote = viewableMedia.mediaLocation,
          fullMediaDeferred = fullImageDeferred,
          onEndFunc = { preloadCancelableDownload = null }
        )
      }
    } else if (viewableMedia.mediaLocation is MediaLocation.Local) {
      if (viewableMedia.mediaLocation.isUri) {
        val mediaPreloadResult = MediaPreloadResult(
          filePath = FilePath.UriPath(Uri.parse(viewableMedia.mediaLocation.path)),
          isForced = false,
        )

        fullImageDeferred.complete(mediaPreloadResult)
      } else {
        val mediaPreloadResult = MediaPreloadResult(
          filePath = FilePath.JavaPath(viewableMedia.mediaLocation.path),
          isForced = false,
        )

        fullImageDeferred.complete(mediaPreloadResult)
      }
    }
  }

  override fun bind() {
  }

  override fun show(isLifecycleChange: Boolean) {
    super.updateComponentsWithViewableMedia(pagerPosition, totalPageItemsCount, viewableMedia)
    onSystemUiVisibilityChanged(isSystemUiHidden())
    onUpdateTransparency()
    thumbnailMediaView.show()

    scope.launch {
      if (hasContent) {
        val isForced = fullImageDeferred.awaitCatching().valueOrNull()?.isForced
        if (isForced != null) {
          audioPlayerView?.loadAndPlaySoundPostAudioIfPossible(
            isLifecycleChange = isLifecycleChange,
            isForceLoad = isForced,
            viewableMedia = viewableMedia
          )

          return@launch
        }
      }

      when (val fullImageDeferredResult = fullImageDeferred.awaitCatching()) {
        is ModularResult.Error -> {
          val error = fullImageDeferredResult.error
          Logger.e(TAG, "onFullImageLoadingError()", error)

          if (error.isExceptionImportant() && shown) {
            cancellableToast.showToast(
              context,
              getString(R.string.image_image_download_failed, error.errorMessageOrClassName())
            )
          }

          actualImageView.setVisibilityFast(View.INVISIBLE)
        }
        is ModularResult.Value -> {
          val mediaPreloadResult = fullImageDeferredResult.value
          setBigImageFromFile(isLifecycleChange, mediaPreloadResult)

          withContext(Dispatchers.Default) {
            val fileSize = mediaPreloadResult.filePath.fileSize(fileManager)
            if (fileSize != null) {
              viewableMedia.viewableMediaMeta.mediaOnDiskSize = fileSize
            }
          }

          super.updateComponentsWithViewableMedia(pagerPosition, totalPageItemsCount, viewableMedia)
        }
      }

      loadingBar.setVisibilityFast(GONE)
    }
  }

  override fun hide(isLifecycleChange: Boolean, isPausing: Boolean, isBecomingInactive: Boolean) {
    thumbnailMediaView.hide()
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

    cacheHandler.get().deleteCacheFileByUrlSuspend(
      cacheFileType = CacheFileType.PostMediaFull,
      url = mediaLocation.url.toString()
    )

    fullImageDeferred.cancel()
    fullImageDeferred = CompletableDeferred<MediaPreloadResult>()

    audioPlayerView?.pauseUnpause(isNowPaused = true)

    thumbnailMediaView.setVisibilityFast(View.VISIBLE)
    actualImageView.setVisibilityFast(View.INVISIBLE)
    actualImageView.setCallback(null)
    actualImageView.recycle()

    preloadCancelableDownload = startFullMediaPreloading(
      forced = true,
      loadingBar = loadingBar,
      mediaLocationRemote = mediaLocation,
      fullMediaDeferred = fullImageDeferred,
      onEndFunc = { preloadCancelableDownload = null }
    )

    show(isLifecycleChange = false)
  }

  private suspend fun setBigImageFromFile(
    isLifecycleChange: Boolean,
    mediaPreloadResult: MediaPreloadResult
  ) {
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
            cancellableToast.showToast(
              context,
              getString(R.string.image_image_load_failed, e.errorMessageOrClassName())
            )
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

      val tiling = !viewableMedia.viewableMediaMeta.isGif

      val imageSource = when (val filePath = mediaPreloadResult.filePath) {
        is FilePath.JavaPath -> ImageSource.uri(filePath.path).tiling(tiling)
        is FilePath.UriPath -> ImageSource.uri(filePath.uri).tiling(tiling)
      }

      actualImageView.setImage(imageSource)

      audioPlayerView?.loadAndPlaySoundPostAudioIfPossible(
        isLifecycleChange = isLifecycleChange,
        isForceLoad = mediaPreloadResult.isForced,
        viewableMedia = viewableMedia
      )

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

    return canAutoLoad(cacheFileType = CacheFileType.PostMediaFull)
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

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
      if (actualImageView.visibility == View.VISIBLE) {
        mediaViewContract.onTapped()
        return true
      } else if (thumbnailMediaView.visibility == View.VISIBLE) {
        return tryPreloadingFunc()
      }

      return super.onSingleTapConfirmed(e)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
      doubleTapped = true
      return super.onDoubleTap(e)
    }

    override fun onLongPress(e: MotionEvent) {
      if (!doubleTapped) {
        onMediaLongClick()
      }
    }

    fun onUpOrCanceled() {
      doubleTapped = false
    }

  }

  class FullImageState(
    audioPlayerViewState: AudioPlayerView.AudioPlayerViewState = AudioPlayerView.AudioPlayerViewState()
  ) : MediaViewState(audioPlayerViewState) {

    override fun resetPosition() {
      super.resetPosition()

      audioPlayerViewState!!.resetPosition()
    }

    override fun clone(): MediaViewState {
      return FullImageState(
        audioPlayerViewState = audioPlayerViewState!!.clone() as AudioPlayerView.AudioPlayerViewState
      )
    }

    override fun updateFrom(other: MediaViewState?) {
      if (other !is FullImageState) {
        return
      }

      audioPlayerViewState!!.updateFrom(other.audioPlayerViewState)
    }
  }

  companion object {
    private const val TAG = "FullImageMediaView"
  }
}