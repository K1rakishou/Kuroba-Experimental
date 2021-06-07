package com.github.k1rakishou.chan.features.media_viewer.helper

import android.content.Context
import android.net.Uri
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaViewContract
import com.github.k1rakishou.core_logger.Logger
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class ExoPlayerWrapper(
  private val context: Context,
  private val cachedHttpDataSourceFactory: DataSource.Factory,
  private val fileDataSourceFactory: DataSource.Factory,
  private val mediaViewContract: MediaViewContract,
  private val onAudioDetected: () -> Unit
) {
  private val reusableExoPlayer by lazy { getOrCreateExoPlayer()  }
  val actualExoPlayer by lazy { reusableExoPlayer.exoPlayer }

  private var _hasContent = false
  val hasContent: Boolean
    get() = _hasContent

  private var firstFrameRendered: CompletableDeferred<Unit>? = null

  suspend fun preload(mediaLocation: MediaLocation, prevPosition: Long, prevWindowIndex: Int) {
    coroutineScope {
      val mediaSource = when (mediaLocation) {
        is MediaLocation.Local -> {
          ProgressiveMediaSource.Factory(fileDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(mediaLocation.path)))
        }
        is MediaLocation.Remote -> {
          ProgressiveMediaSource.Factory(cachedHttpDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(mediaLocation.url.toString())))
        }
      }

      actualExoPlayer.stop()
      actualExoPlayer.playWhenReady = false
      actualExoPlayer.setMediaSource(mediaSource)

      if (prevWindowIndex >= 0 && prevPosition >= 0) {
        actualExoPlayer.seekTo(prevWindowIndex, prevPosition)
      }

      actualExoPlayer.prepare()

      firstFrameRendered?.cancel()
      firstFrameRendered = CompletableDeferred()

      actualExoPlayer.addListener(object : Player.Listener {
        override fun onRenderedFirstFrame() {
          firstFrameRendered?.complete(Unit)
          actualExoPlayer.removeListener(this)

          coroutineContext[Job.Key]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
              actualExoPlayer.removeListener(this)
            }
          }
        }
      })

      actualExoPlayer.addAnalyticsListener(object : AnalyticsListener {
        override fun onAudioEnabled(eventTime: AnalyticsListener.EventTime, counters: DecoderCounters) {
          onAudioDetected()
          actualExoPlayer.removeAnalyticsListener(this)

          coroutineContext[Job.Key]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
              actualExoPlayer.removeAnalyticsListener(this)
            }
          }
        }
      })

      _hasContent = awaitForContentOrError()
    }
  }

  private suspend fun awaitForContentOrError(): Boolean {
    return suspendCancellableCoroutine { continuation ->
      val listener = object : Player.Listener {
        override fun onPlayerError(error: ExoPlaybackException) {
          Logger.e(TAG, "preload() error", error)
          actualExoPlayer.removeListener(this)

          continuation.invokeOnCancellation {
            actualExoPlayer.removeListener(this)
          }

          if (continuation.isActive) {
            continuation.resumeWithException(error)
          }
        }

        override fun onPlaybackStateChanged(state: Int) {
          if (state == Player.STATE_ENDED || state == Player.STATE_READY) {
            actualExoPlayer.removeListener(this)

            continuation.invokeOnCancellation {
              actualExoPlayer.removeListener(this)
            }

            if (continuation.isActive) {
              val hasContent = state == Player.STATE_READY
              continuation.resume(hasContent)
            }
          }
        }
      }

      actualExoPlayer.addListener(listener)
    }
  }

  suspend fun startAndAwaitFirstFrame() {
    start()

    val deferred = requireNotNull(firstFrameRendered) { "firstFrameRendered is null!" }

    if (actualExoPlayer.videoFormat == null) {
      deferred.complete(Unit)
    }

    deferred.await()
  }

  fun start() {
    actualExoPlayer.repeatMode = if (ChanSettings.videoAutoLoop.get()) {
      Player.REPEAT_MODE_ALL
    } else {
      Player.REPEAT_MODE_OFF
    }

    actualExoPlayer.volume = if (mediaViewContract.isSoundCurrentlyMuted()) {
      0f
    } else {
      1f
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

    synchronized(reusableExoPlayer) {
      reusableExoPlayer.giveBack()
    }

    firstFrameRendered = null
  }

  fun setNoContent() {
    _hasContent = false
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

  fun isPlaying(): Boolean {
    return actualExoPlayer.isPlaying
  }

  fun seekTo(windowIndex: Int, position: Long) {
    actualExoPlayer.seekTo(windowIndex, position)
  }

  fun hasNoVideo(): Boolean {
    return actualExoPlayer.videoFormat == null
  }

  class ReusableExoPlayer(
    private var isUsed: Boolean,
    val exoPlayer: SimpleExoPlayer
  ) {
    val notUsed: Boolean
      @get:Synchronized
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

    private val reusableExoPlayerCache = mutableListOf<ReusableExoPlayer>()

    fun releaseAll() {
      reusableExoPlayerCache.forEachIndexed { index, reusableExoPlayer ->
        Logger.d(TAG, "releaseAll() releasing ${index + 1} / ${reusableExoPlayerCache.size} player")
        reusableExoPlayer.releaseCompletely()
      }

      reusableExoPlayerCache.clear()
    }
  }

}