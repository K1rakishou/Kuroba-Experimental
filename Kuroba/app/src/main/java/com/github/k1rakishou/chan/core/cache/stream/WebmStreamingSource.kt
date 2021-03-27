package com.github.k1rakishou.chan.core.cache.stream

import android.net.Uri
import androidx.core.net.toUri
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheListener
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.cache.MediaSourceCallback
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils.runOnMainThread
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.FileDataSource
import java.io.File
import java.io.IOException

class WebmStreamingSource(
  private val siteManager: SiteManager,
  private val siteResolver: SiteResolver,
  private val fileManager: FileManager,
  private val fileCacheV2: FileCacheV2,
  private val cacheHandler: CacheHandler,
  private val appConstants: AppConstants
) {

  fun createMediaSource(postImage: ChanPostImage, callback: MediaSourceCallback) {
    BackgroundUtils.ensureBackgroundThread()

    val imageUrl = postImage.imageUrl
    if (imageUrl == null) {
      runOnMainThread { callback.onError(IOException("PostImage has no imageUrl")) }
      return
    }

    val fileLengthInBytes = getFileLengthIfPossible(postImage)
    val videoUrl = imageUrl.toString()
    val uri = videoUrl.toUri()
    val alreadyExists = cacheHandler.cacheFileExists(videoUrl)
    val rawFile = cacheHandler.getOrCreateCacheFile(videoUrl)

    val fileCacheSource = WebmStreamingDataSource(
      uri,
      siteResolver.findSiteForUrl(videoUrl),
      rawFile,
      fileLengthInBytes.toLong(),
      ChanSettings.verboseLogs.get(),
      appConstants
    )

    fileCacheSource.addListener(object : WebmStreamingDataSource.Callback {
      override fun dataSourceAddedFile(file: File) {
        BackgroundUtils.ensureMainThread()
        cacheHandler.fileWasAdded(file.length())
      }

      override fun onLoadFailed(exception: Exception) {
        BackgroundUtils.ensureMainThread()
        callback.onError(exception)
      }
    })

    if (alreadyExists && rawFile != null && cacheHandler.isAlreadyDownloaded(rawFile)) {
      Logger.d(TAG, "Loaded from file cache")
      runOnMainThread { loadFromCacheFile(rawFile, callback) }
      return
    }

    val cancelableDownload = fileCacheV2.enqueueNormalDownloadFileRequest(
      videoUrl,
      object : FileCacheListener() {
        override fun onSuccess(file: File) {
          Logger.d(TAG, "createMediaSource() Loading just downloaded file after stop()")
          BackgroundUtils.ensureMainThread()

          // The webm file is already completely downloaded, just use it from the disk
          callback.onMediaSourceReady(
            ProgressiveMediaSource.Factory { fileCacheSource }
              .createMediaSource(MediaItem.fromUri(uri))
          )
        }

        override fun onStop(file: File?) {
          BackgroundUtils.ensureMainThread()

          startLoadingFromNetwork(file, fileCacheSource, callback, uri)
        }

        override fun onNotFound() {
          BackgroundUtils.ensureMainThread()
          callback.onError(IOException("Not found"))
        }

        override fun onFail(exception: Exception) {
          Logger.d(TAG, "createMediaSource() onFail ${exception}")

          BackgroundUtils.ensureMainThread()
          callback.onError(exception)
        }
      })

    if (cancelableDownload == null) {
      Logger.d(TAG, "createMediaSource() cancelableDownload == null")
      return
    }

    // Trigger the onStop() callback so that we can load everything we have managed to download
    // via FileCache into the WebmStreamingDataSource
    cancelableDownload.stop()
  }

  private fun getFileLengthIfPossible(postImage: ChanPostImage): Number {
    return siteManager.bySiteDescriptor(postImage.ownerPostDescriptor.siteDescriptor())
      ?.getChunkDownloaderSiteProperties()
      ?.siteSendsCorrectFileSizeInBytes
      ?.let { siteSendsCorrectFileSizeInBytes ->
        if (!siteSendsCorrectFileSizeInBytes) {
          return@let C.LENGTH_UNSET
        }

        return@let postImage.size
      } ?: C.LENGTH_UNSET
  }

  private fun startLoadingFromNetwork(
    file: File?,
    fileCacheSource: WebmStreamingDataSource,
    callback: MediaSourceCallback,
    uri: Uri
  ) {
    if (file != null) {
      // The webm file is either partially downloaded or is not downloaded at all.
      // We take whatever there is and load it into the WebmStreamingDataSource so
      // we don't need to redownload the bytes that have already been downloaded
      val exists = file.exists()
      val fileLength = file.length()

      Logger.d(TAG, "createMediaSource() Loading partially downloaded file after stop(), " +
          "fileLength = $fileLength")

      if (exists && fileLength > 0L) {
        try {
          file.inputStream().use { inputStream ->
            fileCacheSource.fillCache(fileLength, inputStream)
          }
        } catch (error: IOException) {
          Logger.e(TAG, "createMediaSource() Failed to fill cache!", error)
        }
      }
    }

    callback.onMediaSourceReady(
      ProgressiveMediaSource.Factory { fileCacheSource }
        .createMediaSource(MediaItem.fromUri(uri))
    )
  }

  private fun loadFromCacheFile(file: File, callback: MediaSourceCallback) {
    Logger.d(TAG, "createMediaSource() Loading already downloaded file from the disk")
    val fileUri = file.absolutePath.toUri()

    callback.onMediaSourceReady(
      ProgressiveMediaSource.Factory { FileDataSource() }
        .createMediaSource(MediaItem.fromUri(fileUri))
    )
  }

  companion object {
    private const val TAG = "WebmStreamingSource"
  }
}