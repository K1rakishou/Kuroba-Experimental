package com.github.k1rakishou.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.webkit.WebSettings
import com.github.k1rakishou.core_logger.Logger
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File

open class AppConstants(
  context: Context,
  private val flavorType: AndroidUtils.FlavorType,
  private val isLowRamDevice: Boolean,
  val kurobaExCustomUserAgent: String,
  val overrideUserAgent: () -> String,
  maxPostsInDatabaseSettingValue: Int,
  maxThreadsInDatabaseSettingValue: Int
) {
  val maxPostsCountInPostsCache: Int
  val maxAmountOfPostsInDatabase: Int = maxPostsInDatabaseSettingValue
  val maxAmountOfThreadsInDatabase: Int = maxThreadsInDatabaseSettingValue
  val processorsCount: Int
  val proxiesFileName = PROXIES_FILE_NAME
  val thirdEyeSettingsFileName = THIRD_EYE_SETTINGS_FILE_NAME
  val bookmarkWatchWorkUniqueTag = "BookmarkWatcherController_${flavorType.name}"
  val filterWatchWorkUniqueTag = "FilterWatcherController_${flavorType.name}"
  val threadDownloadWorkUniqueTag = "ThreadDownloadController_${flavorType.name}"

  val userAgent by lazy {
    val overriddenUserAgent = overrideUserAgent()
    if (overriddenUserAgent.isNotBlank()) {
      Logger.d(TAG, "userAgent() Using overridden user agent: \'${overriddenUserAgent}\'")
      return@lazy overriddenUserAgent
    }

    try {
      val webViewUserAgent = WebSettings.getDefaultUserAgent(context)
      Logger.d(TAG, "userAgent() Using default WebView user agent: \'${webViewUserAgent}\'")

      return@lazy webViewUserAgent
    } catch (error: Throwable) {
      // Who knows what may happen if the user deletes webview from the system so just in case
      // switch to a default user agent in case of a crash
      Logger.e(TAG, "userAgent() WebSettings.getDefaultUserAgent() error", error)
      return@lazy String.format(USER_AGENT_FORMAT, Build.VERSION.RELEASE, Build.MODEL)
    }
  }

  val isDebuggerAttached: Boolean
    get() = Debug.isDebuggerConnected()

  // 128MB
  val exoPlayerDiskCacheMaxSize = 128L * 1024 * 1024
  val mpvDemuxerCacheMaxSize: Long

  val replyDraftsDir: File
    get() {
      if (field.exists()) {
        return field
      }

      check(field.mkdir()) { "Failed to create reply drafts directory! replyDraftsDir=${field.absolutePath}" }
      return field
    }

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

  val diskCacheDir: File
    get() {
      if (field.exists()) {
        return field
      }

      check(field.mkdir()) { "Failed to create Disk Cache directory! diskCacheDir=${field.absolutePath}" }
      return field
    }

  val mpvNativeLibsDir: File
    get() {
      if (field.exists()) {
        return field
      }

      check(field.mkdirs()) { "Failed to create mpv native libs directory! mpvNativeLibsDir=${field.absolutePath}" }
      return field
    }

  val mpvCertDir: File
    get() {
      if (field.exists()) {
        return field
      }

      check(field.mkdirs()) { "Failed to create mpv certificate directory! mpvCertDir=${field.absolutePath}" }
      return field
    }


  init {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    mpvDemuxerCacheMaxSize = calculateMpvDemuxerCacheSize(activityManager)
    maxPostsCountInPostsCache = calculatePostsCountForPostsCacheDependingOnDeviceRam(activityManager).toInt()

    processorsCount = Runtime.getRuntime().availableProcessors()
      .coerceAtLeast(2)

    replyDraftsDir = File(context.filesDir, REPLY_DRAFTS_DIR_NAME)
    attachFilesDir = File(context.filesDir, ATTACH_FILES_DIR_NAME)
    attachFilesMetaDir = File(context.filesDir, ATTACH_FILES_META_DIR_NAME)
    mediaPreviewsDir = File(context.filesDir, MEDIA_PREVIEWS_DIR_NAME)
    threadDownloaderCacheDir = File(context.filesDir, THREAD_DOWNLOADER_DIR_NAME)

    mpvNativeLibsDir = File(context.filesDir, MPV_NATIVE_LIBS_DIR_NAME)
    mpvCertDir = File(context.filesDir, MPV_CERT_DIR_NAME)

    diskCacheDir = File(context.filesDir, DISK_CACHE_DIR_NAME)

    // TODO(KurobaEx): remove me in v1.5.0
    val oldFileCacheDir = File(context.filesDir, OLD_FILE_CACHE_DIR)
    if (oldFileCacheDir.exists()) {
      Logger.d(TAG, "Deleting oldFileCacheDir: '${oldFileCacheDir.absolutePath}'")
      oldFileCacheDir.deleteRecursively()
    }

    // TODO(KurobaEx): remove me in v1.5.0
    val oldFileCacheChunksDir = File(context.filesDir, OLD_FILE_CHUNKS_CACHE_DIR)
    if (oldFileCacheChunksDir.exists()) {
      Logger.d(TAG, "Deleting oldFileCacheChunksDir: '${oldFileCacheChunksDir.absolutePath}'")
      oldFileCacheChunksDir.deleteRecursively()
    }

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
    private const val TAG = "AppConstants"

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
    private const val THIRD_EYE_SETTINGS_FILE_NAME = "third_eye_settings.json"

    private const val REPLY_DRAFTS_DIR_NAME = "reply_drafts"
    private const val ATTACH_FILES_DIR_NAME = "attach_files"
    private const val ATTACH_FILES_META_DIR_NAME = "attach_files_meta"
    private const val MEDIA_PREVIEWS_DIR_NAME = "media_previews"
    private const val THREAD_DOWNLOADER_DIR_NAME = "thread_downloader_storage"
    private const val MPV_NATIVE_LIBS_DIR_NAME = "mpv_native_libs"
    private const val MPV_CERT_DIR_NAME = "certs/mpv"
    private const val EXO_PLAYER_CACHE_DIR_NAME = "exo_player_cache"

    // TODO(KurobaEx): remove me in v1.5.0
    @Deprecated("Use DISK_CACHE_DIR_NAME") private const val OLD_FILE_CACHE_DIR = "filecache"
    // TODO(KurobaEx): remove me in v1.5.0
    @Deprecated("Moved into InnerCache") private const val OLD_FILE_CHUNKS_CACHE_DIR = "file_chunks_cache"

    const val DISK_CACHE_DIR_NAME = "disk_cache"

    const val MPV_CERTIFICATE_FILE_NAME = "cacert.pem"

    const val RESOURCES_ENDPOINT = "https://raw.githubusercontent.com/K1rakishou/Kuroba-Experimental/develop/docs/"

    const val INLINED_IMAGE_THUMBNAIL = RESOURCES_ENDPOINT + "internal_spoiler.png"
    @JvmField val INLINED_IMAGE_THUMBNAIL_URL = INLINED_IMAGE_THUMBNAIL.toHttpUrl()

    const val HIDDEN_IMAGE_THUMBNAIL = RESOURCES_ENDPOINT + "hide_thumb.png"
    @JvmField val HIDDEN_IMAGE_THUMBNAIL_URL = HIDDEN_IMAGE_THUMBNAIL.toHttpUrl()

    // This is a hack to be able to store into the database and load back an url to the composition
    // icon which we store in the resources
    val COMPOSITE_ICON_URL_LAZY by lazy { "https://composite-icon.resource".toHttpUrl() }
  }
}