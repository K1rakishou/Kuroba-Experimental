package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.helper.CloseMediaActionHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.ExoPlayerCustomPlayerView
import com.github.k1rakishou.chan.features.media_viewer.helper.ExoPlayerWrapper
import com.github.k1rakishou.chan.features.media_viewer.strip.MediaViewerActionStrip
import com.github.k1rakishou.chan.features.media_viewer.strip.MediaViewerBottomActionStrip
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableProgressBar
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet
import com.github.k1rakishou.chan.utils.setEnabledFast
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.awaitCatching
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.findChild
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.core_logger.Logger
import com.google.android.exoplayer2.upstream.DataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ExoPlayerVideoMediaView(
  context: Context,
  initialMediaViewState: VideoMediaViewState,
  mediaViewContract: MediaViewContract,
  private val viewModel: MediaViewerControllerViewModel,
  private val cachedHttpDataSourceFactory: DataSource.Factory,
  private val fileDataSourceFactory: DataSource.Factory,
  private val contentDataSourceFactory: DataSource.Factory,
  private val onThumbnailFullyLoadedFunc: () -> Unit,
  private val isSystemUiHidden: () -> Boolean,
  override val viewableMedia: ViewableMedia.Video,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int,
) : MediaView<ViewableMedia.Video, ExoPlayerVideoMediaView.VideoMediaViewState>(
  context = context,
  attributeSet = null,
  mediaViewContract = mediaViewContract,
  mediaViewState = initialMediaViewState,
  cachedHttpDataSourceFactory = cachedHttpDataSourceFactory,
  fileDataSourceFactory = fileDataSourceFactory,
  contentDataSourceFactory = contentDataSourceFactory,
) {

  private val thumbnailMediaView: ThumbnailMediaView
  private val actualVideoPlayerView: ExoPlayerCustomPlayerView
  private val bufferingProgressView: ColorizableProgressBar
  private val muteUnmuteButton: ImageButton
  private val actionStrip: MediaViewerActionStrip

  private val mainVideoPlayer by lazy {
    ExoPlayerWrapper(
      context = context,
      threadDownloadManager = threadDownloadManager,
      cachedHttpDataSourceFactory = cachedHttpDataSourceFactory,
      fileDataSourceFactory = fileDataSourceFactory,
      contentDataSourceFactory = contentDataSourceFactory,
      mediaViewContract = mediaViewContract,
      onAudioDetected = {
        updateAudioIcon(mediaViewContract.isSoundCurrentlyMuted())
        videoSoundDetected = true
      }
    )
  }

  private val closeMediaActionHelper: CloseMediaActionHelper
  private val gestureDetector: GestureDetector

  private var fullVideoDeferred = CompletableDeferred<Unit>()
  private var preloadingJob: Job? = null
  private var playJob: Job? = null
  private var videoSoundDetected = false

  override val hasContent: Boolean
    get() = mainVideoPlayer.hasContent
  override val mediaViewerActionStrip: MediaViewerActionStrip?
    get() = actionStrip

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_video_exo_player, this)
    setWillNotDraw(false)

    thumbnailMediaView = findViewById(R.id.thumbnail_media_view)
    actualVideoPlayerView = findViewById(R.id.actual_video_view)
    bufferingProgressView = findViewById(R.id.buffering_progress_view)

    if (isTablet()) {
      actionStrip = findViewById<MediaViewerBottomActionStrip?>(R.id.left_action_strip)
    } else {
      actionStrip = findViewById<MediaViewerBottomActionStrip?>(R.id.bottom_action_strip)
    }

    val placeholderView = findViewById<FrameLayout>(R.id.view_player_controls_placeholder)
    actualVideoPlayerView.setControllerPlaceholderView(placeholderView)

    muteUnmuteButton = findViewById(R.id.exo_mute)
    muteUnmuteButton.setEnabledFast(false)

    val movableContainer = findViewById<View>(R.id.exo_content_frame)
      ?: actualVideoPlayerView

    closeMediaActionHelper = CloseMediaActionHelper(
      context = context,
      themeEngine = themeEngine,
      requestDisallowInterceptTouchEvent = { this.parent.requestDisallowInterceptTouchEvent(true) },
      onAlphaAnimationProgress = { alpha -> mediaViewContract.changeMediaViewerBackgroundAlpha(alpha) },
      movableContainerFunc = {
         if (actualVideoPlayerView.visibility == View.VISIBLE) {
           movableContainer
         } else {
           thumbnailMediaView
         }
      },
      invalidateFunc = { invalidate() },
      closeMediaViewer = { mediaViewContract.closeMediaViewer() },
      topPaddingFunc = { toolbarHeight() },
      bottomPaddingFunc = { playerControlsHeight() },
      topGestureInfo = createGestureAction(isTopGesture = true),
      bottomGestureInfo = createGestureAction(isTopGesture = false)
    )

    gestureDetector = GestureDetector(
      context,
      GestureDetectorListener(
        thumbnailMediaView = thumbnailMediaView,
        actualVideoView = actualVideoPlayerView,
        mediaViewContract = mediaViewContract,
        tryPreloadingFunc = {
          val canForcePreload = canPreload(forced = true)

          if (viewableMedia.mediaLocation is MediaLocation.Remote && canForcePreload) {
            preloadingJob = startFullVideoPreloading(viewableMedia.mediaLocation)
            return@GestureDetectorListener true
          } else if (!canForcePreload) {
            mediaViewContract.onTapped()
            return@GestureDetectorListener true
          }

          return@GestureDetectorListener false
        },
        onMediaLongClick = { mediaViewContract.onMediaLongClick(this, viewableMedia) }
      )
    )

    muteUnmuteButton.setOnClickListener {
      if (!muteUnmuteButton.isEnabled) {
        return@setOnClickListener
      }

      mediaViewContract.toggleSoundMuteState()
      updateMuteUnMuteState()
    }

    thumbnailMediaView.setOnTouchListener { v, event ->
      if (thumbnailMediaView.visibility != View.VISIBLE) {
        return@setOnTouchListener false
      }

      // Always return true for thumbnails because otherwise gestures won't work with thumbnails
      gestureDetector.onTouchEvent(event)
      return@setOnTouchListener true
    }

    actualVideoPlayerView.setOnTouchListener { v, event ->
      if (actualVideoPlayerView.visibility != View.VISIBLE) {
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

    val canPreloadRemote = viewableMedia.mediaLocation is MediaLocation.Remote && canPreload(forced = false)
    val mediaIsLocal = viewableMedia.mediaLocation is MediaLocation.Local

    if (canPreloadRemote || mediaIsLocal) {
      preloadingJob = startFullVideoPreloading(viewableMedia.mediaLocation)
    }
  }

  override fun bind() {

  }

  override fun show(isLifecycleChange: Boolean) {
    super.updateComponentsWithViewableMedia(pagerPosition, totalPageItemsCount, viewableMedia)

    onSystemUiVisibilityChanged(isSystemUiHidden())
    updateMuteUnMuteState()
    thumbnailMediaView.show()

    if (playJob != null) {
      return
    }

    playJob = scope.launch {
      if (hasContent) {
        // Already loaded and ready to play
        switchToPlayerViewAndStartPlaying(isLifecycleChange)
        playJob = null

        return@launch
      }

      when (val fullVideoDeferredResult = fullVideoDeferred.awaitCatching()) {
        is ModularResult.Error -> {
          val error = fullVideoDeferredResult.error
          Logger.e(TAG, "onFullVideoLoadingError()", error)

          if (error.isExceptionImportant() && shown) {
            cancellableToast.showToast(
              context,
              getString(R.string.image_failed_video_error, error.errorMessageOrClassName())
            )
          }

          actualVideoPlayerView.setVisibilityFast(View.INVISIBLE)
        }
        is ModularResult.Value -> {
          if (hasContent) {
            switchToPlayerViewAndStartPlaying(isLifecycleChange)
          }
        }
      }

      playJob = null
    }
  }

  override fun hide(isLifecycleChange: Boolean, isPausing: Boolean, isBecomingInactive: Boolean) {
    thumbnailMediaView.hide()

    playJob?.cancel()
    playJob = null

    mediaViewState.prevPosition = mainVideoPlayer.actualExoPlayer.currentPosition
    mediaViewState.prevWindowIndex = mainVideoPlayer.actualExoPlayer.currentWindowIndex
    mediaViewState.videoSoundDetected = videoSoundDetected

    if (mediaViewState.prevPosition <= 0 && mediaViewState.prevWindowIndex <= 0) {
      // Reset the flag because (most likely) the user swiped through the pages so fast that the
      // player hasn't been able to start playing so it's still in some kind of BUFFERING state or
      // something like that so mainVideoPlayer.isPlaying() will return false which will cause the
      // player to appear paused if the user switches back to this page. We don't want that that's
      // why we are resetting the "playing" to null here.
      mediaViewState.playing = null
    } else {
      mediaViewState.playing = mainVideoPlayer.isPlaying()
    }

    val needPause = mainVideoPlayer.isPlaying() && ((isPausing && pauseInBg) || isBecomingInactive)
    if (needPause) {
      mainVideoPlayer.pause()
    }
  }

  override fun unbind() {
    thumbnailMediaView.unbind()
    mainVideoPlayer.release()
    closeMediaActionHelper.onDestroy()

    if (fullVideoDeferred.isActive) {
      fullVideoDeferred.cancel()
    }

    playJob?.cancel()
    playJob = null

    preloadingJob?.cancel()
    preloadingJob = null

    actualVideoPlayerView.player = null
  }

  override suspend fun reloadMedia() {
    if (preloadingJob != null) {
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

    fullVideoDeferred.cancel()
    fullVideoDeferred = CompletableDeferred<Unit>()
    playJob = null

    thumbnailMediaView.setVisibilityFast(View.VISIBLE)
    actualVideoPlayerView.setVisibilityFast(View.INVISIBLE)

    actualVideoPlayerView.player = null
    mainVideoPlayer.setNoContent()
    videoSoundDetected = false

    preloadingJob = startFullVideoPreloading(mediaLocation)
    show(isLifecycleChange = false)
  }

  override fun onSystemUiVisibilityChanged(systemUIHidden: Boolean) {
    super.onSystemUiVisibilityChanged(systemUIHidden)

    if (systemUIHidden) {
      actualVideoPlayerView.hideController()
    } else {
      actualVideoPlayerView.showController()
    }
  }

  override fun onInsetsChanged() {
  }

  private fun playerControlsHeight(): Int {
    val bottomInset = globalWindowInsetsManager.bottom()

    if (!actualVideoPlayerView.isControllerVisible) {
      return bottomInset
    }

    return actualVideoPlayerView
      .findChild { childView -> childView.id == R.id.exo_controls_view_root }
      ?.height
      ?: bottomInset
  }

  private fun updateAudioIcon(soundCurrentlyMuted: Boolean) {
    muteUnmuteButton.setEnabledFast(true)

    if (soundCurrentlyMuted) {
      muteUnmuteButton.setImageResource(R.drawable.ic_volume_off_white_24dp)
    } else {
      muteUnmuteButton.setImageResource(R.drawable.ic_volume_up_white_24dp)
    }
  }

  private fun startFullVideoPreloading(mediaLocation: MediaLocation): Job {
    return scope.launch {
      this@ExoPlayerVideoMediaView.videoSoundDetected = mediaViewState.videoSoundDetected == true

      val showBufferingJob = scope.launch {
        delay(125L)
        bufferingProgressView.setVisibilityFast(View.VISIBLE)
      }

      try {
        actualVideoPlayerView.setOnClickListener(null)
        actualVideoPlayerView.useController = true
        actualVideoPlayerView.controllerAutoShow = false
        actualVideoPlayerView.controllerHideOnTouch = false
        actualVideoPlayerView.controllerShowTimeoutMs = -1
        actualVideoPlayerView.setShowBuffering(ExoPlayerCustomPlayerView.SHOW_BUFFERING_NEVER)
        actualVideoPlayerView.useArtwork = false
        actualVideoPlayerView.setShutterBackgroundColor(Color.TRANSPARENT)
        actualVideoPlayerView.player = mainVideoPlayer.actualExoPlayer

        updateExoBufferingViewColors()

        mainVideoPlayer.preload(
          viewableMedia = viewableMedia,
          mediaLocation = mediaLocation,
          prevPosition = mediaViewState.prevPosition,
          prevWindowIndex = mediaViewState.prevWindowIndex
        )

        fullVideoDeferred.complete(Unit)
      } catch (error: Throwable) {
        fullVideoDeferred.completeExceptionally(error)
      } finally {
        preloadingJob = null

        showBufferingJob.cancel()
        bufferingProgressView.setVisibilityFast(View.INVISIBLE)
      }
    }
  }

  private fun updateMuteUnMuteState() {
    if (!videoSoundDetected) {
      return
    }

    val isSoundCurrentlyMuted = mediaViewContract.isSoundCurrentlyMuted()
    updateAudioIcon(isSoundCurrentlyMuted)
    mainVideoPlayer.muteUnMute(isSoundCurrentlyMuted)
  }

  private fun updateExoBufferingViewColors() {
    actualVideoPlayerView.findViewById<View>(R.id.exo_buffering)?.let { progressView ->
      (progressView as? ProgressBar)?.progressTintList =
        ColorStateList.valueOf(themeEngine.chanTheme.accentColor)
      (progressView as? ProgressBar)?.indeterminateTintList =
        ColorStateList.valueOf(themeEngine.chanTheme.accentColor)
    }
  }

  private suspend fun switchToPlayerViewAndStartPlaying(isLifecycleChange: Boolean) {
    actualVideoPlayerView.setVisibilityFast(VISIBLE)

    if (!isLifecycleChange && ChanSettings.videoAlwaysResetToStart.get()) {
      mediaViewState.resetPosition()
      mainVideoPlayer.resetPosition()
    }

    when {
      mediaViewState.playing == null || mediaViewState.playing == true -> {
        mainVideoPlayer.startAndAwaitFirstFrame(viewableMedia.mediaLocation)
      }
      mediaViewState.prevWindowIndex >= 0 && mediaViewState.prevPosition >= 0 -> {
        // We need to do this hacky stuff to force exoplayer to show the video frame instead of nothing
        // after the activity is paused and then unpaused (like when the user turns off/on the phone
        // screen).
        val newPosition = (mediaViewState.prevPosition - ExoPlayerWrapper.SEEK_POSITION_DELTA).coerceAtLeast(0)
        mainVideoPlayer.seekTo(mediaViewState.prevWindowIndex, newPosition)
      }
    }

    actualVideoPlayerView.useArtwork = mainVideoPlayer.hasNoVideo()
    if (actualVideoPlayerView.useArtwork) {
      actualVideoPlayerView.defaultArtwork = mediaViewContract.defaultArtworkDrawable()
    }

    thumbnailMediaView.setVisibilityFast(INVISIBLE)
  }

  private fun canPreload(forced: Boolean): Boolean {
    if (forced) {
      return !fullVideoDeferred.isCompleted
        && (preloadingJob == null || preloadingJob?.isActive == false)
    }

    return canAutoLoad(cacheFileType = CacheFileType.PostMediaFull)
      && !fullVideoDeferred.isCompleted
      && (preloadingJob == null || preloadingJob?.isActive == false)
  }

  class VideoMediaViewState(
    var prevPosition: Long = -1,
    var prevWindowIndex: Int = -1,
    var videoSoundDetected: Boolean? = null,
    var playing: Boolean? = null
  ) : MediaViewState() {

    override fun resetPosition() {
      super.resetPosition()

      prevPosition = -1
      prevWindowIndex = -1
    }

    override fun clone(): MediaViewState {
      return VideoMediaViewState(prevPosition, prevWindowIndex, videoSoundDetected, playing)
    }

    override fun updateFrom(other: MediaViewState?) {
      if (other == null) {
        prevPosition = -1
        prevWindowIndex = -1
        videoSoundDetected = null
        playing = null
        return
      }

      if (other !is VideoMediaViewState) {
        return
      }

      this.prevPosition = other.prevPosition
      this.prevWindowIndex = other.prevWindowIndex
      this.videoSoundDetected = other.videoSoundDetected
      this.playing = other.playing
    }
  }

  class GestureDetectorListener(
    private val thumbnailMediaView: ThumbnailMediaView,
    private val actualVideoView: ExoPlayerCustomPlayerView,
    private val mediaViewContract: MediaViewContract,
    private val tryPreloadingFunc: () -> Boolean,
    private val onMediaLongClick: () -> Unit
  ) : GestureDetector.SimpleOnGestureListener() {

    override fun onDown(e: MotionEvent?): Boolean {
      return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
      if (actualVideoView.visibility == View.VISIBLE) {
        mediaViewContract.onTapped()
        return true
      } else if (thumbnailMediaView.visibility == View.VISIBLE) {
        if (tryPreloadingFunc()) {
          return true
        }

        mediaViewContract.onTapped()
        return true
      }

      return false
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
      if (e == null || actualVideoView.visibility != View.VISIBLE) {
        return false
      }

      val exoPlayer = actualVideoView.player
        ?: return false

      exoPlayer.playWhenReady = exoPlayer.playWhenReady.not()
      return true
    }

    override fun onLongPress(e: MotionEvent?) {
      onMediaLongClick()
    }
  }

  companion object {
    private const val TAG = "VideoMediaView"
  }
}