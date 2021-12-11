package com.github.k1rakishou.chan.core.cache

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.okhttp.RealDownloaderOkHttpClient
import com.github.k1rakishou.chan.core.cache.downloader.ActiveDownloads
import com.github.k1rakishou.chan.core.cache.downloader.CancelableDownload
import com.github.k1rakishou.chan.core.cache.downloader.ChunkDownloader
import com.github.k1rakishou.chan.core.cache.downloader.ChunkMerger
import com.github.k1rakishou.chan.core.cache.downloader.ChunkPersister
import com.github.k1rakishou.chan.core.cache.downloader.ConcurrentChunkedFileDownloader
import com.github.k1rakishou.chan.core.cache.downloader.DownloadRequestExtraInfo
import com.github.k1rakishou.chan.core.cache.downloader.DownloadState
import com.github.k1rakishou.chan.core.cache.downloader.FileCacheException
import com.github.k1rakishou.chan.core.cache.downloader.FileDownloadRequest
import com.github.k1rakishou.chan.core.cache.downloader.FileDownloadResult
import com.github.k1rakishou.chan.core.cache.downloader.PartialContentSupportChecker
import com.github.k1rakishou.chan.core.cache.downloader.log
import com.github.k1rakishou.chan.core.cache.downloader.logError
import com.github.k1rakishou.chan.core.cache.downloader.logErrorsAndExtractErrorMessage
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getNetworkClass
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils.runOnMainThread
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.util.ChanPostUtils
import dagger.Lazy
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import okhttp3.HttpUrl
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class FileCacheV2(
  private val fileManager: FileManager,
  private val cacheHandler: Lazy<CacheHandler>,
  private val siteResolver: SiteResolver,
  private val downloaderOkHttpClient: Lazy<RealDownloaderOkHttpClient>,
  private val connectivityManager: ConnectivityManager,
  private val appConstants: AppConstants
) {
  private val activeDownloads = ActiveDownloads()

  private val normalRequestQueue = PublishProcessor.create<String>().toSerialized()
  private val threadsCount = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(4)
  private val requestCancellationThread = Executors.newSingleThreadExecutor()
  private val verboseLogs = ChanSettings.verboseLogs.get()

  private val normalThreadIndex = AtomicInteger(0)
  private val workerScheduler = Schedulers.from(
    Executors.newFixedThreadPool(threadsCount) { runnable ->
      return@newFixedThreadPool Thread(
        runnable,
        String.format(
          Locale.ENGLISH,
          NORMAL_THREAD_NAME_FORMAT,
          normalThreadIndex.getAndIncrement()
        )
      )
    }
  )

  private val partialContentSupportChecker = PartialContentSupportChecker(
    downloaderOkHttpClient,
    activeDownloads,
    siteResolver,
    MAX_TIMEOUT_MS,
    appConstants
  )

  private val chunkDownloader = ChunkDownloader(
    downloaderOkHttpClient,
    siteResolver,
    activeDownloads,
    verboseLogs,
    appConstants
  )

  private val chunkReader = ChunkPersister(
    cacheHandler,
    activeDownloads,
    verboseLogs
  )

  private val chunkPersister = ChunkMerger(
    fileManager,
    cacheHandler,
    activeDownloads,
    verboseLogs
  )

  private val concurrentChunkedFileDownloader = ConcurrentChunkedFileDownloader(
    siteResolver,
    chunkDownloader,
    chunkReader,
    chunkPersister,
    workerScheduler,
    verboseLogs,
    activeDownloads,
    cacheHandler
  )

  init {
    initNormalRxWorkerQueue()
  }

  /**
   * This is a singleton class so we don't care about the disposable since we will never should
   * dispose of this stream
   * */
  @SuppressLint("CheckResult")
  private fun initNormalRxWorkerQueue() {
    normalRequestQueue
      .onBackpressureBuffer()
      .observeOn(workerScheduler)
      .flatMap { url ->
        return@flatMap Flowable.defer { handleFileDownload(url) }
          .subscribeOn(workerScheduler)
          .onErrorReturn { throwable ->
            ErrorMapper.mapError(url, throwable, activeDownloads)
          }
          .map { result -> Pair(url, result) }
          .doOnNext { (url, result) -> handleResults(url, result) }
      }
      .subscribe({
        // Do nothing
      }, { error ->
        throw RuntimeException("$TAG Uncaught exception!!! " +
          "workerQueue is in error state now!!! " +
          "This should not happen!!!, original error = " + error.message)
      }, {
        throw RuntimeException(
          "$TAG workerQueue stream has completed!!! This should not happen!!!"
        )
      })
  }

  // For now it is only used in the developer settings so it's okay to block the UI
  fun clearCache(cacheFileType: CacheFileType) {
    activeDownloads.clear()
    cacheHandler.get().clearCache(cacheFileType)
  }

  fun isRunning(url: String): Boolean {
    return synchronized(activeDownloads) {
      activeDownloads.getState(url) == DownloadState.Running
    }
  }

  fun enqueueMediaPrefetchRequest(
    cacheFileType: CacheFileType,
    postImage: ChanPostImage
  ): CancelableDownload? {
    val imageUrl = postImage.imageUrl
      ?: return null

    if (postImage.isInlined) {
      throw IllegalAccessException("Cannot prefetch inlined files! url = $imageUrl")
    }

    val url = imageUrl.toString()

    val (alreadyActive, cancelableDownload) = getOrCreateCancelableDownload(
      url = url,
      callback = null,
      isGalleryBatchDownload = true,
      isPrefetchDownload = true,
      // Prefetch downloads always have default extra info (no file size, no file hash)
      extraInfo = DownloadRequestExtraInfo(),
      cacheFileType = cacheFileType
    )

    if (alreadyActive) {
      return null
    }

    normalRequestQueue.onNext(url)
    return cancelableDownload
  }

  @SuppressLint("CheckResult")
  fun enqueueDownloadFileRequest(
    url: HttpUrl,
    cacheFileType: CacheFileType,
    extraInfo: DownloadRequestExtraInfo,
    callback: FileCacheListener?,
  ): CancelableDownload {
    return enqueueDownloadFileRequest(
      url = url.toString(),
      extraInfo = extraInfo,
      cacheFileType = cacheFileType,
      callback = callback
    )
  }

  fun enqueueDownloadFileRequest(
    url: String,
    cacheFileType: CacheFileType,
    callback: FileCacheListener?,
    extraInfo: DownloadRequestExtraInfo = DownloadRequestExtraInfo()
  ): CancelableDownload {
    val (alreadyActive, cancelableDownload) = getOrCreateCancelableDownload(
      url = url,
      callback = callback,
      isGalleryBatchDownload = false,
      isPrefetchDownload = false,
      extraInfo = extraInfo,
      cacheFileType = cacheFileType
    )

    if (alreadyActive) {
      return cancelableDownload
    }

    log(TAG, "Downloading a file, url=$url")
    normalRequestQueue.onNext(url)

    return cancelableDownload
  }

  private fun getOrCreateCancelableDownload(
    url: String,
    callback: FileCacheListener?,
    isGalleryBatchDownload: Boolean,
    isPrefetchDownload: Boolean,
    extraInfo: DownloadRequestExtraInfo,
    cacheFileType: CacheFileType
  ): Pair<Boolean, CancelableDownload> {
    return synchronized(activeDownloads) {
      val prevRequest = activeDownloads.get(url)
      if (prevRequest != null) {
        log(TAG, "Request $url is already active, re-subscribing to it, " +
          "state=${prevRequest.cancelableDownload.getState()}")

        val prevCancelableDownload = prevRequest.cancelableDownload
        if (callback != null) {
          prevCancelableDownload.addCallback(callback)
        }

        // true means that this request has already been started before and hasn't yet
        // completed so we can just resubscribe to it instead of creating a new one
        return@synchronized true to prevCancelableDownload
      }

      val cancelableDownload = CancelableDownload(
        url = url,
        requestCancellationThread = requestCancellationThread,
        downloadType = CancelableDownload.DownloadType(
          isPrefetchDownload = isPrefetchDownload,
          isGalleryBatchDownload = isGalleryBatchDownload
        )
      )

      if (callback != null) {
        cancelableDownload.addCallback(callback)
      }

      val request = FileDownloadRequest(
        url = url,
        downloaded = AtomicLong(0L),
        total = AtomicLong(0L),
        cancelableDownload = cancelableDownload,
        extraInfo = extraInfo,
        cacheFileType = cacheFileType
      )

      activeDownloads.put(url, request)
      return@synchronized false to cancelableDownload
    }
  }

  private fun handleResults(url: String, result: FileDownloadResult) {
    BackgroundUtils.ensureBackgroundThread()

    try {
      val request = activeDownloads.get(url)
        ?: return

      if (result.isErrorOfAnyKind()) {
        // Only call cancel when not already canceled and not stopped
        if (result !is FileDownloadResult.Canceled && result !is FileDownloadResult.Stopped) {
          activeDownloads.get(url)?.cancelableDownload?.cancel()
        }

        purgeOutput(request.url, request.getOutputFile())
      }

      val networkClass = getNetworkClassOrDefaultText(result)
      val activeDownloadsCount = activeDownloads.count() - 1

      when (result) {
        is FileDownloadResult.Start -> {
          log(TAG, "Download (${request}) has started. " +
            "Chunks count = ${result.chunksCount}. " +
            "Network class = $networkClass. " +
            "Downloads = $activeDownloadsCount")

          // Start is not a terminal event so we don't want to remove request from the
          // activeDownloads
          resultHandler(url, request, false) {
            onStart(result.chunksCount)
          }
        }

        // Success
        is FileDownloadResult.Success -> {
          val (downloaded, total, cacheFileType) = synchronized(activeDownloads) {
            val activeDownload = activeDownloads.get(url)

            val downloaded = activeDownload?.downloaded?.get()
            val total = activeDownload?.total?.get()
            val cacheFileType = activeDownload?.cacheFileType

            Triple(downloaded, total, cacheFileType)
          }

          if (downloaded == null || total == null || cacheFileType == null) {
            return
          }

          val downloadedString = ChanPostUtils.getReadableFileSize(downloaded)
          val totalString = ChanPostUtils.getReadableFileSize(total)

          log(TAG, "Success (" +
            "cacheFileType = cacheFileType, " +
            "downloaded = ${downloadedString} ($downloaded B), " +
            "total = ${totalString} ($total B), " +
            "took ${result.requestTime}ms, " +
            "network class = $networkClass, " +
            "downloads = $activeDownloadsCount" +
            ") for request ${request}"
          )

          // Trigger cache trimmer after a file has been successfully downloaded
          cacheHandler.get().fileWasAdded(
            cacheFileType = cacheFileType,
            fileLen = total
          )

          resultHandler(url, request, true) {
            onSuccess(result.file)
            onEnd()
          }
        }
        // Progress
        is FileDownloadResult.Progress -> {
          val chunkSize = if (result.chunkSize <= 0L) {
            1L
          } else {
            result.chunkSize
          }

          if (SHOW_PROGRESS_LOGS) {
            val percents = (result.downloaded.toFloat() / chunkSize.toFloat()) * 100f
            val downloadedString = ChanPostUtils.getReadableFileSize(result.downloaded)
            val totalString = ChanPostUtils.getReadableFileSize(chunkSize)

            log(TAG,
              "Progress " +
                "chunkIndex = ${result.chunkIndex}, downloaded: (${downloadedString}) " +
                "(${result.downloaded} B) / ${totalString} (${chunkSize} B), " +
                "${percents}%) for request ${request}"
            )
          }

          // Progress is not a terminal event so we don't want to remove request from the
          // activeDownloads
          resultHandler(url, request, false) {
            onProgress(result.chunkIndex, result.downloaded, chunkSize)
          }
        }

        // Cancel
        is FileDownloadResult.Canceled,
          // Stop (called by WebmStreamingSource to stop downloading a file via FileCache and
          // continue downloading it via WebmStreamingDataSource)
        is FileDownloadResult.Stopped -> {
          val (downloaded, total, output) = synchronized(activeDownloads) {
            val activeDownload = activeDownloads.get(url)

            val downloaded = activeDownload?.downloaded?.get()
            val total = activeDownload?.total?.get()
            val output = activeDownload?.getOutputFile()

            Triple(downloaded, total, output)
          }

          val isCanceled = when (result) {
            is FileDownloadResult.Canceled -> true
            is FileDownloadResult.Stopped -> false
            else -> throw RuntimeException("Must be either Canceled or Stopped")
          }

          val causeText = if (isCanceled) {
            "canceled"
          } else {
            "stopped"
          }

          log(TAG, "Request ${request} $causeText, " +
            "downloaded = $downloaded, " +
            "total = $total, " +
            "network class = $networkClass, " +
            "downloads = $activeDownloadsCount")

          resultHandler(url, request, true) {
            if (isCanceled) {
              onCancel()
            } else {
              onStop(output)
            }

            onEnd()
          }
        }
        is FileDownloadResult.KnownException -> {
          val message = "Exception for request ${request}, " +
            "network class = $networkClass, downloads = $activeDownloadsCount"

          if (verboseLogs) {
            logError(TAG, message, result.fileCacheException)
          } else {
            logError(TAG, message)
          }

          resultHandler(url, request, true) {
            when (result.fileCacheException) {
              is FileCacheException.CancellationException -> {
                throw RuntimeException("Not used")
              }
              is FileCacheException.FileNotFoundOnTheServerException -> {
                onNotFound()
              }
              is FileCacheException.CouldNotMarkFileAsDownloaded,
              is FileCacheException.NoResponseBodyException,
              is FileCacheException.CouldNotCreateOutputCacheFile,
              is FileCacheException.OutputFileDoesNotExist,
              is FileCacheException.ChunkFileDoesNotExist,
              is FileCacheException.HttpCodeException,
              is FileCacheException.BadOutputFileException -> {
                if (result.fileCacheException is FileCacheException.HttpCodeException
                  && result.fileCacheException.statusCode == 404) {
                  throw RuntimeException("This shouldn't be handled here!")
                }

                onFail(IOException(result.fileCacheException.message))
              }
            }.exhaustive

            onEnd()
          }
        }
        is FileDownloadResult.UnknownException -> {
          val message = logErrorsAndExtractErrorMessage(
            TAG,
            "Unknown exception",
            result.error
          )

          resultHandler(url, request, true) {
            onFail(IOException(message))
            onEnd()
          }
        }
      }.exhaustive
    } catch (error: Throwable) {
      Logger.e(TAG, "An error in result handler", error)
    }
  }

  private fun getNetworkClassOrDefaultText(result: FileDownloadResult): String {
    return when (result) {
      is FileDownloadResult.Start,
      is FileDownloadResult.Success,
      FileDownloadResult.Canceled,
      FileDownloadResult.Stopped,
      is FileDownloadResult.KnownException -> getNetworkClass(connectivityManager)
      is FileDownloadResult.Progress,
      is FileDownloadResult.UnknownException -> {
        "Unsupported result: ${result::class.java.simpleName}"
      }
    }.exhaustive
  }

  private fun resultHandler(
    url: String,
    request: FileDownloadRequest,
    isTerminalEvent: Boolean,
    func: FileCacheListener.() -> Unit
  ) {
    try {
      request.cancelableDownload.forEachCallback {
        runOnMainThread {
          func()
        }
      }
    } finally {
      if (isTerminalEvent) {
        request.cancelableDownload.clearCallbacks()
        activeDownloads.remove(url)
      }
    }
  }

  private fun handleFileDownload(url: String): Flowable<FileDownloadResult> {
    BackgroundUtils.ensureBackgroundThread()

    val request = activeDownloads.get(url)
    if (request == null || !request.cancelableDownload.isRunning()) {
      val state = request?.cancelableDownload?.getState()
        ?: DownloadState.Canceled

      return Flowable.error(FileCacheException.CancellationException(state, url))
    }

    val cacheFileType = request.cacheFileType

    val outputFile = cacheHandler.get().getOrCreateCacheFile(
      cacheFileType = cacheFileType,
      url = url
    ) ?: return Flowable.error(FileCacheException.CouldNotCreateOutputCacheFile(url))

    if (cacheHandler.get().isAlreadyDownloaded(cacheFileType, outputFile)) {
      return Flowable.just(FileDownloadResult.Success(outputFile, 0L))
    }

    val fullPath = outputFile.absolutePath
    val exists = outputFile.exists()
    val isFile = outputFile.isFile()
    val canWrite = outputFile.canWrite()

    if (!exists || !isFile || !canWrite) {
      return Flowable.error(
        FileCacheException.BadOutputFileException(fullPath, exists, isFile, canWrite, cacheFileType)
      )
    }

    request.setOutputFile(outputFile)

    return partialContentSupportChecker.check(url)
      .observeOn(workerScheduler)
      .toFlowable()
      .flatMap { result ->
        if (result.notFoundOnServer) {
          throw FileCacheException.FileNotFoundOnTheServerException()
        }

        return@flatMap concurrentChunkedFileDownloader.download(
          result,
          url,
          result.supportsPartialContentDownload
        )
      }
  }

  private fun purgeOutput(url: String, output: File?) {
    BackgroundUtils.ensureBackgroundThread()

    val request = activeDownloads.get(url)
      ?: return

    if (request.cancelableDownload.getState() != DownloadState.Canceled) {
      // Not canceled, only purge output when canceled. Do not purge the output file when
      // the state stopped too, because we are gonna use the file for the webm streaming cache.
      return
    }

    if (output == null) {
      return
    }

    log(TAG, "Purging url=${url}, file=${output.absolutePath}")

    if (!cacheHandler.get().deleteCacheFile(request.cacheFileType, output)) {
      logError(TAG, "Could not delete the file in purgeOutput, output = ${output.absolutePath}")
    }
  }

  companion object {
    private const val TAG = "FileCacheV2"
    private const val NORMAL_THREAD_NAME_FORMAT = "NormalFileCacheV2Thread-%d"
    private const val MAX_TIMEOUT_MS = 1000L

    const val MIN_CHUNK_SIZE = 1024L * 8L // 8 KB

    private const val SHOW_PROGRESS_LOGS = false
  }
}