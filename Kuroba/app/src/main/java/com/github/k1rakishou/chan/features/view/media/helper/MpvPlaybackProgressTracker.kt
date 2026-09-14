package com.github.k1rakishou.chan.features.view.media.helper

import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.chan.core.mpv.MPVView
import com.github.k1rakishou.core_logger.Logger
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MpvPlaybackProgressTracker {
  private var isStreaming = false
  private var fullyCached = false

  private var durationMs = 0L
  private var displayedPositionMs = 0L
  private var bufferedPositionMs = 0L

  private var lastRawPositionMs = -1L
  private var lastRawPositionChangeTimeMs = 0L
  private var lastCacheStateQueryTimeMs = 0L

  private var pendingSeekPositionMs: Long? = null
  private var pendingSeekStartTimeMs = 0L

  fun reset(isStreaming: Boolean) {
    this.isStreaming = isStreaming
    // Local files are always "fully cached"
    this.fullyCached = !isStreaming

    durationMs = 0L
    displayedPositionMs = 0L
    bufferedPositionMs = 0L
    lastRawPositionMs = -1L
    lastRawPositionChangeTimeMs = 0L
    lastCacheStateQueryTimeMs = 0L
    pendingSeekPositionMs = null
    pendingSeekStartTimeMs = 0L
  }

  fun onSeekRequested(positionMs: Long, nowMs: Long) {
    // The seek is asynchronous, keep displaying the target position until mpv reaches it,
    // otherwise the handle jumps back to the old position for a moment.
    pendingSeekPositionMs = positionMs
    pendingSeekStartTimeMs = nowMs
    displayedPositionMs = positionMs
  }

  fun update(mpvView: MPVView, nowMs: Long): Progress {
    mpvView.durationFull
      ?.let { durationSeconds -> (durationSeconds * 1000.0).toLong() }
      ?.takeIf { newDurationMs -> newDurationMs > 0 }
      ?.let { newDurationMs -> durationMs = newDurationMs }

    val rawPositionMs = mpvView.timePos?.let { timePosSeconds -> (timePosSeconds * 1000.0).toLong() }
    val playing = MPVLib.mpvGetPropertyBoolean("core-idle") == false

    if (rawPositionMs == null) {
      // Nothing is loaded (e.g. the file has ended without looping), keep the last position. If we were
      // at the last frame then show the end.
      if (durationMs > 0 && durationMs - displayedPositionMs <= MAX_EXTRAPOLATION_MS) {
        displayedPositionMs = durationMs
      }
    } else {
      displayedPositionMs = calculatePosition(rawPositionMs, playing, nowMs)
    }

    updateBufferedPosition(nowMs)

    return Progress(
      positionMs = displayedPositionMs,
      durationMs = durationMs,
      bufferedPositionMs = max(bufferedPositionMs, displayedPositionMs)
    )
  }

  private fun calculatePosition(rawPositionMs: Long, playing: Boolean, nowMs: Long): Long {
    if (rawPositionMs != lastRawPositionMs) {
      lastRawPositionMs = rawPositionMs
      lastRawPositionChangeTimeMs = nowMs
    }

    val seekPositionMs = pendingSeekPositionMs
    if (seekPositionMs != null) {
      val seekReached = abs(rawPositionMs - seekPositionMs) <= SEEK_REACHED_THRESHOLD_MS
      val seekTimedOut = nowMs - pendingSeekStartTimeMs > SEEK_MAX_WAIT_TIME_MS

      if (!seekReached && !seekTimedOut) {
        return seekPositionMs
      }

      pendingSeekPositionMs = null
      lastRawPositionChangeTimeMs = nowMs
    }

    var positionMs = rawPositionMs
    if (playing) {
      // Extrapolate between video frames. Limited so that it doesn't run away when the playback is stuck.
      positionMs += min(nowMs - lastRawPositionChangeTimeMs, MAX_EXTRAPOLATION_MS)
    }

    if (durationMs > 0) {
      positionMs = positionMs.coerceAtMost(durationMs)
    }

    // When the next frame arrives its timestamp may be slightly behind the extrapolated position. Don't move
    // the handle backwards in that case, only for real jumps (seeks, loops).
    val movedBackBy = displayedPositionMs - positionMs
    if (movedBackBy in 1..MAX_EXTRAPOLATION_MS) {
      return displayedPositionMs
    }

    return positionMs
  }

  private fun updateBufferedPosition(nowMs: Long) {
    if (fullyCached) {
      bufferedPositionMs = durationMs
      return
    }

    if (nowMs - lastCacheStateQueryTimeMs < CACHE_STATE_QUERY_INTERVAL_MS) {
      return
    }

    lastCacheStateQueryTimeMs = nowMs

    val cacheState = readCacheState()
    if (cacheState != null) {
      if (cacheState.eofCached && durationMs > 0) {
        // Once the whole file is in the demuxer cache it stays "full" until a new file is loaded
        fullyCached = true
        bufferedPositionMs = durationMs
        return
      }

      if (cacheState.bufferedEndMs != null) {
        bufferedPositionMs = cacheState.bufferedEndMs
        return
      }
    }

    // Fallback for mpv versions without (or with a different format of) demuxer-cache-state
    val cacheDurationMs = MPVLib.mpvGetPropertyDouble("demuxer-cache-duration")
      ?.let { cacheDurationSeconds -> (cacheDurationSeconds * 1000.0).toLong() }
      ?: 0L

    bufferedPositionMs = displayedPositionMs + cacheDurationMs
  }

  private fun readCacheState(): CacheState? {
    // Node properties are returned as JSON when read as a string
    val json = MPVLib.mpvGetPropertyString("demuxer-cache-state")
    if (json.isNullOrBlank()) {
      return null
    }

    return try {
      val jsonObject = JSONObject(json)
      val eofCached = jsonObject.optBoolean("eof-cached", false)
      val positionSeconds = displayedPositionMs.toDouble() / 1000.0

      // Prefer the end of the seekable range that contains the current position
      var bufferedEndSeconds: Double? = null
      val seekableRanges = jsonObject.optJSONArray("seekable-ranges")
      if (seekableRanges != null) {
        for (index in 0 until seekableRanges.length()) {
          val range = seekableRanges.optJSONObject(index) ?: continue
          val start = range.optDouble("start", Double.NaN)
          val end = range.optDouble("end", Double.NaN)

          if (start.isNaN() || end.isNaN()) {
            continue
          }

          if (positionSeconds >= start - RANGE_TOLERANCE_SECONDS && positionSeconds <= end + RANGE_TOLERANCE_SECONDS) {
            bufferedEndSeconds = end
            break
          }
        }
      }

      if (bufferedEndSeconds == null) {
        bufferedEndSeconds = jsonObject.optDouble("cache-end", Double.NaN).takeIf { !it.isNaN() }
      }

      CacheState(
        eofCached = eofCached,
        bufferedEndMs = bufferedEndSeconds?.let { endSeconds -> (endSeconds * 1000.0).toLong() }
      )
    } catch (error: Throwable) {
      Logger.e(TAG, "readCacheState() failed to parse '${json}'", error)
      null
    }
  }

  data class Progress(
    val positionMs: Long,
    val durationMs: Long,
    val bufferedPositionMs: Long
  )

  private class CacheState(
    val eofCached: Boolean,
    val bufferedEndMs: Long?
  )

  companion object {
    private const val TAG = "MpvPlaybackProgressTracker"

    // A bit more than the frame duration of low fps (10 fps) videos
    private const val MAX_EXTRAPOLATION_MS = 150L
    private const val SEEK_REACHED_THRESHOLD_MS = 250L
    private const val SEEK_MAX_WAIT_TIME_MS = 1500L
    private const val CACHE_STATE_QUERY_INTERVAL_MS = 250L
    private const val RANGE_TOLERANCE_SECONDS = 0.5
  }
}
