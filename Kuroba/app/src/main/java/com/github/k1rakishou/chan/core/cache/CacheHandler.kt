package com.github.k1rakishou.chan.core.cache

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.cache.downloader.Chunk
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.Generators
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.mbytesToBytes
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.measureTime

/**
 * CacheHandler has been re-worked a little bit because old implementation was relying on the
 * lastModified file flag which doesn't work on some Android versions/different phones. It was decided
 * to instead use a meta file for every cache file which will contain the following information:
 * 1. Time of creation of the cache file (in millis).
 * 2. A flag that indicates whether a download has been completed or not.
 *
 * We need creation time to not delete cache file for active downloads or for downloads that has
 * just been completed (otherwise the user may see a black screen instead of an image/webm). The
 * minimum cache file life time is 5 minutes. That means we won't delete any cache files (and their
 * meta files) for at least 5 minutes.
 *
 * CacheHandler now also caches file chunks that are used by [ConcurrentChunkedFileDownloader] as well
 * as all media files retrieved via [ImageLoaderV2]
 */
class CacheHandler(
  private val autoLoadThreadImages: Boolean,
  private val appConstants: AppConstants
) {
  private val temporaryFilesCleared = AtomicBoolean(false)
  private val innerCaches = ConcurrentHashMap<CacheFileType, InnerCache>()

  init {
    val duration = measureTime { init() }
    Logger.d(TAG, "CacheHandler.init() took $duration")
  }

  private fun init() {
    if (AppModuleAndroidUtils.isDevBuild) {
      CacheFileType.checkValid()
    }

    val totalFileCacheDiskSizeBytes = if (autoLoadThreadImages) {
      ChanSettings.prefetchDiskCacheSizeMegabytes.get().mbytesToBytes()
    } else {
      ChanSettings.diskCacheSizeMegabytes.get().mbytesToBytes()
    }

    val diskCacheDir = appConstants.diskCacheDir
    if (!diskCacheDir.exists()) {
      diskCacheDir.mkdirs()
    }

    Logger.d(TAG, "diskCacheDir: ${diskCacheDir.absolutePath}, " +
      "totalFileCacheDiskSize: ${ChanPostUtils.getReadableFileSize(totalFileCacheDiskSizeBytes)}")

    for (cacheFileType in CacheFileType.entries) {
      if (innerCaches.containsKey(cacheFileType)) {
        continue
      }

      val innerCacheDirFile = File(File(diskCacheDir, cacheFileType.id.toString()), "files")
      if (!innerCacheDirFile.exists()) {
        innerCacheDirFile.mkdirs()
      }

      val innerCacheChunksDirFile = File(File(diskCacheDir, cacheFileType.id.toString()), "chunks")
      if (!innerCacheChunksDirFile.exists()) {
        innerCacheChunksDirFile.mkdirs()
      }

      val innerCache = InnerCache(
        cacheDirFile = innerCacheDirFile,
        chunksCacheDirFile = innerCacheChunksDirFile,
        fileCacheDiskSizeBytes = cacheFileType.calculateDiskSize(totalFileCacheDiskSizeBytes),
        cacheFileType = cacheFileType
      )

      innerCaches.put(cacheFileType, innerCache)
    }
  }

  suspend fun createTemptFile(
    fileName: String? = null,
    extension: String? = null
  ): File {
    return withContext(Dispatchers.IO) {
      if (temporaryFilesCleared.compareAndSet(false, true)) {
        appConstants.tempFilesDir.listFiles()
          ?.forEach { file -> file.delete() }
      }

      var attempts = 5

      while (attempts > 0) {
        val actualFileName = fileName
          ?.takeIf { it.isNotNullNorBlank() }
          ?: Generators.generateRandomHexString(symbolsCount = 32)

        val actualExtension = extension
          ?.takeIf { it.isNotNullNorBlank() }
          ?: "tmp"

        val fullName = "${actualFileName}.${actualExtension}"

        val file = File(appConstants.tempFilesDir, fullName)
        if (!file.exists()) {
          check(file.createNewFile()) { "Failed to create file: ${file.absolutePath}" }
          return@withContext file
        }

        --attempts
      }

      error("Failed to create a temporary file within 5 attempts!")
    }
  }

  fun getCacheFileOrNull(cacheFileType: CacheFileType, url: String): File? {
    BackgroundUtils.ensureBackgroundThread()

    val file = getInnerCacheByFileType(cacheFileType).getCacheFileOrNull(url)
    Logger.verbose(TAG) { "getCacheFileOrNull($cacheFileType, $url) -> ${file?.name}" }

    return file
  }

  /**
   * Either returns already downloaded file or creates an empty new one on the disk (also creates
   * cache file meta with default parameters)
   * */
  fun getOrCreateCacheFile(cacheFileType: CacheFileType, url: String): File? {
    BackgroundUtils.ensureBackgroundThread()

    val file = getInnerCacheByFileType(cacheFileType).getOrCreateCacheFile(url)
    Logger.verbose(TAG) { "getOrCreateCacheFile($cacheFileType, $url) -> ${file?.name}" }

    return file
  }

  fun getChunkCacheFileOrNull(cacheFileType: CacheFileType, chunk: Chunk, url: String): File? {
    BackgroundUtils.ensureBackgroundThread()

    val file = getInnerCacheByFileType(cacheFileType).getChunkCacheFileOrNull(chunk.start, chunk.end, url)
    Logger.verbose(TAG) {
      "getChunkCacheFileOrNull($cacheFileType, $chunk, $url) -> ${file?.absolutePath}"
    }

    return file
  }

  fun getOrCreateChunkCacheFile(cacheFileType: CacheFileType, chunk: Chunk, url: String): File? {
    BackgroundUtils.ensureBackgroundThread()

    val file = getInnerCacheByFileType(cacheFileType).getOrCreateChunkCacheFile(chunk.start, chunk.end, url)
    Logger.verbose(TAG) { "getOrCreateChunkCacheFile(${cacheFileType}, ${chunk}, ${url})" }

    return file
  }

  fun cacheFileExists(cacheFileType: CacheFileType, fileUrl: String): Boolean {
    val innerCache = getInnerCacheByFileType(cacheFileType)
    val fileName = innerCache.formatCacheFileName(innerCache.hashUrl(fileUrl))
    val exists = innerCache.containsFile(fileName)
    Logger.verbose(TAG) { "cacheFileExists($cacheFileType, $fileUrl) -> $exists" }

    return exists
  }

  suspend fun deleteCacheFileByUrlSuspend(cacheFileType: CacheFileType, url: String): Boolean {
    val innerCache = getInnerCacheByFileType(cacheFileType)

    return withContext(Dispatchers.IO) {
      val deleted = innerCache.deleteCacheFile(innerCache.hashUrl(url))
      Logger.verbose(TAG) { "deleteCacheFileByUrlSuspend($cacheFileType, $url) -> ${deleted}"}

      return@withContext deleted
    }
  }

  fun deleteCacheFileByUrl(cacheFileType: CacheFileType, url: String): Boolean {
    val innerCache = getInnerCacheByFileType(cacheFileType)

    val deleted = getInnerCacheByFileType(cacheFileType).deleteCacheFile(innerCache.hashUrl(url))
    Logger.verbose(TAG) { "deleteCacheFileByUrl($cacheFileType, $url) -> ${deleted}" }

    return deleted
  }

  /**
   * Deletes a cache file with it's meta. Also decreases the total cache size variable by the size
   * of the file.
   * */
  fun deleteCacheFile(cacheFileType: CacheFileType, cacheFile: File): Boolean {
    val deleted = getInnerCacheByFileType(cacheFileType).deleteCacheFile(cacheFile.name)
    Logger.verbose(TAG) { "deleteCacheFile($cacheFileType, ${cacheFile.absolutePath}) -> ${deleted}" }

    return deleted
  }

  fun isAlreadyDownloaded(cacheFileType: CacheFileType, fileUrl: String): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val innerCache = getInnerCacheByFileType(cacheFileType)
    val alreadyDownloaded = isAlreadyDownloaded(cacheFileType, innerCache.getCacheFileByUrl(fileUrl))
    Logger.verbose(TAG) { "isAlreadyDownloaded($cacheFileType, $fileUrl) -> $alreadyDownloaded" }

    return alreadyDownloaded
  }

  /**
   * Checks whether this file is already downloaded by reading it's meta info. If a file has no
   * meta info or it cannot be read - deletes the file so it can be re-downloaded again with all
   * necessary information
   *
   * [cacheFile] must be the cache file, not cache file meta!
   * */
  fun isAlreadyDownloaded(cacheFileType: CacheFileType, cacheFile: File): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val alreadyDownloaded = getInnerCacheByFileType(cacheFileType).isAlreadyDownloaded(cacheFile)
    Logger.verbose(TAG) { "isAlreadyDownloaded($cacheFileType, ${cacheFile.absolutePath}) -> $alreadyDownloaded" }

    return alreadyDownloaded
  }

  fun markFileDownloaded(cacheFileType: CacheFileType, output: File): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val markedAsDownloaded = getInnerCacheByFileType(cacheFileType).markFileDownloaded(output)
    Logger.verbose(TAG) { "markFileDownloaded($cacheFileType, ${output.absolutePath}) -> $markedAsDownloaded" }

    return markedAsDownloaded
  }

  fun getSize(cacheFileType: CacheFileType): Long {
    val size = getInnerCacheByFileType(cacheFileType).getSize()
    Logger.verbose(TAG) { "getSize($cacheFileType) -> ${size}" }

    return size
  }

  fun getMaxSize(cacheFileType: CacheFileType): Long {
    val maxSize = getInnerCacheByFileType(cacheFileType).getMaxSize()
    Logger.verbose(TAG) { "getMaxSize($cacheFileType) -> ${maxSize}" }

    return maxSize
  }

  /**
   * When a file is downloaded we add it's size to the total cache directory size variable and
   * check whether it exceeds the maximum cache size or not. If it does then the trim() operation
   * is executed in a background thread.
   * */
  fun fileWasAdded(cacheFileType: CacheFileType, fileLen: Long) {
    val totalSize = getInnerCacheByFileType(cacheFileType).fileWasAdded(fileLen)

    Logger.verbose(TAG) {
      val maxSizeFormatted = ChanPostUtils.getReadableFileSize(getInnerCacheByFileType(cacheFileType).getMaxSize())
      val fileLenFormatted = ChanPostUtils.getReadableFileSize(fileLen)
      val totalSizeFormatted = ChanPostUtils.getReadableFileSize(totalSize)

      return@verbose "fileWasAdded(${cacheFileType}, ${fileLenFormatted}) -> (${totalSizeFormatted} / ${maxSizeFormatted})"
    }
  }

  /**
   * For now only used in developer settings. Clears the cache completely.
   * */
  fun clearCache(cacheFileType: CacheFileType) {
    getInnerCacheByFileType(cacheFileType).clearCache()
    Logger.verbose(TAG) { "clearCache($cacheFileType)" }
  }

  private fun getInnerCacheByFileType(cacheFileType: CacheFileType): InnerCache {
    return innerCaches[cacheFileType]!!
  }

  companion object {
    private const val TAG = "CacheHandler"
  }
}