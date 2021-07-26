package com.github.k1rakishou.chan.ui.cell

import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_logger.Logger
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

object PostCellStatistics {
  private const val TAG = "PostCellStatistics"
  private const val LOG_ENABLED = false

  private val ONE_MINUTE = TimeUnit.MINUTES.toMillis(1)

  private var lastMeasureResetTime = 0L
  private var totalMeasureTime = 0.0
  private var maxMeasureTime = 0.0
  private var minMeasureTime = 16.0
  private var totalMeasuredPostsCount = 0
  private var longMeasuredPostsCount = 0

  private var lastBindResetTime = 0L
  private var totalBindTime = 0.0
  private var maxBindTime = 0.0
  private var minBindTime = 16.0
  private var totalBoundPostsCount = 0
  private var longBoundPostsCount = 0

  @OptIn(ExperimentalTime::class)
  fun onPostBound(postCellInterface: PostCellInterface?, time: Duration) {
    if (!AppModuleAndroidUtils.isDevBuild() || !LOG_ENABLED) {
      return
    }

    val post = postCellInterface?.getPost()
      ?: return

    if (System.currentTimeMillis() - lastBindResetTime > ONE_MINUTE) {
      Logger.d(TAG, "onPostBound() STATS RESET")

      totalBindTime = 0.0
      maxBindTime = 0.0
      minBindTime = 16.0
      totalBoundPostsCount = 0
      longBoundPostsCount = 0
      lastBindResetTime = System.currentTimeMillis()
    }

    ++totalBoundPostsCount
    val timeMs = time.toDouble(DurationUnit.MILLISECONDS)

    if (timeMs > 10.0) {
      ++longBoundPostsCount
    }

    totalBindTime += timeMs
    maxBindTime = Math.max(maxBindTime, timeMs)
    minBindTime = Math.min(minBindTime, timeMs)

    val medianBindTime = (totalBindTime / totalBoundPostsCount.toDouble())
    val medianBindTimeFormatted = String.format("%.2f", medianBindTime)
    val postNo = post.postNo()
    val imagesCount = post.postImages.size

    val timeFormatted = if (timeMs < 1.0) {
      "< 1ms"
    } else {
      "${timeMs}ms"
    }

    Logger.d(TAG, "onPostBound() postNo: ${postNo}, " +
      "imgs: $imagesCount, took ${timeFormatted} (medianBindTime: ${medianBindTimeFormatted}ms, " +
      "maxBindTime=${maxBindTime}ms, minBindTime=${minBindTime}ms, longBoundPostsCount=${longBoundPostsCount})")
  }

  @OptIn(ExperimentalTime::class)
  fun onPostMeasured(postCellInterface: PostCellInterface?, time: Duration) {
    if (!AppModuleAndroidUtils.isDevBuild() || !LOG_ENABLED) {
      return
    }

    val post = postCellInterface?.getPost()
      ?: return

    if (System.currentTimeMillis() - lastMeasureResetTime > ONE_MINUTE) {
      Logger.d(TAG, "onPostMeasured() STATS RESET")

      totalMeasureTime = 0.0
      maxMeasureTime = 0.0
      minMeasureTime = 16.0
      totalMeasuredPostsCount = 0
      longMeasuredPostsCount = 0
      lastMeasureResetTime = System.currentTimeMillis()
    }

    ++totalMeasuredPostsCount
    val timeMs = time.toDouble(DurationUnit.MILLISECONDS)

    if (timeMs > 10.0) {
      ++longMeasuredPostsCount
    }

    totalMeasureTime += timeMs
    maxMeasureTime = Math.max(maxMeasureTime, timeMs)
    minMeasureTime = Math.min(minMeasureTime, timeMs)

    val medianMeasureTime = (totalMeasureTime / totalMeasuredPostsCount.toDouble())
    val medianMeasureTimeFormatted = String.format("%.2f", medianMeasureTime)
    val postNo = post.postNo()
    val imagesCount = post.postImages.size

    val timeFormatted = if (timeMs < 1.0) {
      "< 1ms"
    } else {
      "${timeMs}ms"
    }

    Logger.d(TAG, "onPostMeasured() postNo: $postNo, " +
      "imgs: $imagesCount, took ${timeFormatted} (medianMeasureTime: ${medianMeasureTimeFormatted}ms, " +
      "maxMeasureTime=${maxMeasureTime}ms, minMeasureTime=${minMeasureTime}ms, " +
      "longMeasuredPostsCount=${longMeasuredPostsCount})")
  }

}