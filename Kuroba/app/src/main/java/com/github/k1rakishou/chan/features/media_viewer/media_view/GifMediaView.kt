package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
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
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.awaitCatching
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.file.FileDescriptorMode
import com.google.android.exoplayer2.upstream.DataSource
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
  cachedHttpDataSourceFactory: DataSource.Factory,
  fileDataSourceFactory: DataSource.Factory,
  contentDataSourceFactory: DataSource.Factory,
  override val viewableMedia: ViewableMedia.Gif,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<ViewableMedia.Gif, GifMediaView.GifMediaViewState>(
  context = context,
  attributeSet = null,
  mediaViewContract = mediaViewContract,
  mediaViewState = initialMediaViewState,
  cachedHttpDataSourceFactory = cachedHttpDataSourceFactory,
  fileDataSourceFactory = fileDataSourceFactory,
  contentDataSourceFactory = contentDataSourceFactory,
) {

  private val movableContainer: FrameLayout
  private val thumbnailMediaView: ThumbnailMediaView
  private val actualGifView: GifImageView
  private val loadingBar: CircularChunkedLoadingBar
  private val actionStrip: MediaViewerActionStrip

  private val closeMediaActionHelper: CloseMediaActionHelper
  private val gestureDetector: GestureDetector

  private var fullGifDeferred = CompletableDeferred<MediaPreloadResult>()
  private var preloadCancelableDownload: CancelableDownload? = null

  override val hasContent: Boolean
    get() = actualGifView.drawable != null
  override val mediaViewerActionStrip: MediaViewerActionStrip?
    get() = actionStrip

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_gif, this)
    setWillNotDraw(false)

    movableContainer = findViewById(R.id.movable_container)
    thumbnailMediaView = findViewById(R.id.thumbnail_media_view)
    actualGifView = findViewById(R.id.actual_gif_view)
    loadingBar = findViewById(R.id.loading_bar)

    if (isTablet()) {
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

    gestureDetector = GestureDetector(
      context,
      GestureDetectorListener(
        thumbnailMediaView = thumbnailMediaView,
        actualGifView = actualGifView,
        mediaViewContract = mediaViewContract,
        tryPreloadingFunc = {
          val canForcePreload = canPreload(forced = true)

          if (viewableMedia.mediaLocation is MediaLocation.Remote && canForcePreload) {
            scope.launch {
              preloadCancelableDownload = startFullMediaPreloading(
                forced = true,
                loadingBar = loadingBar,
                mediaLocationRemote = viewableMedia.mediaLocation,
                fullMediaDeferred = fullGifDeferred,
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
        onMediaLongClick = { mediaViewContract.onMediaLongClick(this, viewableMedia) },
        pauseUnpauseGifFunc = {
          val isNowPlaying = (actualGifView.drawable as? GifDrawable)?.isPlaying?.not()
            ?: return@GestureDetectorListener

          onPauseUnpauseButtonToggled(isNowPlaying = isNowPlaying)
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
          forced = false,
          loadingBar = loadingBar,
          mediaLocationRemote = viewableMedia.mediaLocation,
          fullMediaDeferred = fullGifDeferred,
          onEndFunc = { preloadCancelableDownload = null }
        )
      }
    } else if (viewableMedia.mediaLocation is MediaLocation.Local) {
      val mediaPreloadResult = MediaPreloadResult(
        filePath = FilePath.JavaPath(viewableMedia.mediaLocation.path),
        isForced = false
      )

      fullGifDeferred.complete(mediaPreloadResult)
    }
  }

  override fun bind() {

  }

  override fun show(isLifecycleChange: Boolean) {
    super.updateComponentsWithViewableMedia(pagerPosition, totalPageItemsCount, viewableMedia)
    onSystemUiVisibilityChanged(isSystemUiHidden())
    thumbnailMediaView.show()

    scope.launch {
      val fullGifDeferredResult = fullGifDeferred.awaitCatching()
      super.updateComponentsWithViewableMedia(pagerPosition, totalPageItemsCount, viewableMedia)

      when (fullGifDeferredResult) {
        is ModularResult.Error -> {
          val error = fullGifDeferredResult.error
          Logger.e(TAG, "onFullGifLoadingError()", error)

          if (error.isExceptionImportant() && shown) {
            cancellableToast.showToast(
              context,
              AppModuleAndroidUtils.getString(R.string.image_failed_gif_error, error.errorMessageOrClassName())
            )
          }

          actualGifView.setVisibilityFast(View.INVISIBLE)
        }
        is ModularResult.Value -> {
          val mediaPreloadResult = fullGifDeferredResult.value

          if (!hasContent) {
            val filePath = mediaPreloadResult.filePath
            if (!setBigGifFromFile(filePath)) {
              return@launch
            }
          }

          withContext(Dispatchers.Default) {
            val fileSize = mediaPreloadResult.filePath.fileSize(fileManager)
            if (fileSize != null) {
              viewableMedia.viewableMediaMeta.mediaOnDiskSize = fileSize
            }
          }

          audioPlayerView?.loadAndPlaySoundPostAudioIfPossible(
            isLifecycleChange = isLifecycleChange,
            isForceLoad = fullGifDeferredResult.value.isForced,
            viewableMedia = viewableMedia
          )
        }
      }

      loadingBar.setVisibilityFast(GONE)

      onUpdateTransparency()

      val gifImageViewDrawable = actualGifView.drawable as? GifDrawable
      if (gifImageViewDrawable != null) {
        if (!isLifecycleChange && ChanSettings.videoAlwaysResetToStart.get()) {
          mediaViewState.resetPosition()
        }

        val playing = mediaViewState.playing ?: true
        if (playing) {
          gifImageViewDrawable.start()
        } else {
          gifImageViewDrawable.pause()
        }

        gifImageViewDrawable.seekToFrame(mediaViewState.prevFrameIndex)
      }
    }
  }

  override fun hide(isLifecycleChange: Boolean, isPausing: Boolean, isBecomingInactive: Boolean) {
    thumbnailMediaView.hide()

    val gifImageViewDrawable = actualGifView.drawable as? GifDrawable
    if (gifImageViewDrawable != null) {
      mediaViewState.prevFrameIndex = gifImageViewDrawable.currentFrameIndex
      mediaViewState.playing = gifImageViewDrawable.isPlaying

      if (gifImageViewDrawable.isPlaying) {
        gifImageViewDrawable.pause()
      }
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

    cacheHandler.get().deleteCacheFileByUrlSuspend(
      cacheFileType = CacheFileType.PostMediaFull,
      url = mediaLocation.url.toString()
    )

    fullGifDeferred.cancel()
    fullGifDeferred = CompletableDeferred<MediaPreloadResult>()

    audioPlayerView?.pauseUnpause(isNowPaused = true)

    thumbnailMediaView.setVisibilityFast(View.VISIBLE)
    actualGifView.setVisibilityFast(View.INVISIBLE)
    actualGifView.setImageDrawable(null)

    preloadCancelableDownload = startFullMediaPreloading(
      forced = true,
      loadingBar = loadingBar,
      mediaLocationRemote = mediaLocation,
      fullMediaDeferred = fullGifDeferred,
      onEndFunc = { preloadCancelableDownload = null }
    )

    show(isLifecycleChange = false)
  }

  override fun onAudioPlayerPlaybackChanged(isNowPaused: Boolean) {
    pauseUnpauseGif(isNowPaused)
  }

  @Suppress("IfThenToSafeAccess")
  override fun onRewindPlayback() {
    val gifImageViewDrawable = actualGifView.drawable as? GifDrawable
    if (gifImageViewDrawable != null) {
      gifImageViewDrawable.seekTo(0)
    }
  }

  private fun onPauseUnpauseButtonToggled(isNowPlaying: Boolean) {
    pauseUnpauseGif(isNowPaused = !isNowPlaying)
    audioPlayerView?.pauseUnpause(isNowPaused = !isNowPlaying)
  }

  private fun pauseUnpauseGif(isNowPaused: Boolean) {
    val gifImageViewDrawable = actualGifView.drawable as? GifDrawable
    if (gifImageViewDrawable != null) {
      if (isNowPaused) {
        gifImageViewDrawable.pause()
      } else {
        gifImageViewDrawable.start()
      }
    }
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  private suspend fun setBigGifFromFile(filePath: FilePath): Boolean {
    return coroutineScope {
      val drawable = try {
        createGifDrawableSafe(filePath)
      } catch (e: Throwable) {
        Logger.e(TAG, "Error while trying to set a gif file", e)

        if (shown) {
          cancellableToast.showToast(context, "Failed to draw Gif. Error: ${e.message}")
        }

        return@coroutineScope true
      }

      if (drawable.numberOfFrames == 1) {
        val imageMedia = ViewableMedia.Image(
          mediaLocation = viewableMedia.mediaLocation,
          previewLocation = viewableMedia.previewLocation,
          spoilerLocation = viewableMedia.spoilerLocation,
          viewableMediaMeta = viewableMedia.viewableMediaMeta,
        )

        mediaViewContract.reloadAs(pagerPosition, imageMedia)
        return@coroutineScope false
      }

      drawable.pause()

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
      return@coroutineScope true
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

    return canAutoLoad(cacheFileType = CacheFileType.PostMediaFull)
      && !fullGifDeferred.isCompleted
      && (preloadCancelableDownload == null || preloadCancelableDownload?.isRunning() == false)
  }

  class GifMediaViewState(
    var prevFrameIndex: Int = 0,
    var playing: Boolean? = null,
    audioPlayerViewState: AudioPlayerView.AudioPlayerViewState = AudioPlayerView.AudioPlayerViewState()
  ) : MediaViewState(audioPlayerViewState) {

    override fun resetPosition() {
      super.resetPosition()

      prevFrameIndex = 0
      playing = null
      audioPlayerViewState!!.resetPosition()
    }

    override fun clone(): MediaViewState {
      return GifMediaViewState(
        prevFrameIndex = prevFrameIndex,
        playing = playing,
        audioPlayerViewState = audioPlayerViewState!!.clone() as AudioPlayerView.AudioPlayerViewState
      )
    }

    override fun updateFrom(other: MediaViewState?) {
      if (other !is GifMediaViewState) {
        return
      }

      prevFrameIndex = other.prevFrameIndex
      playing = other.playing
      audioPlayerViewState!!.updateFrom(other.audioPlayerViewState)
    }
  }

  class GestureDetectorListener(
    private val thumbnailMediaView: ThumbnailMediaView,
    private val actualGifView: GifImageView,
    private val mediaViewContract: MediaViewContract,
    private val tryPreloadingFunc: () -> Boolean,
    private val onMediaLongClick: () -> Unit,
    private val pauseUnpauseGifFunc: () -> Unit
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

      pauseUnpauseGifFunc()

      return super.onDoubleTap(e)
    }

    override fun onLongPress(e: MotionEvent?) {
      onMediaLongClick()
    }
  }

  private class GifIsTooBigException(sizeInBytes: Long)
    : Exception("Gif is too big! (${sizeInBytes / (1024 * 1024)} MB)")

  companion object {
    private const val TAG = "GifMediaView"
    // 99 MB. Max - 1 just in case. The max allowed by Android Canvas is 100 MB.
    private const val MAX_GIF_SIZE = 99 * 1024 * 1024
  }
}