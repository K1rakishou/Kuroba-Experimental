package com.github.k1rakishou.chan.features.view.media.soundpost

import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.chan.core.mpv.MPVView
import com.github.k1rakishou.chan.features.view.media.helper.ExoPlayerWrapper
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import pl.droidsonroids.gif.GifDrawable

interface SoundPostSyncTarget {
  /** The media is loaded, belongs to the owner page and its duration is known. */
  fun isReady(): Boolean

  /** The playback is actually progressing (not paused and not buffering). */
  fun isPlaying(): Boolean

  fun positionMs(): Long
  fun durationMs(): Long

  fun play()
  fun pause()
  fun seekTo(positionMs: Long)
  fun setLooping(looping: Boolean)
}

class ExoPlayerSyncTarget(
  private val exoPlayerWrapper: ExoPlayerWrapper
) : SoundPostSyncTarget {

  override fun isReady(): Boolean {
    // hasContent becomes false once the (reusable) player is given back so another page may use it
    if (!exoPlayerWrapper.hasContent) {
      return false
    }

    val duration = exoPlayerWrapper.actualExoPlayer.duration
    return duration != C.TIME_UNSET && duration > 0
  }

  override fun isPlaying(): Boolean {
    return exoPlayerWrapper.actualExoPlayer.isPlaying
  }

  override fun positionMs(): Long {
    return exoPlayerWrapper.actualExoPlayer.currentPosition
  }

  override fun durationMs(): Long {
    return exoPlayerWrapper.actualExoPlayer.duration
  }

  override fun play() {
    val exoPlayer = exoPlayerWrapper.actualExoPlayer
    if (exoPlayer.playbackState == Player.STATE_ENDED) {
      exoPlayer.seekTo(0)
    }

    exoPlayer.play()
  }

  override fun pause() {
    exoPlayerWrapper.actualExoPlayer.pause()
  }

  override fun seekTo(positionMs: Long) {
    exoPlayerWrapper.actualExoPlayer.seekTo(positionMs)
  }

  override fun setLooping(looping: Boolean) {
    val exoPlayer = exoPlayerWrapper.actualExoPlayer
    val repeatMode = if (looping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF

    if (exoPlayer.repeatMode != repeatMode) {
      exoPlayer.repeatMode = repeatMode
    }
  }
}

class MpvSyncTarget(
  private val mpvView: MPVView,
  private val isFileLoaded: () -> Boolean
) : SoundPostSyncTarget {

  override fun isReady(): Boolean {
    // All properties are unavailable until the file is loaded, don't spam mpv with requests
    if (!isFileLoaded()) {
      return false
    }

    // mpv is a global instance. It only belongs to us while our MPVView is initialized and attached.
    if (!MPVLib.librariesAreLoaded() || !MPVLib.isCreated()) {
      return false
    }

    if (!mpvView.initialized || !mpvView.isAttachedToWindow) {
      return false
    }

    return durationMs() > 0
  }

  override fun isPlaying(): Boolean {
    // core-idle is true when paused, buffering or when there is nothing to play
    return MPVLib.mpvGetPropertyBoolean("core-idle") == false
  }

  override fun positionMs(): Long {
    val timePosSeconds = mpvView.timePos ?: return 0L
    return (timePosSeconds * 1000.0).toLong()
  }

  override fun durationMs(): Long {
    val durationSeconds = MPVLib.mpvGetPropertyDouble("duration/full") ?: return 0L
    return (durationSeconds * 1000.0).toLong()
  }

  override fun play() {
    mpvView.paused = false
  }

  override fun pause() {
    mpvView.paused = true
  }

  override fun seekTo(positionMs: Long) {
    val positionSeconds = positionMs.toDouble() / 1000.0
    MPVLib.mpvCommand(arrayOf("seek", "$positionSeconds", "absolute+exact"))
  }

  override fun setLooping(looping: Boolean) {
    val currentlyLooping = MPVLib.mpvGetPropertyString("loop-file").let { value -> value != null && value != "no" }
    if (currentlyLooping == looping) {
      return
    }

    MPVLib.mpvSetPropertyString("loop-file", if (looping) "inf" else "no")
  }
}

class GifSyncTarget(
  private val gifDrawableProvider: () -> GifDrawable?
) : SoundPostSyncTarget {
  private var originalLoopCount: Int? = null

  private val gifDrawable: GifDrawable
    get() = requireNotNull(gifDrawableProvider()) { "GifDrawable is null" }

  override fun isReady(): Boolean {
    val drawable = gifDrawableProvider() ?: return false
    return !drawable.isRecycled && drawable.duration > 0
  }

  override fun isPlaying(): Boolean {
    return gifDrawable.isPlaying
  }

  override fun positionMs(): Long {
    return gifDrawable.currentPosition.toLong()
  }

  override fun durationMs(): Long {
    return gifDrawable.duration.toLong()
  }

  override fun play() {
    gifDrawable.start()
  }

  override fun pause() {
    gifDrawable.pause()
  }

  override fun seekTo(positionMs: Long) {
    gifDrawable.seekTo(positionMs.toInt())
  }

  override fun setLooping(looping: Boolean) {
    val drawable = gifDrawable

    if (originalLoopCount == null) {
      originalLoopCount = drawable.loopCount
    }

    // 0 means infinite loop. When we don't need to force the looping, restore the loop count from the file.
    val newLoopCount = if (looping) 0 else originalLoopCount!!
    if (drawable.loopCount != newLoopCount) {
      drawable.loopCount = newLoopCount
    }
  }
}
