package com.github.k1rakishou.chan.features.view.media.helper

import android.content.Context
import android.net.Uri
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.features.view.media.MediaLocation
import com.github.k1rakishou.chan.features.view.media.ViewableMedia
import com.github.k1rakishou.chan.features.view.media.element.MediaViewContract
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.file.ExternalFile
import com.github.k1rakishou.fsaf.file.RawFile
import com.github.k1rakishou.v2.KurobaSettings
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.measureTime


class ExoPlayerWrapper(
  private val context: Context,
  private val kurobaSettings: KurobaSettings,
  private val threadDownloadManager: ThreadDownloadManager,
  private val cachedHttpDataSourceFactory: DataSource.Factory,
  private val fileDataSourceFactory: DataSource.Factory,
  private val contentDataSourceFactory: DataSource.Factory,
  private val mediaViewContract: MediaViewContract,
  private val onAudioDetected: () -> Unit
) {
  private val scope = KurobaCoroutineScope()
  private val reusableExoPlayer by lazy { getOrCreateExoPlayer()  }
  val actualExoPlayer by lazy { reusableExoPlayer.exoPlayer }

  private var timelineUpdateJob: Job? = null

  private var _hasContent = false
  val hasContent: Boolean
    get() = _hasContent

  private var firstFrameRendered: CompletableDeferred<MediaLocation>? = null

  private val _positionAndDurationFlow = MutableStateFlow(Pair(0L, 0L))
  val positionAndDurationFlow: StateFlow<Pair<Long, Long>>
    get() = _positionAndDurationFlow.asStateFlow()

  suspend fun preload(
    viewableMedia: ViewableMedia,
    mediaLocation: MediaLocation,
    prevPosition: Long,
    prevWindowIndex: Int
  ) {
    coroutineScope {
      val mediaSource = createMediaSource(viewableMedia, mediaLocation)

      actualExoPlayer.stop()
      actualExoPlayer.playWhenReady = false
      actualExoPlayer.setMediaSource(mediaSource)

      if (prevWindowIndex >= 0 && prevPosition >= 0) {
        actualExoPlayer.seekTo(prevWindowIndex, prevPosition)
      }

      actualExoPlayer.prepare()

      val prevRenderedVideo = firstFrameRendered
      val shouldRecreateDeferred = prevRenderedVideo == null || !prevRenderedVideo.isCompleted
        || (prevRenderedVideo.isCompleted && prevRenderedVideo.getCompleted() != mediaLocation)

      if (shouldRecreateDeferred) {
        firstFrameRendered?.cancel()
        firstFrameRendered = CompletableDeferred()
      }

      actualExoPlayer.addListener(object : Player.Listener {
        override fun onRenderedFirstFrame() {
          firstFrameRendered?.complete(mediaLocation)
          actualExoPlayer.removeListener(this)

          coroutineContext[Job.Key]?.invokeOnCompletion {
            actualExoPlayer.removeListener(this)
          }
        }
      })

      actualExoPlayer.addAnalyticsListener(object : AnalyticsListener {
        override fun onAudioEnabled(eventTime: AnalyticsListener.EventTime, counters: DecoderCounters) {
          onAudioDetected()
          actualExoPlayer.removeAnalyticsListener(this)

          coroutineContext[Job.Key]?.invokeOnCompletion {
            actualExoPlayer.removeAnalyticsListener(this)
          }
        }
      })

      _hasContent = withTimeout(MAX_BG_AUDIO_DOWNLOAD_WAIT_TIME_MS) { awaitForContentOrError() }
    }
  }

  private suspend fun createMediaSource(
    viewableMedia: ViewableMedia,
    mediaLocation: MediaLocation
  ): MediaSource {
    if (mediaLocation is MediaLocation.Local) {
      return ProgressiveMediaSource.Factory(fileDataSourceFactory)
        .createMediaSource(MediaItem.fromUri(Uri.parse(mediaLocation.path)))
    }

    mediaLocation as MediaLocation.Remote

    val threadDescriptor = viewableMedia.viewableMediaMeta.ownerPostDescriptor?.threadDescriptor()

    // Check whether we can use video from the thread downloader cache
    if (threadDescriptor != null && threadDownloadManager.canUseThreadDownloaderCache(threadDescriptor)) {
      val file = threadDownloadManager.findDownloadedFile(mediaLocation.url, threadDescriptor)
      if (file != null) {
        // We can, use the cached video
        return when (file) {
          is RawFile -> {
            ProgressiveMediaSource.Factory(fileDataSourceFactory)
              .createMediaSource(MediaItem.fromUri(Uri.parse(file.getFullPath())))
          }
          is ExternalFile -> {
            ProgressiveMediaSource.Factory(contentDataSourceFactory)
              .createMediaSource(MediaItem.fromUri(file.getUri()))
          }
          else -> error("Unknown file type: ${file.javaClass.simpleName}")
        }
      }

      // fallthrough
    }

    // Thread is not downloaded or the file is not cached, use regular remote video source
    return ProgressiveMediaSource.Factory(cachedHttpDataSourceFactory)
      .createMediaSource(MediaItem.fromUri(Uri.parse(mediaLocation.url.toString())))
  }

  suspend fun startAndAwaitFirstFrame(mediaLocation: MediaLocation) {
    start()

    val deferred = requireNotNull(firstFrameRendered) { "firstFrameRendered is null!" }

    if (actualExoPlayer.videoFormat == null) {
      deferred.complete(mediaLocation)
    }

    deferred.await()
  }

  fun start() {
    actualExoPlayer.repeatMode = if (kurobaSettings.application.videoAutoLoop.readBlocking()) {
      Player.REPEAT_MODE_ALL
    } else {
      Player.REPEAT_MODE_OFF
    }

    actualExoPlayer.volume = if (mediaViewContract.isSoundCurrentlyMuted()) {
      0f
    } else {
      1f
    }

    timelineUpdateJob?.cancel()
    timelineUpdateJob = scope.launch {
      while (isActive) {
        _positionAndDurationFlow.value = Pair(
          actualExoPlayer.currentPosition.coerceAtLeast(0),
          actualExoPlayer.duration.coerceAtLeast(0)
        )

        delay(1000L)
      }
    }

    actualExoPlayer.play()
  }

  fun muteUnMute(mute: Boolean) {
    actualExoPlayer.volume = if (mute) {
      0f
    } else {
      1f
    }
  }

  fun pause() {
    actualExoPlayer.pause()
  }

  fun release() {
    _hasContent = false

    val duration = measureTime {
      synchronized(reusableExoPlayer) {
        reusableExoPlayer.giveBack()
      }
    }

    Logger.d(TAG, "release() giving the player back took ${duration}")

    timelineUpdateJob?.cancel()
    timelineUpdateJob = null

    scope.cancelChildren()

    firstFrameRendered = null
  }

  fun setNoContent() {
    _hasContent = false
  }

  fun isPlaying(): Boolean {
    return actualExoPlayer.isPlaying
  }

  fun seekTo(windowIndex: Int, position: Long) {
    actualExoPlayer.seekTo(windowIndex, position)
  }

  fun hasNoVideo(): Boolean {
    return actualExoPlayer.videoFormat == null
  }

  fun resetPosition() {
    actualExoPlayer.seekTo(0, 0)
  }

  private suspend fun awaitForContentOrError(): Boolean {
    return suspendCancellableCoroutine { cancellableContinuation ->
      val listener = object : Player.Listener {

        override fun onPlayerErrorChanged(error: PlaybackException?) {
          Logger.e(TAG, "preload() error", error)
          actualExoPlayer.removeListener(this)

          cancellableContinuation.invokeOnCancellation {
            actualExoPlayer.removeListener(this)
          }

          if (error != null && cancellableContinuation.isActive) {
            cancellableContinuation.resumeWithException(error)
          }
        }

        override fun onPlaybackStateChanged(state: Int) {
          if (state == Player.STATE_ENDED || state == Player.STATE_READY) {
            actualExoPlayer.removeListener(this)

            cancellableContinuation.invokeOnCancellation {
              actualExoPlayer.removeListener(this)
            }

            if (cancellableContinuation.isActive) {
              val hasContent = state == Player.STATE_READY
              cancellableContinuation.resume(hasContent)
            }
          }
        }

      }

      actualExoPlayer.addListener(listener)
    }
  }

  private fun getOrCreateExoPlayer(): ReusableExoPlayer {
    return synchronized(reusableExoPlayerCache) {
      val exoPlayer = reusableExoPlayerCache
        .firstOrNull { reusableExoPlayer -> reusableExoPlayer.notUsed }

      if (exoPlayer != null) {
        Logger.d(TAG, "getOrCreateExoPlayer() acquiring already instantiated player, " +
          "total players count: ${reusableExoPlayerCache.size}")

        exoPlayer.acquire()
        return exoPlayer
      }

      val newExoPlayer = SimpleExoPlayer.Builder(context).build()
      val newReusableExoPlayer = ReusableExoPlayer(isUsed = true, newExoPlayer)
      reusableExoPlayerCache.add(newReusableExoPlayer)

      Logger.d(TAG, "getOrCreateExoPlayer() creating a new player, " +
        "total players count: ${reusableExoPlayerCache.size}")

      return@synchronized newReusableExoPlayer
    }
  }

  class ReusableExoPlayer(
    private var isUsed: Boolean,
    val exoPlayer: SimpleExoPlayer
  ) {
    val notUsed: Boolean
      @Synchronized
      get() = !isUsed

    @Synchronized
    fun acquire() {
      isUsed = true
    }

    @Synchronized
    fun giveBack() {
      exoPlayer.stop()
      isUsed = false
    }

    @Synchronized
    fun releaseCompletely() {
      exoPlayer.release()
      isUsed = false
    }
  }

  companion object {
    private const val TAG = "ExoPlayerWrapper"
    private const val MAX_BG_AUDIO_DOWNLOAD_WAIT_TIME_MS = 30_000L

    const val SEEK_POSITION_DELTA = 100

    private val reusableExoPlayerCache = mutableListOf<ReusableExoPlayer>()

    fun releaseAll() {
      reusableExoPlayerCache.forEachIndexed { index, reusableExoPlayer ->
        val duration = measureTime { reusableExoPlayer.releaseCompletely() }
        Logger.d(TAG, "releaseAll() released ${index + 1} / ${reusableExoPlayerCache.size} player, took ${duration}")
      }

      reusableExoPlayerCache.clear()
    }
  }

}