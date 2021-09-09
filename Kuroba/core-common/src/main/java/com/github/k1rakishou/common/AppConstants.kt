package com.github.k1rakishou.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File

open class AppConstants(
  context: Context,
  private val flavorType: AndroidUtils.FlavorType,
  private val isLowRamDevice: Boolean,
  val kurobaExUserAgent: String,
  maxPostsInDatabaseSettingValue: Int,
  maxThreadsInDatabaseSettingValue: Int
) {
  val maxPostsCountInPostsCache: Int
  val maxAmountOfPostsInDatabase: Int = maxPostsInDatabaseSettingValue
  val maxAmountOfThreadsInDatabase: Int = maxThreadsInDatabaseSettingValue
  val userAgent: String
  val processorsCount: Int
  val proxiesFileName = PROXIES_FILE_NAME
  val bookmarkWatchWorkUniqueTag = "BookmarkWatcherController_${flavorType.name}"
  val filterWatchWorkUniqueTag = "FilterWatcherController_${flavorType.name}"
  val threadDownloadWorkUniqueTag = "ThreadDownloadController_${flavorType.name}"

  // 128MB
  val exoPlayerDiskCacheMaxSize = 128L * 1024 * 1024
  val mpvDemuxerCacheMaxSize: Int

  val attachFilesDir: File
    get() {
      if (field.exists()) {
        return field
      }

      check(field.mkdir()) { "Failed to create attach files directory! attachFilesDir=${field.absolutePath}" }
      return field
    }

  val attachFilesMetaDir: File
    get() {
      if (field.exists()) {
        return field
      }

      check(field.mkdir()) { "Failed to create attach files meta directory! attachFilesMetaDir=${field.absolutePath}" }
      return field
    }

  val mediaPreviewsDir: File
    get() {
      if (field.exists()) {
        return field
      }

      check(field.mkdir()) { "Failed to create attach files meta directory! attachFilesMetaDir=${field.absolutePath}" }
      return field
    }

  val exoPlayerCacheDir: File
    get() {
      if (field.exists()) {
        return field
      }

      check(field.mkdir()) { "Failed to create ExoPlayer cache directory! exoPlayerCacheDir=${field.absolutePath}" }
      return field
    }

  val threadDownloaderCacheDir: File
    get() {
      if (field.exists()) {
        return field
      }

      check(field.mkdir()) { "Failed to create ThreadDownloader cache directory! threadDownloaderCacheDir=${field.absolutePath}" }
      return field
    }

  val crashLogsDir: File
    get() {
      if (field.exists()) {
        return field
      }

      check(field.mkdir()) { "Failed to create Crash Log directory! crashLogsDir=${field.absolutePath}" }
      return field
    }

  val anrsDir: File
    get() {
      if (field.exists()) {
        return field
      }

      check(field.mkdir()) { "Failed to create ANR directory! anrsDir=${field.absolutePath}" }
      return field
    }

  val fileCacheDir: File
    get() {
      if (field.exists()) {
        return field
      }

      check(field.mkdir()) { "Failed to create File Cache directory! fileCacheDir=${field.absolutePath}" }
      return field
    }

  val fileCacheChunksDir: File
    get() {
      if (field.exists()) {
        return field
      }

      check(field.mkdir()) { "Failed to create File Cache Chunks directory! fileCacheChunksDir=${field.absolutePath}" }
      return field
    }

  init {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    mpvDemuxerCacheMaxSize = calculateMpvDemuxerCacheSize(activityManager).toInt()
    maxPostsCountInPostsCache = calculatePostsCountForPostsCacheDependingOnDeviceRam(activityManager).toInt()
    userAgent = String.format(USER_AGENT_FORMAT, Build.VERSION.RELEASE, Build.MODEL)
    processorsCount = Runtime.getRuntime().availableProcessors()
      .coerceAtLeast(2)

    attachFilesDir = File(context.filesDir, ATTACH_FILES_DIR_NAME)
    attachFilesMetaDir = File(context.filesDir, ATTACH_FILES_META_DIR_NAME)
    mediaPreviewsDir = File(context.filesDir, MEDIA_PREVIEWS_DIR_NAME)
    threadDownloaderCacheDir = File(context.filesDir, THREAD_DOWNLOADER_DIR_NAME)

    crashLogsDir = File(context.filesDir, CRASH_LOGS_DIR_NAME)
    anrsDir = File(context.filesDir, ANRS_DIR_NAME)

    fileCacheDir = File(context.filesDir, FILE_CACHE_DIR)
    fileCacheChunksDir = File(context.filesDir, FILE_CHUNKS_CACHE_DIR)

    exoPlayerCacheDir = File(context.cacheDir, EXO_PLAYER_CACHE_DIR_NAME)
  }

  private fun calculateMpvDemuxerCacheSize(activityManager: ActivityManager?): Long {
    if (isLowRamDevice || activityManager == null) {
      return 32 * ONE_MEGABYTE
    }

    return 64 * ONE_MEGABYTE
  }

  private fun calculatePostsCountForPostsCacheDependingOnDeviceRam(activityManager: ActivityManager?): Long {
    if (isLowRamDevice || activityManager == null) {
      return MINIMUM_POSTS_CACHE_POSTS_COUNT
    }

    val memoryChunk = ((activityManager.memoryClass * ONE_MEGABYTE) / 100) * RAM_PERCENT_FOR_POSTS_CACHE
    val cachePostsCount = memoryChunk / AVERAGE_POST_MEMORY_SIZE

    return cachePostsCount.coerceIn(MINIMUM_POSTS_CACHE_POSTS_COUNT, MAX_POSTS_CACHE_COUNT)
  }

  companion object {
    const val loggingInterceptorEnabled = false

    // 10 percents of the app's available memory (not device's)
    private const val RAM_PERCENT_FOR_POSTS_CACHE = 10
    private const val ONE_MEGABYTE = 1L * 1024 * 1024
    private const val AVERAGE_POST_MEMORY_SIZE = 2048

    private const val MINIMUM_POSTS_CACHE_POSTS_COUNT = 5000L
    private const val MAX_POSTS_CACHE_COUNT = 16000L

    private const val USER_AGENT_FORMAT =
      "Mozilla/5.0 (Linux; Android %s; %s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.127 Mobile Safari/537.36"

    private const val PROXIES_FILE_NAME = "kuroba_proxies.json"
    private const val ATTACH_FILES_DIR_NAME = "attach_files"
    private const val ATTACH_FILES_META_DIR_NAME = "attach_files_meta"
    private const val MEDIA_PREVIEWS_DIR_NAME = "media_previews"
    private const val THREAD_DOWNLOADER_DIR_NAME = "thread_downloader_storage"
    private const val EXO_PLAYER_CACHE_DIR_NAME = "exo_player_cache"
    private const val CRASH_LOGS_DIR_NAME = "crashlogs"
    private const val ANRS_DIR_NAME = "anrs"
    private const val FILE_CACHE_DIR = "filecache"
    private const val FILE_CHUNKS_CACHE_DIR = "file_chunks_cache"

    const val RESOURCES_ENDPOINT = "https://raw.githubusercontent.com/K1rakishou/Kuroba-Experimental/develop/docs/"
    const val DEFAULT_THUMBNAIL = (RESOURCES_ENDPOINT + "internal_spoiler.png")

    // This is a hack to be able to store into the database and load back an url to the composition
    // icon which we store in the resources
    val COMPOSITE_ICON_URL_LAZY by lazy { "https://composite-icon.resource".toHttpUrl() }
  }
}