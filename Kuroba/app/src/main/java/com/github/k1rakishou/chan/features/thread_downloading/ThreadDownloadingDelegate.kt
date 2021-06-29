package com.github.k1rakishou.chan.features.thread_downloading

import com.github.k1rakishou.chan.core.base.okhttp.DownloaderOkHttpClient
import com.github.k1rakishou.chan.core.helper.ThreadDownloaderFileManagerWrapper
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.extractFileName
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.isOutOfDiskSpaceError
import com.github.k1rakishou.common.processDataCollectionConcurrently
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.DirectorySegment
import com.github.k1rakishou.fsaf.file.FileSegment
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.options.ChanReadOptions
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.repository.ChanPostImageRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.closeQuietly
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class ThreadDownloadingDelegate(
  private val appConstants: AppConstants,
  private val downloaderOkHttpClient: DownloaderOkHttpClient,
  private val siteManager: SiteManager,
  private val siteResolver: SiteResolver,
  private val threadDownloadManager: ThreadDownloadManager,
  private val chanThreadManager: ChanThreadManager,
  private val chanPostRepository: ChanPostRepository,
  private val chanPostImageRepository: ChanPostImageRepository,
  private val threadDownloaderFileManagerWrapper: ThreadDownloaderFileManagerWrapper,
  private val threadDownloadProgressNotifier: ThreadDownloadProgressNotifier
) {
  private val fileManager: FileManager
    get() = threadDownloaderFileManagerWrapper.fileManager
  private val okHttpClient: OkHttpClient
    get() = downloaderOkHttpClient.okHttpClient()
  private val batchCount = appConstants.processorsCount

  private val running = AtomicBoolean(false)

  @OptIn(ExperimentalTime::class)
  suspend fun doWork(): ModularResult<Unit> {
    return ModularResult.Try {
      if (!running.compareAndSet(false, true)) {
        Logger.d(TAG, "doWorkInternal() already running")
        return@Try
      }

      val (result, duration) = measureTimedValue {
        try {
          doWorkInternal()
        } finally {
          running.set(false)
        }
      }

      Logger.d(TAG, "doWorkInternal() took $duration")

      return@Try result
    }
  }

  private suspend fun doWorkInternal() {
    siteManager.awaitUntilInitialized()
    threadDownloadManager.awaitUntilInitialized()
    chanPostRepository.awaitUntilInitialized()

    if (!threadDownloadManager.hasActiveThreads()) {
      Logger.d(TAG, "doWorkInternal() no active threads left, exiting")
      return
    }

    val threadDownloads = threadDownloadManager.getAllActiveThreadDownloads()

    Logger.d(TAG, "doWorkInternal() start, batchCount=$batchCount")
    threadDownloads.forEach { threadDownload ->
      Logger.d(TAG, "doWorkInternal() threadDownload=$threadDownload")
    }

    val outOfDiskSpaceError = AtomicBoolean(false)
    val outputDirError = AtomicBoolean(false)

    threadDownloads.forEachIndexed { index, threadDownload ->
      try {
        if (outOfDiskSpaceError.get()) {
          return@forEachIndexed
        }

        threadDownloadProgressNotifier.notifyProgressEvent(
          threadDownload.threadDescriptor,
          ThreadDownloadProgressNotifier.Event.Progress(0.1f)
        )

        processThread(
          threadDownload = threadDownload,
          index = index + 1,
          total = threadDownloads.size,
          outOfDiskSpaceError = outOfDiskSpaceError,
          outputDirError = outputDirError
        )

        threadDownloadProgressNotifier.notifyProgressEvent(
          threadDownload.threadDescriptor,
          ThreadDownloadProgressNotifier.Event.Progress(1f)
        )

        threadDownloadProgressNotifier.notifyProgressEvent(
          threadDownload.threadDescriptor,
          ThreadDownloadProgressNotifier.Event.Empty
        )
      } catch (error: CancellationException) {
        Logger.e(TAG, "doWorkInternal() ${threadDownload.threadDescriptor} canceled")
      }
    }

    threadDownloadManager.onThreadsProcessed()
    Logger.d(TAG, "doWorkInternal() success")
  }

  private suspend fun processThread(
    threadDownload: ThreadDownload,
    index: Int,
    total: Int,
    outOfDiskSpaceError: AtomicBoolean,
    outputDirError: AtomicBoolean,
  ) {
    val threadDescriptor = threadDownload.threadDescriptor
    Logger.d(TAG, "processThread($index/$total) loadThreadOrCatalog($threadDescriptor) start")

    val threadLoadResult = chanThreadManager.loadThreadOrCatalog(
      threadDescriptor,
      ChanCacheUpdateOptions.UpdateCache,
      ChanLoadOptions.retainAll(),
      ChanCacheOptions.threadDownloaderOption(),
      ChanReadOptions.default()
    )

    threadDownloadProgressNotifier.notifyProgressEvent(
      threadDownload.threadDescriptor,
      ThreadDownloadProgressNotifier.Event.Progress(0.2f)
    )

    if (threadLoadResult is ThreadLoadResult.Error) {
      Logger.e(TAG, "processThread($index/$total) loadThreadOrCatalog($threadDescriptor) " +
          "error: ${threadLoadResult.exception.message}")

      return
    }

    val ownerThreadDatabaseId = threadDownload.ownerThreadDatabaseId

    val chanPostImages = chanPostImageRepository.selectPostImagesByOwnerThreadDatabaseId(ownerThreadDatabaseId)
        .peekError { error -> Logger.e(TAG, "Failed to select images by threadId: ${ownerThreadDatabaseId}", error) }
        .mapErrorToValue { emptyList<ChanPostImage>() }

    if (chanPostImages.isNotEmpty() && threadDownload.downloadMedia && !outOfDiskSpaceError.get()) {
      processThreadMedia(
        index = index,
        total = total,
        chanPostImages = chanPostImages,
        threadDescriptor = threadDescriptor,
        outOfDiskSpaceError = outOfDiskSpaceError,
        outputDirError = outputDirError
      )
    }

    threadDownloadManager.onDownloadProcessed(
      threadDescriptor = threadDescriptor,
      outOfDiskSpaceError = outOfDiskSpaceError.get(),
      outputDirError = outputDirError.get()
    )

    val chanThread = chanThreadManager.getChanThread(threadDescriptor)
    if (chanThread == null) {
      Logger.e(TAG, "processThread($index/$total) loadThreadOrCatalog($threadDescriptor) end, " +
        "status: error chanThread is null")
      return
    }

    val originalPost = chanThread.getOriginalPost()
    if (originalPost.archived || originalPost.closed || originalPost.deleted) {
      threadDownloadManager.completeDownloading(threadDescriptor)
    }

    val status = "archived: ${originalPost.archived}, " +
      "closed: ${originalPost.closed}, " +
      "deleted: ${originalPost.deleted}, " +
      "outOfDiskSpace: ${outOfDiskSpaceError.get()}, " +
      "outputDirError: ${outputDirError.get()}, "

    Logger.d(TAG, "processThread($index/$total) loadThreadOrCatalog($threadDescriptor) end, status: $status")
  }

  private suspend fun processThreadMedia(
    index: Int,
    total: Int,
    chanPostImages: List<ChanPostImage>,
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    outOfDiskSpaceError: AtomicBoolean,
    outputDirError: AtomicBoolean,
  ) {
    if (chanPostImages.isEmpty()) {
      return
    }

    val rootDir = fileManager.fromRawFile(appConstants.threadDownloaderCacheDir)
    Logger.d(TAG, "processThreadMedia($index/$total) threadDescriptor=${threadDescriptor}, " +
      "chanPostImages=${chanPostImages.size}")

    val directoryName = formatDirectoryName(threadDescriptor)

    var outputDirectory = fileManager.findFile(rootDir, directoryName)
    if (outputDirectory == null) {
      outputDirectory = fileManager.create(rootDir, listOf(DirectorySegment(directoryName)))
    }

    if (outputDirectory == null) {
      Logger.d(TAG, "processThreadMedia($index/$total) " +
        "chanThread=${threadDescriptor} failure! outputDirectory is null")
      outputDirError.set(true)
      return
    }

    val noMediaFile = outputDirectory.clone(FileSegment(NO_MEDIA_FILE_NAME))
    if (!fileManager.exists(noMediaFile)) {
      // Disable media scanner
      fileManager.create(noMediaFile)
    }

    val progressIncrement = 0.8f / (chanPostImages.size.toFloat() * 2) // thumbnails and full images
    val mutex = Mutex()
    var totalProgress = 0.2f

    processDataCollectionConcurrently(
      dataList = chanPostImages,
      batchCount = batchCount,
      dispatcher = Dispatchers.IO
    ) { postImage ->
      if (outOfDiskSpaceError.get()) {
        return@processDataCollectionConcurrently
      }

      if (outputDirError.get()) {
        return@processDataCollectionConcurrently
      }

      val thumbnailUrl = postImage.actualThumbnailUrl
      val thumbnailName = postImage.actualThumbnailUrl?.extractFileName()

      if (thumbnailUrl != null && thumbnailName.isNotNullNorEmpty()) {
        downloadImage(
          outputDirectory = outputDirectory,
          isThumbnail = true,
          name = thumbnailName,
          imageUrl = thumbnailUrl,
          outOfDiskSpaceError = outOfDiskSpaceError,
          outputDirError = outputDirError
        )
      }

      val newProgress1 = mutex.withLock {
        totalProgress += progressIncrement
        totalProgress
      }
      threadDownloadProgressNotifier.notifyProgressEvent(
        threadDescriptor,
        ThreadDownloadProgressNotifier.Event.Progress(newProgress1)
      )

      val fullImageUrl = postImage.imageUrl
      val fullImageName = postImage.imageUrl?.extractFileName()

      if (fullImageUrl != null && fullImageName.isNotNullNorEmpty()) {
        downloadImage(
          outputDirectory = outputDirectory,
          isThumbnail = false,
          name = fullImageName,
          imageUrl = fullImageUrl,
          outOfDiskSpaceError = outOfDiskSpaceError,
          outputDirError = outputDirError
        )
      }

      val newProgress2 = mutex.withLock {
        totalProgress += progressIncrement
        totalProgress
      }
      threadDownloadProgressNotifier.notifyProgressEvent(
        threadDescriptor,
        ThreadDownloadProgressNotifier.Event.Progress(newProgress2)
      )
    }

    Logger.d(TAG, "processThreadMedia($index/$total) chanThread=${threadDescriptor} success")
  }

  private suspend fun downloadImage(
    outputDirectory: AbstractFile,
    isThumbnail: Boolean,
    name: String,
    imageUrl: HttpUrl,
    outOfDiskSpaceError: AtomicBoolean,
    outputDirError: AtomicBoolean,
  ) {
    var outputFile = fileManager.findFile(outputDirectory, name)
    if (outputFile == null) {
      outputFile = fileManager.create(outputDirectory, listOf(FileSegment(name)))
    }

    if (outputFile == null) {
      outputDirError.set(true)
      return
    }

    if (fileManager.exists(outputFile) && fileManager.getLength(outputFile) > 0L) {
      // Already downloaded, nothing to do
      return
    }

    val site = siteResolver.findSiteForUrl(imageUrl.toString())
    val requestModifier = site?.requestModifier()

    val requestBuilder = Request.Builder()
      .url(imageUrl)
      .get()

    if (site != null && requestModifier != null) {
      if (isThumbnail) {
        requestModifier.modifyThumbnailGetRequest(site, requestBuilder)
      } else {
        requestModifier.modifyFullImageGetRequest(site, requestBuilder)
      }
    }

    val response = okHttpClient.suspendCall(requestBuilder.build())
    if (!response.isSuccessful) {
      Logger.e(TAG, "downloadImage(isThumbnail=$isThumbnail, name=$name, imageUrl=$imageUrl) " +
        "bad response code: ${response.code}")
      return
    }

    val responseBody = if (response.body == null) {
      Logger.e(TAG, "downloadImage(isThumbnail=$isThumbnail, name=$name, imageUrl=$imageUrl) " +
        "response body is null")
      return
    } else {
      response.body!!
    }

    try {
      val outputStream = fileManager.getOutputStream(outputFile)
      if (outputStream == null) {
        Logger.e(TAG, "downloadImage(isThumbnail=$isThumbnail, name=$name, imageUrl=$imageUrl) " +
          "failed to get output stream for file '${outputFile.getFullPath()}'")
        return
      }

      runInterruptible {
        responseBody.byteStream().use { inputStream ->
          outputStream.use { os ->
            inputStream.copyTo(os)
          }
        }
      }
    } catch (error: Throwable) {
      if (error.isOutOfDiskSpaceError()) {
        outOfDiskSpaceError.set(true)
      }

      Logger.e(TAG, "Failed to store image into file '$outputFile', deleting it. " +
        "Error: ${error.errorMessageOrClassName()}")
      fileManager.delete(outputFile)
    } finally {
      responseBody.closeQuietly()
    }
  }

  companion object {
    private const val TAG = "ThreadDownloadingDelegate"
    private const val NO_MEDIA_FILE_NAME = ".nomedia"

    fun formatDirectoryName(threadDescriptor: ChanDescriptor.ThreadDescriptor): String {
      return buildString {
        append(threadDescriptor.siteName())
        append("_")
        append(threadDescriptor.boardCode())
        append("_")
        append(threadDescriptor.threadNo)
      }
    }
  }

}