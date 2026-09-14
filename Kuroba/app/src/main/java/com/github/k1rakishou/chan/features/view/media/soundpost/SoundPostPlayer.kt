package com.github.k1rakishou.chan.features.view.media.soundpost

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.features.view.media.MediaLocation
import com.github.k1rakishou.chan.features.view.media.MediaViewerControllerViewModel
import com.github.k1rakishou.chan.features.view.media.ViewableMedia
import com.github.k1rakishou.chan.features.view.media.element.MediaViewContract
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isCancellationException
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettings
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Renderer
import com.google.android.exoplayer2.RenderersFactory
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.min

class SoundPostPlayer(
  private val context: Context,
  private val kurobaSettings: KurobaSettings,
  private val cacheHandler: CacheHandler,
  private val proxyStorage: ProxyStorage,
  private val dialogFactory: DialogFactory,
  private val soundPostAudioDownloader: SoundPostAudioDownloader,
  private val snackbarManager: SnackbarManager,
  private val mediaViewContract: MediaViewContract,
  private val ownerMedia: ViewableMedia,
  private val soundMedia: ViewableMedia.Audio,
  private val state: State
) {
  private val scope = KurobaCoroutineScope()

  private var audioPlayer: ExoPlayer? = null
  private var loadJob: Job? = null
  private var syncJob: Job? = null

  private var attached = false
  private var target: SoundPostSyncTarget? = null
  private var autoLoop = false

  // Sync bookkeeping. Only valid while attached.
  private var restorePending = false
  private var prevSample: TargetSample? = null
  private var lastCorrectionTimeMs = 0L
  private var cycleEnded = false

  private val _uiState = MutableStateFlow<UiState>(UiState.Hidden)
  val uiState: StateFlow<UiState>
    get() = _uiState.asStateFlow()

  /**
   * Must be called when the owner page is shown and its media is ready to be played. Can be called
   * multiple times (every call re-synchronizes the audio with the [target]).
   * */
  fun attach(target: SoundPostSyncTarget?, isForced: Boolean, isLifecycleChange: Boolean) {
    BackgroundUtils.ensureMainThread()

    if (!isLifecycleChange && kurobaSettings.application.videoAlwaysResetToStart.readBlocking()) {
      state.resetPosition()
    }

    this.attached = true
    this.target = target
    this.autoLoop = kurobaSettings.application.videoAutoLoop.readBlocking()
    resetSyncState()

    if (audioPlayer != null) {
      startSyncLoop()
      return
    }

    if (loadJob != null) {
      // The sync loop will be started once loaded
      return
    }

    val canAutoLoad = isForced || MediaViewerControllerViewModel.canAutoLoad(
      kurobaSettings = kurobaSettings,
      cacheHandler = cacheHandler,
      viewableMedia = soundMedia,
      cacheFileType = CacheFileType.PostMediaFull
    )

    Logger.d(TAG, "attach() canAutoLoad: ${canAutoLoad}, isForced: ${isForced}, " +
      "hasTarget: ${target != null}, sound: ${soundMedia.mediaLocation}")

    if (!canAutoLoad) {
      _uiState.value = UiState.NotLoaded
      return
    }

    load(isManual = false)
  }

  /**
   * Must be called when the owner page is hidden.
   * */
  fun onHide(isPausing: Boolean, isBecomingInactive: Boolean) {
    BackgroundUtils.ensureMainThread()

    val player = audioPlayer
    if (player != null) {
      state.audioPositionMs = player.currentPosition
      if (target == null) {
        state.playing = player.playWhenReady
      }
    }

    val pauseInBg = kurobaSettings.application.mediaViewerPausePlayersWhenInBackground.readBlocking()
    val keepPlaying = isPausing && !pauseInBg && !isBecomingInactive
    if (keepPlaying) {
      // The app went to background but players are allowed to play in background. No other page can
      // become active meanwhile so it's safe to keep synchronizing.
      return
    }

    detach()
  }

  /**
   * Stops the synchronization and pauses the audio but keeps it loaded (e.g. when the owner media is
   * being reloaded). [attach] must be called again to resume.
   * */
  fun detach() {
    BackgroundUtils.ensureMainThread()

    attached = false
    target = null

    syncJob?.cancel()
    syncJob = null

    audioPlayer?.pause()
  }

  fun release() {
    BackgroundUtils.ensureMainThread()

    detach()

    loadJob?.cancel()
    loadJob = null
    scope.cancelChildren()

    audioPlayer?.release()
    audioPlayer = null

    _uiState.value = UiState.Hidden
  }

  /**
   * Manually loads the sound when it wasn't loaded automatically (media load settings, proxy warning)
   * or when loading has failed.
   * */
  fun onLoadClicked() {
    BackgroundUtils.ensureMainThread()

    if (!attached || audioPlayer != null || loadJob != null) {
      return
    }

    load(isManual = true)
  }

  fun onPlayPauseClicked() {
    BackgroundUtils.ensureMainThread()

    if (!attached) {
      return
    }

    val player = audioPlayer
    if (player == null) {
      onLoadClicked()
      return
    }

    val currentTarget = target
    if (currentTarget != null) {
      // The target is the source of truth, the audio will follow it
      if (!currentTarget.isReady()) {
        return
      }

      if (currentTarget.isPlaying()) {
        currentTarget.pause()
      } else {
        currentTarget.play()
      }
    } else {
      if (player.playWhenReady) {
        player.pause()
      } else {
        if (player.playbackState == Player.STATE_ENDED) {
          player.seekTo(0)
        }

        player.play()
      }
    }

    tick()
  }

  fun onRestartClicked() {
    BackgroundUtils.ensureMainThread()

    val player = audioPlayer
    if (!attached || player == null) {
      return
    }

    resetSyncState()
    restorePending = false
    player.seekTo(0)

    val currentTarget = target
    if (currentTarget != null) {
      if (currentTarget.isReady()) {
        currentTarget.seekTo(0)

        if (!currentTarget.isPlaying()) {
          currentTarget.play()
        }
      }
    } else {
      player.play()
    }

    tick()
  }

  fun onMuteUnmuteClicked() {
    BackgroundUtils.ensureMainThread()

    mediaViewContract.toggleSoundMuteState()
    tick()
  }

  private fun load(isManual: Boolean) {
    val remoteLocation = soundMedia.mediaLocation as? MediaLocation.Remote
    if (remoteLocation == null) {
      Logger.e(TAG, "load() unsupported sound media location: ${soundMedia.mediaLocation}")
      return
    }

    loadJob = scope.launch {
      try {
        // Only warn about the proxy when loading automatically, when the user presses the play button
        // they already know what's going to happen.
        if (!isManual && !checkCanLoadWithProxy(remoteLocation)) {
          _uiState.value = UiState.NotLoaded
          return@launch
        }

        _uiState.value = UiState.Loading(progress = null)

        val audioFile = soundPostAudioDownloader.download(remoteLocation.url) { progress ->
          _uiState.value = UiState.Loading(progress = progress)
        }

        val player = createAudioPlayer(audioFile)

        try {
          withTimeout(MAX_PREPARE_WAIT_TIME_MS) { awaitPlayerReady(player) }
        } catch (error: Throwable) {
          player.release()
          throw error
        }

        audioPlayer = player
        _uiState.value = UiState.Ready(positionMs = 0, durationMs = 0, isPlaying = false, isMuted = false)

        Logger.d(TAG, "load() success, duration: ${player.duration}, sound: ${soundMedia.mediaLocation}")
        startSyncLoop()
      } catch (error: Throwable) {
        if (error.isCancellationException()) {
          throw error
        }

        Logger.e(TAG, "load() error, sound: ${soundMedia.mediaLocation}", error)
        _uiState.value = UiState.Error

        snackbarManager.errorToast(
          message = getString(R.string.media_viewer_error_loading_bg_audio, error.errorMessageOrClassName())
        )
      } finally {
        loadJob = null
      }
    }
  }

  private suspend fun checkCanLoadWithProxy(remoteLocation: MediaLocation.Remote): Boolean {
    val siteDescriptor = ownerMedia.viewableMediaMeta.ownerPostDescriptor?.siteDescriptor()
      ?: return true

    val hasEnabledProxies = withContext(Dispatchers.IO) {
      proxyStorage.hasEnabledProxiesForSite(siteDescriptor)
    }

    if (!hasEnabledProxies) {
      return true
    }

    return proxyWarningMutex.withLock {
      val prevDecision = proxyWarningDecision
      if (prevDecision != null) {
        return@withLock prevDecision
      }

      val decision = showProxyWarningDialog(remoteLocation.url.host)
        ?: return@withLock false

      // Intentionally not persisted, the user should see the warning after every app restart
      proxyWarningDecision = decision
      return@withLock decision
    }
  }

  private suspend fun showProxyWarningDialog(host: String): Boolean? {
    return suspendCancellableCoroutine { continuation ->
      var resumed = false

      fun finish(decision: Boolean?) {
        if (resumed) {
          return
        }

        resumed = true
        continuation.resume(decision)
      }

      val dialogHandle = dialogFactory.createSimpleConfirmationDialog(
        context = context,
        titleTextId = R.string.sound_post_proxy_warning_title,
        descriptionText = getString(R.string.sound_post_proxy_warning_description, host),
        positiveButtonText = getString(R.string.sound_post_proxy_warning_load),
        onPositiveButtonClickListener = { finish(true) },
        negativeButtonText = getString(R.string.sound_post_proxy_warning_do_not_load),
        onNegativeButtonClickListener = { finish(false) },
        onDismissListener = { finish(false) }
      )

      if (dialogHandle == null) {
        // App is not in foreground, don't remember anything
        finish(null)
        return@suspendCancellableCoroutine
      }

      continuation.invokeOnCancellation {
        BackgroundUtils.runOnMainThread { dialogHandle.dismiss() }
      }
    }
  }

  private fun createAudioPlayer(audioFile: File): ExoPlayer {
    // Only create an audio renderer so that video tracks of video files are not even decoded
    val audioOnlyRenderersFactory = RenderersFactory { eventHandler, _, audioRendererEventListener, _, _ ->
      arrayOf<Renderer>(
        MediaCodecAudioRenderer(
          context,
          MediaCodecSelector.DEFAULT,
          eventHandler,
          audioRendererEventListener
        )
      )
    }

    val player = ExoPlayer.Builder(context, audioOnlyRenderersFactory).build()
    player.playWhenReady = false
    player.repeatMode = Player.REPEAT_MODE_OFF
    player.setMediaItem(MediaItem.fromUri(Uri.fromFile(audioFile)))
    player.prepare()

    return player
  }

  private suspend fun awaitPlayerReady(player: ExoPlayer) {
    if (player.playbackState == Player.STATE_READY) {
      return
    }

    suspendCancellableCoroutine<Unit> { continuation ->
      val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          if (playbackState == Player.STATE_READY) {
            player.removeListener(this)

            if (continuation.isActive) {
              continuation.resume(Unit)
            }
          }
        }

        override fun onPlayerError(error: PlaybackException) {
          player.removeListener(this)

          if (continuation.isActive) {
            continuation.resumeWithException(error)
          }
        }
      }

      player.addListener(listener)
      continuation.invokeOnCancellation {
        BackgroundUtils.runOnMainThread { player.removeListener(listener) }
      }
    }
  }

  private fun startSyncLoop() {
    if (!attached || audioPlayer == null) {
      return
    }

    syncJob?.cancel()
    syncJob = scope.launch {
      while (isActive) {
        tick()
        delay(TICK_INTERVAL_MS)
      }
    }
  }

  private fun resetSyncState() {
    restorePending = true
    prevSample = null
    lastCorrectionTimeMs = 0L
    cycleEnded = false
  }

  private fun tick() {
    val player = audioPlayer ?: return
    if (!attached) {
      return
    }

    val isMuted = mediaViewContract.isSoundCurrentlyMuted()
    val volume = if (isMuted) 0f else 1f
    if (player.volume != volume) {
      player.volume = volume
    }

    val audioDurationMs = player.duration
    if (audioDurationMs == C.TIME_UNSET || audioDurationMs <= 0) {
      return
    }

    val currentTarget = target
    val isPlaying = if (currentTarget == null) {
      tickStandalone(player, audioDurationMs)
    } else {
      tickSynchronized(player, currentTarget, audioDurationMs)
    }

    _uiState.value = UiState.Ready(
      positionMs = player.currentPosition.coerceIn(0, audioDurationMs),
      durationMs = audioDurationMs,
      isPlaying = isPlaying,
      isMuted = isMuted
    )
  }

  /**
   * @return whether the audio is playing
   * */
  private fun tickStandalone(player: ExoPlayer, audioDurationMs: Long): Boolean {
    if (restorePending) {
      restorePending = false

      if (state.audioPositionMs > 0) {
        player.seekTo(state.audioPositionMs.coerceIn(0, audioDurationMs))
      }

      if (state.playing ?: true) {
        player.play()
      } else {
        player.pause()
      }
    }

    val repeatMode = if (autoLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    if (player.repeatMode != repeatMode) {
      player.repeatMode = repeatMode
    }

    return player.playWhenReady && player.playbackState != Player.STATE_ENDED
  }

  /**
   * @return whether the target is playing
   * */
  private fun tickSynchronized(
    player: ExoPlayer,
    currentTarget: SoundPostSyncTarget,
    audioDurationMs: Long
  ): Boolean {
    if (player.repeatMode != Player.REPEAT_MODE_OFF) {
      player.repeatMode = Player.REPEAT_MODE_OFF
    }

    if (!currentTarget.isReady()) {
      // Target is loading/buffering/destroyed or doesn't belong to this page anymore
      if (player.playWhenReady) {
        player.pause()
      }

      prevSample = null
      return false
    }

    val nowMs = SystemClock.elapsedRealtime()
    val videoDurationMs = currentTarget.durationMs()
    val videoPositionMs = currentTarget.positionMs().coerceIn(0, videoDurationMs)
    val videoPlaying = currentTarget.isPlaying()
    val videoIsShorter = videoDurationMs + LENGTH_EQUALITY_THRESHOLD_MS < audioDurationMs

    if (videoIsShorter) {
      // The video must loop until the audio ends
      currentTarget.setLooping(!cycleEnded || autoLoop)
    } else {
      currentTarget.setLooping(autoLoop)
    }

    if (restorePending) {
      restorePending = false

      val restoredAudioPositionMs = if (videoIsShorter && state.audioPositionMs > 0) {
        // Restore the same video loop we were at before
        (state.audioPositionMs / videoDurationMs) * videoDurationMs + videoPositionMs
      } else {
        videoPositionMs
      }

      if (restoredAudioPositionMs < audioDurationMs) {
        player.seekTo(restoredAudioPositionMs)
      }

      lastCorrectionTimeMs = nowMs
    }

    val prev = prevSample
    prevSample = TargetSample(positionMs = videoPositionMs, playing = videoPlaying, timeMs = nowMs)

    var videoWrapped = false
    var videoSeeked = false

    if (prev != null) {
      val predictedPositionMs = if (prev.playing) {
        prev.positionMs + (nowMs - prev.timeMs)
      } else {
        prev.positionMs
      }

      val wrapWindowMs = min(WRAP_WINDOW_MS, videoDurationMs / 3)

      videoWrapped = videoPositionMs < prev.positionMs
        && prev.positionMs >= videoDurationMs - wrapWindowMs
        && videoPositionMs <= wrapWindowMs
      videoSeeked = !videoWrapped && abs(videoPositionMs - predictedPositionMs) > SEEK_DETECTION_THRESHOLD_MS
    }

    if (videoIsShorter) {
      tickVideoShorter(
        player = player,
        currentTarget = currentTarget,
        audioDurationMs = audioDurationMs,
        videoDurationMs = videoDurationMs,
        videoPositionMs = videoPositionMs,
        videoPlaying = videoPlaying,
        videoWrapped = videoWrapped,
        videoSeeked = videoSeeked,
        nowMs = nowMs
      )
    } else {
      tickVideoLonger(
        player = player,
        audioDurationMs = audioDurationMs,
        videoPositionMs = videoPositionMs,
        videoPlaying = videoPlaying,
        videoWrapped = videoWrapped,
        videoSeeked = videoSeeked,
        nowMs = nowMs
      )
    }

    return videoPlaying
  }

  /**
   * The video is the master. The audio position == the video position until the audio ends.
   * */
  private fun tickVideoLonger(
    player: ExoPlayer,
    audioDurationMs: Long,
    videoPositionMs: Long,
    videoPlaying: Boolean,
    videoWrapped: Boolean,
    videoSeeked: Boolean,
    nowMs: Long
  ) {
    val videoInsideAudio = videoPositionMs < audioDurationMs - END_THRESHOLD_MS

    if (!videoPlaying || !videoInsideAudio) {
      if (player.playWhenReady) {
        player.pause()
      }

      // Keep the audio aligned so that it resumes from the correct position
      if (videoInsideAudio && (videoSeeked || videoWrapped)) {
        player.seekTo(videoPositionMs)
      }

      return
    }

    val audioPositionMs = player.currentPosition
    val drifted = abs(audioPositionMs - videoPositionMs) > DRIFT_THRESHOLD_MS
      && nowMs - lastCorrectionTimeMs > CORRECTION_INTERVAL_MS

    if (videoWrapped || videoSeeked || drifted || player.playbackState == Player.STATE_ENDED) {
      player.seekTo(videoPositionMs)
      lastCorrectionTimeMs = nowMs
    }

    if (!player.playWhenReady) {
      player.play()
    }
  }

  /**
   * The audio is the master. The video loops until the audio ends and is kept at
   * (audio position % video duration).
   * */
  private fun tickVideoShorter(
    player: ExoPlayer,
    currentTarget: SoundPostSyncTarget,
    audioDurationMs: Long,
    videoDurationMs: Long,
    videoPositionMs: Long,
    videoPlaying: Boolean,
    videoWrapped: Boolean,
    videoSeeked: Boolean,
    nowMs: Long
  ) {
    if (cycleEnded) {
      if (videoPlaying) {
        // The user resumed the video after the audio has ended with the loop setting disabled
        restartCycle(player, currentTarget, nowMs)
        player.play()
      } else if (player.playWhenReady) {
        player.pause()
      }

      return
    }

    val audioPositionMs = player.currentPosition
    val audioEnded = player.playbackState == Player.STATE_ENDED
      || audioPositionMs >= audioDurationMs - END_THRESHOLD_MS

    if (audioEnded) {
      if (autoLoop) {
        restartCycle(player, currentTarget, nowMs)

        if (videoPlaying) {
          player.play()
        }
      } else {
        cycleEnded = true
        currentTarget.pause()
        player.pause()
      }

      return
    }

    if (videoSeeked) {
      // The user seeked the video, move the audio within the current video loop
      val newAudioPositionMs = (audioPositionMs / videoDurationMs) * videoDurationMs + videoPositionMs
      if (newAudioPositionMs < audioDurationMs) {
        player.seekTo(newAudioPositionMs)
      }

      lastCorrectionTimeMs = nowMs
    } else if (videoPlaying && !videoWrapped && nowMs - lastCorrectionTimeMs > CORRECTION_INTERVAL_MS) {
      val expectedVideoPositionMs = audioPositionMs % videoDurationMs

      // Circular distance because the video loops
      var diffMs = videoPositionMs - expectedVideoPositionMs
      if (diffMs > videoDurationMs / 2) {
        diffMs -= videoDurationMs
      } else if (diffMs < -videoDurationMs / 2) {
        diffMs += videoDurationMs
      }

      if (abs(diffMs) > DRIFT_THRESHOLD_MS) {
        currentTarget.seekTo(expectedVideoPositionMs)
        prevSample = null
        lastCorrectionTimeMs = nowMs
      }
    }

    if (videoPlaying) {
      if (!player.playWhenReady) {
        player.play()
      }
    } else if (player.playWhenReady) {
      player.pause()
    }
  }

  private fun restartCycle(player: ExoPlayer, currentTarget: SoundPostSyncTarget, nowMs: Long) {
    cycleEnded = false
    prevSample = null
    lastCorrectionTimeMs = nowMs

    currentTarget.seekTo(0)
    player.seekTo(0)
  }

  private data class TargetSample(
    val positionMs: Long,
    val playing: Boolean,
    val timeMs: Long
  )

  class State(
    var audioPositionMs: Long = -1L,
    // Only used when there is no sync target
    var playing: Boolean? = null
  ) {

    fun resetPosition() {
      audioPositionMs = -1L
    }

    fun updateFrom(other: State?) {
      audioPositionMs = other?.audioPositionMs ?: -1L
      playing = other?.playing
    }
  }

  sealed interface UiState {
    data object Hidden : UiState
    data object NotLoaded : UiState
    data class Loading(val progress: Float?) : UiState
    data object Error : UiState
    data class Ready(
      val positionMs: Long,
      val durationMs: Long,
      val isPlaying: Boolean,
      val isMuted: Boolean
    ) : UiState
  }

  companion object {
    private const val TAG = "SoundPostPlayer"

    private const val TICK_INTERVAL_MS = 100L
    private const val MAX_PREPARE_WAIT_TIME_MS = 30_000L

    // Video and audio with the length difference less than this are considered to be of the same length
    private const val LENGTH_EQUALITY_THRESHOLD_MS = 250L

    // Positions this close to the end are considered to be the end
    private const val END_THRESHOLD_MS = 50L
    private const val WRAP_WINDOW_MS = 1500L
    private const val SEEK_DETECTION_THRESHOLD_MS = 1000L
    private const val DRIFT_THRESHOLD_MS = 200L
    private const val CORRECTION_INTERVAL_MS = 1000L

    private val proxyWarningMutex = Mutex()

    // Per app lifetime, intentionally not stored in the settings
    @Volatile
    private var proxyWarningDecision: Boolean? = null
  }
}
