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
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerToolbar
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.helper.CloseMediaActionHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.ExoPlayerWrapper
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableProgressBar
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.setEnabledFast
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.awaitCatching
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.findChild
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.common.updateHeight
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_logger.Logger
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class VideoMediaView(
  context: Context,
  initialMediaViewState: VideoMediaViewState,
  mediaViewContract: MediaViewContract,
  private val viewModel: MediaViewerControllerViewModel,
  private val cacheDataSourceFactory: DataSource.Factory,
  private val onThumbnailFullyLoadedFunc: () -> Unit,
  private val isSystemUiHidden: () -> Boolean,
  override val viewableMedia: ViewableMedia.Video,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int,
) : MediaView<ViewableMedia.Video, VideoMediaView.VideoMediaViewState>(
  context = context,
  attributeSet = null,
  cacheDataSourceFactory = cacheDataSourceFactory,
  mediaViewContract = mediaViewContract,
  mediaViewState = initialMediaViewState
), WindowInsetsListener {

  @Inject
  lateinit var cacheHandler: CacheHandler
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val thumbnailMediaView: ThumbnailMediaView
  private val actualVideoPlayerView: PlayerView
  private val bufferingProgressView: ColorizableProgressBar
  private val muteUnmuteButton: ImageButton

  private val mainVideoPlayer = ExoPlayerWrapper(
    context = context,
    cacheDataSourceFactory = cacheDataSourceFactory,
    mediaViewContract = mediaViewContract,
    onAudioDetected = {
      updateAudioIcon(mediaViewContract.isSoundCurrentlyMuted())
      videoSoundDetected = true
    }
  )

  private val closeMediaActionHelper: CloseMediaActionHelper
  private val gestureDetector: GestureDetector
  private val canAutoLoad by lazy { MediaViewerControllerViewModel.canAutoLoad(cacheHandler, viewableMedia) }

  private var fullVideoDeferred = CompletableDeferred<Unit>()
  private var preloadingJob: Job? = null
  private var playJob: Job? = null
  private var videoSoundDetected = false

  override val hasContent: Boolean
    get() = mainVideoPlayer.hasContent

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_video, this)
    setWillNotDraw(false)

    thumbnailMediaView = findViewById(R.id.thumbnail_media_view)
    actualVideoPlayerView = findViewById(R.id.actual_video_view)
    bufferingProgressView = findViewById(R.id.buffering_progress_view)
    val toolbar = findViewById<MediaViewerToolbar>(R.id.full_video_view_toolbar)
    initToolbar(toolbar)

    muteUnmuteButton = actualVideoPlayerView.findViewById(R.id.exo_mute)
    muteUnmuteButton.setEnabledFast(false)

    val movableContainer = actualVideoPlayerView.findViewById<View>(R.id.exo_content_frame)
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

    gestureDetector = GestureDetector(
      context,
      GestureDetectorListener(
        thumbnailMediaView = thumbnailMediaView,
        actualVideoView = actualVideoPlayerView,
        mediaViewContract = mediaViewContract,
        tryPreloadingFunc = {
          if (viewableMedia.mediaLocation is MediaLocation.Remote && canPreload(forced = true)) {
            preloadingJob = startFullVideoPreloading(viewableMedia.mediaLocation)
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
        onThumbnailFullyLoaded()
      }
    )

    if (viewableMedia.mediaLocation is MediaLocation.Remote && canPreload(forced = false)) {
      preloadingJob = startFullVideoPreloading(viewableMedia.mediaLocation)
    }
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
      this@VideoMediaView.videoSoundDetected = mediaViewState.videoSoundDetected == true

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
        actualVideoPlayerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        actualVideoPlayerView.useArtwork = false
        actualVideoPlayerView.setShutterBackgroundColor(Color.TRANSPARENT)
        actualVideoPlayerView.player = mainVideoPlayer.actualExoPlayer

        updatePlayerControlsInsets()
        updateExoBufferingViewColors()

        mainVideoPlayer.preload(mediaLocation, mediaViewState.prevPosition, mediaViewState.prevWindowIndex)
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

  override fun bind() {
    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  override fun show() {
    mediaViewToolbar?.updateWithViewableMedia(pagerPosition, totalPageItemsCount, viewableMedia)

    onSystemUiVisibilityChanged(isSystemUiHidden())
    updateMuteUnMuteState()

    if (playJob == null) {
      playJob = scope.launch {
        if (hasContent) {
          // Already loaded and ready to play
          switchToPlayerViewAndStartPlaying()
        } else {
          fullVideoDeferred.awaitCatching()
            .onFailure { error ->
              Logger.e(TAG, "onFullVideoLoadingError()", error)

              if (error.isExceptionImportant()) {
                cancellableToast.showToast(
                  context,
                  getString(R.string.image_failed_video_error, error.errorMessageOrClassName())
                )
              }

              actualVideoPlayerView.setVisibilityFast(View.INVISIBLE)
              thumbnailMediaView.setError(error.errorMessageOrClassName())
            }
            .onSuccess {
              if (hasContent) {
                switchToPlayerViewAndStartPlaying()
              }
            }
        }

        playJob = null
      }
    }
  }

  override fun hide() {
    playJob?.cancel()
    playJob = null

    mediaViewState.prevPosition = mainVideoPlayer.actualExoPlayer.currentPosition
    mediaViewState.prevWindowIndex = mainVideoPlayer.actualExoPlayer.currentWindowIndex
    mediaViewState.videoSoundDetected = videoSoundDetected
    mediaViewState.playing = mainVideoPlayer.isPlaying()

    mainVideoPlayer.pause()
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
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  override suspend fun onReloadButtonClick() {
    if (preloadingJob != null) {
      return
    }

    val mediaLocation = viewableMedia.mediaLocation
    if (mediaLocation !is MediaLocation.Remote) {
      return
    }

    cacheHandler.deleteCacheFileByUrl(mediaLocation.url.toString())

    fullVideoDeferred.cancel()
    fullVideoDeferred = CompletableDeferred<Unit>()
    playJob = null

    thumbnailMediaView.setVisibilityFast(View.VISIBLE)
    actualVideoPlayerView.setVisibilityFast(View.INVISIBLE)

    actualVideoPlayerView.player = null
    mainVideoPlayer.setNoContent()
    videoSoundDetected = false

    preloadingJob = startFullVideoPreloading(mediaLocation)
    show()
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
    updatePlayerControlsInsets()
  }

  private fun updateMuteUnMuteState() {
    if (!videoSoundDetected) {
      return
    }

    val isSoundCurrentlyMuted = mediaViewContract.isSoundCurrentlyMuted()
    updateAudioIcon(isSoundCurrentlyMuted)
    mainVideoPlayer.muteUnMute(isSoundCurrentlyMuted)
  }

  @Suppress("IfThenToSafeAccess")
  private fun updatePlayerControlsInsets() {
    val insetsView = actualVideoPlayerView
      .findChild { childView -> childView.id == R.id.exo_controls_insets_view }
      as? FrameLayout

    if (insetsView != null) {
      insetsView.updateHeight(globalWindowInsetsManager.bottom())
    }

    val rootView = actualVideoPlayerView
      .findChild { childView -> childView.id == R.id.exo_controls_view_root }
      as? LinearLayout

    if (rootView != null) {
      rootView.updatePaddings(
        left = globalWindowInsetsManager.left(),
        right = globalWindowInsetsManager.right()
      )
    }
  }

  private fun updateExoBufferingViewColors() {
    actualVideoPlayerView.findViewById<View>(R.id.exo_buffering)?.let { progressView ->
      (progressView as? ProgressBar)?.progressTintList =
        ColorStateList.valueOf(themeEngine.chanTheme.accentColor)
      (progressView as? ProgressBar)?.indeterminateTintList =
        ColorStateList.valueOf(themeEngine.chanTheme.accentColor)
    }
  }

  private suspend fun switchToPlayerViewAndStartPlaying() {
    actualVideoPlayerView.setVisibilityFast(VISIBLE)

    if (mediaViewState.playing == null || mediaViewState.playing == true) {
      mainVideoPlayer.startAndAwaitFirstFrame()
    } else if (mediaViewState.prevWindowIndex >= 0 && mediaViewState.prevPosition >= 0) {
      // We need to do this hacky stuff to force exoplayer to show the video frame instead of nothing
      // after the activity is paused and then unpaused (like when the user turns off/on the phone
      // screen).
      val newPosition = (mediaViewState.prevPosition - SEEK_POSITION_DELTA).coerceAtLeast(0)
      mainVideoPlayer.seekTo(mediaViewState.prevWindowIndex, newPosition)
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

    return canAutoLoad
      && !fullVideoDeferred.isCompleted
      && (preloadingJob == null || preloadingJob?.isActive == false)
  }

  class VideoMediaViewState(
    var prevPosition: Long = -1,
    var prevWindowIndex: Int = -1,
    var videoSoundDetected: Boolean? = null,
    var playing: Boolean? = null
  ) : MediaViewState {
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
    private val actualVideoView: PlayerView,
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
    private const val SEEK_POSITION_DELTA = 100
  }
}