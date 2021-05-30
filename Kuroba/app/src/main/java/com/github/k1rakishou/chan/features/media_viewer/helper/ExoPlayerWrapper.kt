package com.github.k1rakishou.chan.features.media_viewer.helper

import android.content.Context
import android.net.Uri
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaViewContract
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


class ExoPlayerWrapper(
  private val context: Context,
  private val cacheDataSourceFactory: DataSource.Factory,
  private val mediaViewContract: MediaViewContract,
  private val onAudioDetected: () -> Unit
) {
  private val exoPlayerLazy = lazy { SimpleExoPlayer.Builder(context).build() }
  private val exoPlayer by exoPlayerLazy

  val actualExoPlayer: SimpleExoPlayer
    get() = exoPlayer

  private var _hasContent = false
  val hasContent: Boolean
    get() = _hasContent

  private var firstFrameRendered: CompletableDeferred<Unit>? = null

  fun isInitialized(): Boolean = exoPlayerLazy.isInitialized()

  suspend fun preload(mediaLocation: MediaLocation, prevPosition: Long, prevWindowIndex: Int) {
    when (mediaLocation) {
      is MediaLocation.Local -> TODO()
      is MediaLocation.Remote -> {
        val mediaUri = Uri.parse(mediaLocation.url.toString())
        val mediaSource = ProgressiveMediaSource.Factory(cacheDataSourceFactory)
          .createMediaSource(MediaItem.fromUri(mediaUri))

        exoPlayer.playWhenReady = false
        exoPlayer.setMediaSource(mediaSource)

        if (prevWindowIndex >= 0 && prevPosition >= 0) {
          exoPlayer.seekTo(prevWindowIndex, prevPosition)
        }

        exoPlayer.prepare()

        firstFrameRendered?.cancel()
        firstFrameRendered = CompletableDeferred()

        exoPlayer.addListener(object : Player.Listener {
          override fun onRenderedFirstFrame() {
            firstFrameRendered?.complete(Unit)
            exoPlayer.removeListener(this)
          }
        })

        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
          override fun onAudioEnabled(eventTime: AnalyticsListener.EventTime, counters: DecoderCounters) {
            onAudioDetected()
            exoPlayer.removeAnalyticsListener(this)
          }
        })

        _hasContent = suspendCancellableCoroutine { continuation ->
          val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
              if (state == Player.STATE_ENDED || state == Player.STATE_READY) {
                exoPlayer.removeListener(this)

                if (continuation.isActive) {
                  val hasContent = state == Player.STATE_READY
                  continuation.resume(hasContent)
                }
              }
            }
          }

          exoPlayer.addListener(listener)
        }
      }
    }
  }

  suspend fun startAndAwaitFirstFrame() {
    if (!exoPlayerLazy.isInitialized()) {
      return
    }

    start()

    val deferred = requireNotNull(firstFrameRendered) { "firstFrameRendered is null!" }
    deferred.await()
  }

  fun start() {
    exoPlayer.repeatMode = if (ChanSettings.videoAutoLoop.get()) {
      Player.REPEAT_MODE_ALL
    } else {
      Player.REPEAT_MODE_OFF
    }

    exoPlayer.volume = if (mediaViewContract.isSoundCurrentlyMuted()) {
      0f
    } else {
      1f
    }

    exoPlayer.play()
  }

  fun muteUnMute(mute: Boolean) {
    if (!exoPlayerLazy.isInitialized()) {
      return
    }

    exoPlayer.volume = if (mute) {
      0f
    } else {
      1f
    }
  }

  fun pause() {
    if (exoPlayerLazy.isInitialized()) {
      exoPlayer.pause()
    }
  }

  fun resetPosition() {
    if (exoPlayerLazy.isInitialized()) {
      exoPlayer.seekTo(0, 0L)
    }
  }

  fun release() {
    _hasContent = false

    if (exoPlayerLazy.isInitialized()) {
      exoPlayer.release()
    }

    firstFrameRendered = null
  }

  fun setNoContent() {
    _hasContent = false
  }

}