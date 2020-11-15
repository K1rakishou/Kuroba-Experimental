package com.github.k1rakishou.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build

open class AppConstants(
  context: Context,
  private val flavorType: AndroidUtils.FlavorType
) {
  val maxPostsCountInPostsCache: Int
  val maxAmountOfPostsInDatabase: Int
  val maxAmountOfThreadsInDatabase: Int
  val userAgent: String
  val processorsCount: Int

  val proxiesFileName = PROXIES_FILE_NAME

  val bookmarkWatchWorkUniqueTag = "BookmarkWatcherController_${flavorType.name}"

  init {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    maxAmountOfPostsInDatabase = if (flavorType == AndroidUtils.FlavorType.Dev) {
      5000
    } else {
      125_000
    }

    maxAmountOfThreadsInDatabase = if (flavorType == AndroidUtils.FlavorType.Dev) {
      500
    } else {
      12_500
    }

    maxPostsCountInPostsCache = calculatePostsCountForPostsCacheDependingOnDeviceRam(activityManager)
    userAgent = String.format(USER_AGENT_FORMAT, Build.VERSION.RELEASE, Build.MODEL)
    processorsCount = Runtime.getRuntime().availableProcessors()
      .coerceAtLeast(2)
  }

  private fun calculatePostsCountForPostsCacheDependingOnDeviceRam(activityManager: ActivityManager?): Int {
    if (flavorType == AndroidUtils.FlavorType.Dev) {
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

    private const val USER_AGENT_FORMAT =
      "Mozilla/5.0 (Linux; Android %s; %s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.127 Mobile Safari/537.36"

    private const val PROXIES_FILE_NAME = "kuroba_proxies.json"

    const val RESOURCES_ENDPOINT = "https://raw.githubusercontent.com/K1rakishou/Kuroba-Experimental/release/docs/"
  }
}