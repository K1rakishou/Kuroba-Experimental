package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.animation.ValueAnimator
import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerToolbar
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.helper.ExoPlayerWrapper
import com.github.k1rakishou.chan.ui.widget.CancellableToast
import com.github.k1rakishou.chan.utils.AnimationUtils.fadeIn
import com.github.k1rakishou.chan.utils.AnimationUtils.fadeOut
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.TimeUtils
import com.github.k1rakishou.chan.utils.setEnabledFast
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.google.android.exoplayer2.upstream.DataSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class AudioPlayerView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : FrameLayout(context, attributeSet, defAttrStyle) {
  private lateinit var audioPlayerMuteUnmute: ImageButton
  private lateinit var audioPlayerPlayPause: ImageButton
  private lateinit var audioPlayerRestart: ImageButton
  private lateinit var audioPlayerControlsRoot: LinearLayout
  private lateinit var audioPlayerPositionDuration: TextView

  private var hasSoundPostUrl: Boolean = false
  private var positionAndDurationUpdateJob: Job? = null
  private var hideShowAnimation: ValueAnimator? = null
  private var audioPlayerCallbacks: AudioPlayerCallbacks? = null

  private lateinit var audioPlayerViewState: AudioPlayerViewState
  private lateinit var mediaViewContract: MediaViewContract
  private lateinit var cacheHandler: CacheHandler
  private lateinit var threadDownloadManager: ThreadDownloadManager
  private lateinit var cachedHttpDataSourceFactory: DataSource.Factory
  private lateinit var fileDataSourceFactory: DataSource.Factory
  private lateinit var contentDataSourceFactory: DataSource.Factory

  private val scope = KurobaCoroutineScope()
  private val cancellableToast by lazy { CancellableToast() }

  private val pauseInBg: Boolean
    get() = ChanSettings.mediaViewerPausePlayersWhenInBackground.get()

  private val soundPostVideoPlayerLazy = lazy {
    ExoPlayerWrapper(
      context = context,
      threadDownloadManager = threadDownloadManager,
      cachedHttpDataSourceFactory = cachedHttpDataSourceFactory,
      fileDataSourceFactory = fileDataSourceFactory,
      contentDataSourceFactory = contentDataSourceFactory,
      mediaViewContract = mediaViewContract,
      onAudioDetected = {}
    )
  }

  private val soundPostVideoPlayer: ExoPlayerWrapper
    get() = soundPostVideoPlayerLazy.value

  init {
    inflate(context, R.layout.audio_player_control_view, this)
  }

  fun bind(
    audioPlayerCallbacks: AudioPlayerCallbacks,
    viewableMedia: ViewableMedia,
    cacheHandler: CacheHandler,
    audioPlayerViewState: AudioPlayerViewState,
    mediaViewContract: MediaViewContract,
    threadDownloadManager: ThreadDownloadManager,
    cachedHttpDataSourceFactory: DataSource.Factory,
    fileDataSourceFactory: DataSource.Factory,
    contentDataSourceFactory: DataSource.Factory,
  ) {
    this.hasSoundPostUrl = viewableMedia.viewableMediaMeta.soundPostActualSoundMedia != null
    if (!hasSoundPostUrl) {
      return
    }

    this.audioPlayerCallbacks = audioPlayerCallbacks
    this.audioPlayerViewState = audioPlayerViewState
    this.mediaViewContract = mediaViewContract
    this.cacheHandler = cacheHandler
    this.threadDownloadManager = threadDownloadManager
    this.cachedHttpDataSourceFactory = cachedHttpDataSourceFactory
    this.fileDataSourceFactory = fileDataSourceFactory
    this.contentDataSourceFactory = contentDataSourceFactory

    audioPlayerControlsRoot = findViewById(R.id.audio_player_controls_view_root)
    audioPlayerMuteUnmute = findViewById(R.id.audio_player_mute_unmute)
    audioPlayerPlayPause = findViewById(R.id.audio_player_play_pause)
    audioPlayerRestart = findViewById(R.id.audio_player_restart)
    audioPlayerPositionDuration = findViewById(R.id.audio_player_position_duration)

    audioPlayerMuteUnmute.setEnabledFast(false)
    audioPlayerPlayPause.setEnabledFast(false)
    audioPlayerRestart.setEnabledFast(false)
    audioPlayerControlsRoot.setVisibilityFast(View.VISIBLE)

    audioPlayerMuteUnmute.setOnClickListener {
      mediaViewContract.toggleSoundMuteState()
      val isSoundCurrentlyMuted = mediaViewContract.isSoundCurrentlyMuted()

      if (isSoundCurrentlyMuted) {
        soundPostVideoPlayer.muteUnMute(true)
      } else {
        soundPostVideoPlayer.muteUnMute(false)
      }

      updateAudioIcon(isSoundCurrentlyMuted)
    }

    audioPlayerPlayPause.setOnClickListener {
      val isNowPlaying = soundPostVideoPlayer.isPlaying().not()

      pauseUnpause(isNowPaused = !isNowPlaying)
      updatePlayIcon(isNowPlaying = isNowPlaying)

      audioPlayerCallbacks.onAudioPlayerPlaybackChanged(isNowPaused = !isNowPlaying)
    }

    positionAndDurationUpdateJob?.cancel()
    positionAndDurationUpdateJob = scope.launch(start = CoroutineStart.LAZY) {
      soundPostVideoPlayer.positionAndDurationFlow.collect { (position, duration) ->
        updatePlayerPositionDuration(position, duration)
      }
    }

    audioPlayerRestart.setOnClickListener {
      soundPostVideoPlayer.resetPosition()
      audioPlayerCallbacks.onRewindPlayback()
    }
  }

  fun show(isLifecycleChange: Boolean) {
    if (!hasSoundPostUrl) {
      return
    }
  }

  fun hide(isLifecycleChange: Boolean, isPausing: Boolean, isBecomingInactive: Boolean) {
    if (!hasSoundPostUrl) {
      return
    }

    if (soundPostVideoPlayerLazy.isInitialized() && soundPostVideoPlayer.hasContent) {
      audioPlayerViewState.prevPosition = soundPostVideoPlayer.actualExoPlayer.currentPosition
      audioPlayerViewState.prevWindowIndex = soundPostVideoPlayer.actualExoPlayer.currentWindowIndex

      if (audioPlayerViewState.prevPosition <= 0 && audioPlayerViewState.prevWindowIndex <= 0) {
        // Reset the flag because (most likely) the user swiped through the pages so fast that the
        // player hasn't been able to start playing so it's still in some kind of BUFFERING state or
        // something like that so mainVideoPlayer.isPlaying() will return false which will cause the
        // player to appear paused if the user switches back to this page. We don't want that that's
        // why we are resetting the "playing" to null here.
        audioPlayerViewState.playing = null
      } else {
        audioPlayerViewState.playing = soundPostVideoPlayer.isPlaying()
      }

      val needPause = soundPostVideoPlayer.isPlaying() && ((isPausing && pauseInBg) || isBecomingInactive)
      if (needPause) {
        soundPostVideoPlayer.pause()
      }
    }
  }

  fun unbind() {
    hasSoundPostUrl = false
    audioPlayerCallbacks = null

    if (soundPostVideoPlayerLazy.isInitialized() && soundPostVideoPlayer.hasContent) {
      soundPostVideoPlayer.release()
    }

    positionAndDurationUpdateJob?.cancel()
    positionAndDurationUpdateJob = null

    cancellableToast.cancel()
    scope.cancelChildren()
  }

  fun onSystemUiVisibilityChanged(systemUIHidden: Boolean) {
    if (hasSoundPostUrl && soundPostVideoPlayer.hasContent) {
      if (systemUIHidden) {
        hideAudioPlayerView()
      } else {
        showAudioPlayerView()
      }
    }
  }

  fun pauseUnpause(isNowPaused: Boolean) {
    if (!hasSoundPostUrl) {
      return
    }

    if (soundPostVideoPlayerLazy.isInitialized()) {
      if (isNowPaused) {
        soundPostVideoPlayer.pause()
      } else {
        soundPostVideoPlayer.start()
      }

      updatePlayIcon(isNowPlaying = !isNowPaused)
    }
  }

  suspend fun loadAndPlaySoundPostAudioIfPossible(
    isLifecycleChange: Boolean,
    isForceLoad: Boolean,
    viewableMedia: ViewableMedia
  ) {
    if (!hasSoundPostUrl) {
      return
    }

    if (soundPostVideoPlayerLazy.isInitialized() && soundPostVideoPlayer.isPlaying()) {
      Logger.d(TAG, "loadAndPlaySoundPostAudioIfPossible() isPlaying == true")

      if (!mediaViewContract.isSystemUiHidden()) {
        showAudioPlayerView()
      }

      onAudioPlaying()
      return
    }

    val soundPostActualSoundMedia = viewableMedia.viewableMediaMeta.soundPostActualSoundMedia
    if (soundPostActualSoundMedia != null) {
      val canAutoLoad = MediaViewerControllerViewModel.canAutoLoad(
        cacheHandler = cacheHandler,
        viewableMedia = soundPostActualSoundMedia,
        cacheFileType = CacheFileType.PostMediaFull,
      )

      Logger.d(TAG, "loadAndPlaySoundPostAudioIfPossible() canAutoLoad: ${canAutoLoad}, isForceLoad: ${isForceLoad}, " +
        "soundPostActualSoundMedia: ${soundPostActualSoundMedia.mediaLocation}")

      if ((isForceLoad || canAutoLoad) && loadImageBgAudio(isLifecycleChange, soundPostActualSoundMedia)) {
        positionAndDurationUpdateJob?.start()

        if (!mediaViewContract.isSystemUiHidden()) {
          showAudioPlayerView()
        }

        onAudioPlaying()
        return
      } else if (!canAutoLoad) {
        val message = getString(
          R.string.media_viewer_error_cannot_load_bg_audio_because_of_settings,
          soundPostActualSoundMedia.mediaLocation.value
        )

        cancellableToast.showToast(context, message)
      }

      // fallthrough
    }

    hideAudioPlayerView()
  }

  private fun onAudioPlaying() {
    audioPlayerMuteUnmute.setEnabledFast(true)
    audioPlayerPlayPause.setEnabledFast(true)
    audioPlayerRestart.setEnabledFast(true)
    audioPlayerControlsRoot.setVisibilityFast(View.VISIBLE)

    updateAudioIcon(mediaViewContract.isSoundCurrentlyMuted())
    updatePlayIcon(soundPostVideoPlayer.isPlaying())
  }

  private suspend fun loadImageBgAudio(
    isLifecycleChange: Boolean,
    soundPostActualSoundMedia: ViewableMedia.Audio
  ): Boolean {
    try {
      if (!isLifecycleChange && ChanSettings.videoAlwaysResetToStart.get()) {
        audioPlayerViewState.resetPosition()
        soundPostVideoPlayer.resetPosition()
      }

      Logger.d(TAG, "loadImageBgAudio() preload()")
      cancellableToast.showToast(context, R.string.media_viewer_loading_bg_audio)

      soundPostVideoPlayer.preload(
        viewableMedia = soundPostActualSoundMedia,
        mediaLocation = soundPostActualSoundMedia.mediaLocation,
        prevPosition = audioPlayerViewState.prevPosition,
        prevWindowIndex = audioPlayerViewState.prevWindowIndex
      )

      if (audioPlayerViewState.playing == null || audioPlayerViewState.playing == true) {
        Logger.d(TAG, "loadImageBgAudio() startAndAwaitFirstFrame()")

        soundPostVideoPlayer.startAndAwaitFirstFrame(soundPostActualSoundMedia.mediaLocation)
      } else if (audioPlayerViewState.prevWindowIndex >= 0 && audioPlayerViewState.prevPosition >= 0) {
        // We need to do this hacky stuff to force exoplayer to show the video frame instead of nothing
        // after the activity is paused and then unpaused (like when the user turns off/on the phone
        // screen).
        val newPosition = (audioPlayerViewState.prevPosition - ExoPlayerWrapper.SEEK_POSITION_DELTA).coerceAtLeast(0)
        soundPostVideoPlayer.seekTo(audioPlayerViewState.prevWindowIndex, newPosition)
      }

      updatePlayerPositionDuration(
        soundPostVideoPlayer.actualExoPlayer.currentPosition,
        soundPostVideoPlayer.actualExoPlayer.duration
      )

      Logger.d(TAG, "loadImageBgAudio() success")
      return true
    } catch (error: Throwable) {
      Logger.e(TAG, "loadImageBgAudio() Failed to load image bg audio: ${soundPostActualSoundMedia.mediaLocation}", error)

      val errorMessage = getString(R.string.media_viewer_error_loading_bg_audio, error.errorMessageOrClassName())
      cancellableToast.showToast(context, errorMessage)

      return false
    }
  }

  private fun updateAudioIcon(soundCurrentlyMuted: Boolean) {
    val imageDrawable =  if (soundCurrentlyMuted) {
      R.drawable.ic_volume_off_white_24dp
    } else {
      R.drawable.ic_volume_up_white_24dp
    }

    audioPlayerMuteUnmute.setImageResource(imageDrawable)
  }

  private fun updatePlayIcon(isNowPlaying: Boolean) {
    val imageDrawable = if (isNowPlaying) {
      com.google.android.exoplayer2.ui.R.drawable.exo_controls_pause
    } else {
      com.google.android.exoplayer2.ui.R.drawable.exo_controls_play
    }

    audioPlayerPlayPause.setImageResource(imageDrawable)
  }

  private fun updatePlayerPositionDuration(position: Long, duration: Long) {
    val positionMsFormatted = TimeUtils.formatPeriod(position)
    val durationMsFormatted = TimeUtils.formatPeriod(duration)

    audioPlayerPositionDuration.setText("${positionMsFormatted} / $durationMsFormatted")
  }

  private fun hideAudioPlayerView() {
    hideShowAnimation = audioPlayerControlsRoot.fadeOut(
      duration = MediaViewerToolbar.ANIMATION_DURATION_MS,
      animator = hideShowAnimation,
      onEnd = { hideShowAnimation = null }
    )
  }

  private fun showAudioPlayerView() {
    hideShowAnimation = audioPlayerControlsRoot.fadeIn(
      duration = MediaViewerToolbar.ANIMATION_DURATION_MS,
      animator = hideShowAnimation,
      onEnd = { hideShowAnimation = null }
    )
  }

  @Parcelize
  class AudioPlayerViewState(
    var prevPosition: Long = 0,
    var prevWindowIndex: Int = 0,
    var playing: Boolean? = null
  ) : MediaViewState(null), Parcelable {

    override fun resetPosition() {
      super.resetPosition()

      prevPosition = 0
      prevWindowIndex = 0
    }

    override fun clone(): MediaViewState {
      return AudioPlayerViewState(prevPosition, prevWindowIndex, playing)
    }

    override fun updateFrom(other: MediaViewState?) {
      if (other !is AudioPlayerViewState) {
        return
      }

      this.prevPosition = other.prevPosition
      this.prevWindowIndex = other.prevWindowIndex
      this.playing = other.playing
    }
  }

  interface AudioPlayerCallbacks {
    fun onAudioPlayerPlaybackChanged(isNowPaused: Boolean)
    fun onRewindPlayback()
  }

  companion object {
    private const val TAG = "AudioPlayerView"
  }

}