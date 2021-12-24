/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.cache

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.mbytesToBytes
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.time.ExperimentalTime
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
@OptIn(ExperimentalTime::class)
class CacheHandler(
  private val autoLoadThreadImages: Boolean,
  private val appConstants: AppConstants
) {
  private val innerCaches = ConcurrentHashMap<CacheFileType, InnerCache>()
  private val cacheHandlerDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

  init {
    val duration = measureTime { init() }
    Logger.d(TAG, "CacheHandler.init() took $duration")
  }

  private fun init() {
    if (AppModuleAndroidUtils.isDevBuild()) {
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

    Logger.d(TAG, "diskCacheDir=${diskCacheDir.absolutePath}, " +
      "totalFileCacheDiskSize=${ChanPostUtils.getReadableFileSize(totalFileCacheDiskSizeBytes)}")

    for (cacheFileType in CacheFileType.values()) {
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
        cacheFileType = cacheFileType,
        isDevBuild = ENABLE_LOGGING
      )

      innerCaches.put(cacheFileType, innerCache)
    }
  }

  fun getCacheFileOrNull(cacheFileType: CacheFileType, url: String): File? {
    BackgroundUtils.ensureBackgroundThread()
    val innerCache = getInnerCacheByFileType(cacheFileType)
    val file = innerCache.getCacheFileOrNull(url)

    if (ENABLE_LOGGING) {
      Logger.d(TAG, "getCacheFileOrNull($cacheFileType, $url) -> ${file?.name}")
    }

    return file
  }

  /**
   * Either returns already downloaded file or creates an empty new one on the disk (also creates
   * cache file meta with default parameters)
   * */
  fun getOrCreateCacheFile(cacheFileType: CacheFileType, url: String): File? {
    BackgroundUtils.ensureBackgroundThread()
    val innerCache = getInnerCacheByFileType(cacheFileType)
    val file = innerCache.getOrCreateCacheFile(url)

    if (ENABLE_LOGGING) {
      Logger.d(TAG, "getOrCreateCacheFile($cacheFileType, $url) -> ${file?.name}")
    }

    return file
  }

  fun getChunkCacheFileOrNull(cacheFileType: CacheFileType, chunkStart: Long, chunkEnd: Long, url: String): File? {
    if (ENABLE_LOGGING) {
      Logger.d(TAG, "getChunkCacheFileOrNull($cacheFileType, $chunkStart..$chunkEnd, $url)")
    }

    BackgroundUtils.ensureBackgroundThread()
    val innerCache = getInnerCacheByFileType(cacheFileType)

    return innerCache.getChunkCacheFileOrNull(chunkStart, chunkEnd, url)
  }

  fun getOrCreateChunkCacheFile(cacheFileType: CacheFileType, chunkStart: Long, chunkEnd: Long, url: String): File? {
    if (ENABLE_LOGGING) {
      Logger.d(TAG, "getOrCreateChunkCacheFile($cacheFileType, $chunkStart..$chunkEnd, $url)")
    }

    BackgroundUtils.ensureBackgroundThread()
    val innerCache = getInnerCacheByFileType(cacheFileType)

    return innerCache.getOrCreateChunkCacheFile(chunkStart, chunkEnd, url)
  }

  fun cacheFileExists(cacheFileType: CacheFileType, fileUrl: String): Boolean {
    val innerCache = getInnerCacheByFileType(cacheFileType)
    val fileName = innerCache.formatCacheFileName(innerCache.hashUrl(fileUrl))
    val exists = innerCache.containsFile(fileName)

    if (ENABLE_LOGGING) {
      Logger.d(TAG, "cacheFileExists($cacheFileType, $fileUrl) -> $exists")
    }

    return exists
  }

  suspend fun deleteCacheFileByUrlSuspend(cacheFileType: CacheFileType, url: String): Boolean {
    if (ENABLE_LOGGING) {
      Logger.d(TAG, "deleteCacheFileByUrlSuspend($cacheFileType, $url)")
    }

    val innerCache = getInnerCacheByFileType(cacheFileType)

    return withContext(cacheHandlerDispatcher) {
      return@withContext innerCache.deleteCacheFile(innerCache.hashUrl(url))
    }
  }

  fun deleteCacheFileByUrl(cacheFileType: CacheFileType, url: String): Boolean {
    if (ENABLE_LOGGING) {
      Logger.d(TAG, "deleteCacheFileByUrl($cacheFileType, $url)")
    }

    val innerCache = getInnerCacheByFileType(cacheFileType)

    return getInnerCacheByFileType(cacheFileType).deleteCacheFile(innerCache.hashUrl(url))
  }

  fun isAlreadyDownloaded(cacheFileType: CacheFileType, fileUrl: String): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val innerCache = getInnerCacheByFileType(cacheFileType)
    val alreadyDownloaded = isAlreadyDownloaded(cacheFileType, innerCache.getCacheFileByUrl(fileUrl))

    if (ENABLE_LOGGING) {
      Logger.d(TAG, "isAlreadyDownloaded($cacheFileType, $fileUrl) -> $alreadyDownloaded")
    }

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

    if (ENABLE_LOGGING) {
      Logger.d(TAG, "isAlreadyDownloaded($cacheFileType, ${cacheFile.absolutePath}) -> $alreadyDownloaded")
    }

    return alreadyDownloaded
  }

  fun markFileDownloaded(cacheFileType: CacheFileType, output: File): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val markedAsDownloaded = getInnerCacheByFileType(cacheFileType).markFileDownloaded(output)

    if (ENABLE_LOGGING) {
      Logger.d(TAG, "markFileDownloaded($cacheFileType, ${output.absolutePath}) -> $markedAsDownloaded")
    }

    return markedAsDownloaded
  }

  fun getSize(cacheFileType: CacheFileType): Long {
    if (ENABLE_LOGGING) {
      Logger.d(TAG, "getSize($cacheFileType)")
    }

    return getInnerCacheByFileType(cacheFileType).getSize()
  }

  fun getMaxSize(cacheFileType: CacheFileType): Long {
    if (ENABLE_LOGGING) {
      Logger.d(TAG, "getMaxSize($cacheFileType)")
    }

    return getInnerCacheByFileType(cacheFileType).getMaxSize()
  }

  /**
   * When a file is downloaded we add it's size to the total cache directory size variable and
   * check whether it exceeds the maximum cache size or not. If it does then the trim() operation
   * is executed in a background thread.
   * */
  fun fileWasAdded(cacheFileType: CacheFileType, fileLen: Long) {
    val totalSize = getInnerCacheByFileType(cacheFileType).fileWasAdded(fileLen)

    if (ENABLE_LOGGING) {
      val maxSizeFormatted = ChanPostUtils.getReadableFileSize(getInnerCacheByFileType(cacheFileType).getMaxSize())
      val fileLenFormatted = ChanPostUtils.getReadableFileSize(fileLen)
      val totalSizeFormatted = ChanPostUtils.getReadableFileSize(totalSize)

      Logger.d(TAG, "fileWasAdded($cacheFileType, ${fileLenFormatted}) -> (${totalSizeFormatted} / ${maxSizeFormatted})")
    }
  }

  /**
   * For now only used in developer settings. Clears the cache completely.
   * */
  fun clearCache(cacheFileType: CacheFileType) {
    if (ENABLE_LOGGING) {
      Logger.d(TAG, "clearCache($cacheFileType)")
    }

    getInnerCacheByFileType(cacheFileType).clearCache()
  }

  /**
   * Deletes a cache file with it's meta. Also decreases the total cache size variable by the size
   * of the file.
   * */
  fun deleteCacheFile(cacheFileType: CacheFileType, cacheFile: File): Boolean {
    if (ENABLE_LOGGING) {
      Logger.d(TAG, "deleteCacheFile($cacheFileType, ${cacheFile.absolutePath})")
    }

    return getInnerCacheByFileType(cacheFileType).deleteCacheFile(cacheFile.name)
  }

  private fun getInnerCacheByFileType(cacheFileType: CacheFileType): InnerCache {
    return innerCaches[cacheFileType]!!
  }

  companion object {
    private const val TAG = "CacheHandler"
    private const val ENABLE_LOGGING = false
  }
}