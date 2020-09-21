package com.github.k1rakishou.common

import android.app.ActivityManager
import android.content.Context

class AppConstants(
  context: Context,
  private val isDev: Boolean
) {
  val maxPostsCountInPostsCache: Int
  val maxAmountOfPostsInDatabase: Int
  val maxAmountOfThreadsInDatabase: Int

  init {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    if (!isDev) {
      maxAmountOfPostsInDatabase = 125_000
    } else {
      maxAmountOfPostsInDatabase = 5000
    }

    if (!isDev) {
      maxAmountOfThreadsInDatabase = 12_500
    } else {
      maxAmountOfThreadsInDatabase = 500
    }

    maxPostsCountInPostsCache = calculatePostsCountForPostsCacheDependingOnDeviceRam(activityManager)
  }

  private fun calculatePostsCountForPostsCacheDependingOnDeviceRam(activityManager: ActivityManager?): Int {
    if (isDev) {
      return DEV_BUILD_POSTS_CACHE_COUNT
    }

    return activityManager?.let { am ->
      val memoryChunk = ((am.memoryClass * ONE_MEGABYTE) / 100) * RAM_PERCENT_FOR_POSTS_CACHE
      val cachePostsCount = memoryChunk / AVERAGE_POST_MEMORY_SIZE

      if (cachePostsCount < MINIMUM_POSTS_CACHE_POSTS_COUNT) {
        return@let MINIMUM_POSTS_CACHE_POSTS_COUNT
      }

      return@let cachePostsCount
    } ?: MINIMUM_POSTS_CACHE_POSTS_COUNT
  }

  companion object {
    // 10 percents of the app's available memory (not device's)
    private const val RAM_PERCENT_FOR_POSTS_CACHE = 10
    private const val ONE_MEGABYTE = 1 * 1024 * 1024
    private const val AVERAGE_POST_MEMORY_SIZE = 2048

    private const val MINIMUM_POSTS_CACHE_POSTS_COUNT = 16 * 1024
    private const val DEV_BUILD_POSTS_CACHE_COUNT = 3000

    const val USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36"
  }
}