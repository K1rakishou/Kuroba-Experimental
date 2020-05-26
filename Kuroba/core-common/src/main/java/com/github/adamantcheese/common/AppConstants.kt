package com.github.adamantcheese.common

import android.app.ActivityManager
import android.content.Context

class AppConstants(context: Context) {
  val maxPostsCountInPostsCache: Int

  /**
   * After every archive fetch, we store the information whether that fetch was successful or
   * not in the database, to accumulate all that data afterwards, and figure out whether an
   * archive still works or maybe it's down, or maybe it has changed some json fields and now
   * nothing works etc.
   * */
  val archiveFetchHistoryMaxEntries = 5

  val maxAmountOfPostsInDatabase = 125_000

  init {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    maxPostsCountInPostsCache = calculatePostsCountForPostsCacheDependingOnDeviceRam(activityManager)
  }

  private fun calculatePostsCountForPostsCacheDependingOnDeviceRam(activityManager: ActivityManager?): Int {
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
  }
}