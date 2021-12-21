package com.github.k1rakishou.chan.features.thread_downloading

import android.net.ConnectivityManager
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.okhttp.RealDownloaderOkHttpClient
import com.github.k1rakishou.chan.core.helper.ThreadDownloaderFileManagerWrapper
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.usecase.DownloadParams
import com.github.k1rakishou.chan.core.usecase.ThreadDownloaderPersistPostsInDatabaseUseCase
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
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
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.repository.ChanPostImageRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.closeQuietly
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class ThreadDownloadingDelegate(
  private val appConstants: AppConstants,
  private val downloaderOkHttpClient: Lazy<RealDownloaderOkHttpClient>,
  private val siteManager: SiteManager,
  private val siteResolver: SiteResolver,
  private val threadDownloadManager: ThreadDownloadManager,
  private val chanPostRepository: ChanPostRepository,
  private val chanPostImageRepository: ChanPostImageRepository,
  private val threadDownloaderFileManagerWrapper: ThreadDownloaderFileManagerWrapper,
  private val threadDownloadProgressNotifier: ThreadDownloadProgressNotifier,
  private val threadDownloaderPersistPostsInDatabaseUseCase: ThreadDownloaderPersistPostsInDatabaseUseCase
) {
  private val fileManager: FileManager
    get() = threadDownloaderFileManagerWrapper.fileManager
  private val okHttpClient: OkHttpClient
    get() = downloaderOkHttpClient.get().okHttpClient()
  private val batchCount = appConstants.processorsCount

  private val _running = AtomicBoolean(false)
  val running: Boolean
    get() = _running.get()

  @OptIn(ExperimentalTime::class)
  suspend fun doWork(): ModularResult<Unit> {
    return ModularResult.Try {
      if (!_running.compareAndSet(false, true)) {
        Logger.d(TAG, "doWorkInternal() already running")
        return@Try
      }

      val (result, duration) = measureTimedValue {
        try {
          doWorkInternal()
        } finally {
          _running.set(false)
        }
      }

      Logger.d(TAG, "doWorkInternal() took $duration")

      return@Try result
    }
  }

  private suspend fun doWorkInternal() {
    siteManager.awaitUntilInitialized()
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
    val canceled = AtomicBoolean(false)

    threadDownloads.forEachIndexed { index, threadDownload ->
      try {
        if (outOfDiskSpaceError.get() || canceled.get()) {
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
        canceled.set(true)
      }
    }

    coroutineContext[Job.Key]?.invokeOnCompletion { cause ->
      if (cause is CancellationException) {
        threadDownloads.forEach { threadDownload ->
          threadDownloadProgressNotifier.notifyProgressEvent(
            threadDownload.threadDescriptor,
            ThreadDownloadProgressNotifier.Event.Empty
          )
        }
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

    val params = DownloadParams(threadDownload.ownerThreadDatabaseId, threadDescriptor)
    val executionResult = threadDownloaderPersistPostsInDatabaseUseCase.execute(params)

    threadDownloadProgressNotifier.notifyProgressEvent(
      threadDownload.threadDescriptor,
      ThreadDownloadProgressNotifier.Event.Progress(POSTS_PROCESSED_PROGRESS)
    )

    val downloadResult = if (executionResult is ModularResult.Error) {
      Logger.e(TAG, "processThread($index/$total) loadThreadOrCatalog($threadDescriptor)", executionResult.error)

      threadDownloadManager.onDownloadProcessed(
        threadDescriptor = threadDescriptor,
        resultMessage = executionResult.error.message
          ?: executionResult.error.errorMessageOrClassName()
      )

      return
    } else {
      executionResult as ModularResult.Value
      executionResult.value
    }

    val ownerThreadDatabaseId = threadDownload.ownerThreadDatabaseId

    val isNetworkGoodForMediaDownload = if (ChanSettings.threadDownloaderDownloadMediaOnMeteredNetwork.get()) {
      true
    } else {
      AppModuleAndroidUtils.isConnected(ConnectivityManager.TYPE_WIFI)
    }

    val canProcessThreadMedia = threadDownload.downloadMedia
      && !outOfDiskSpaceError.get()
      && isNetworkGoodForMediaDownload

    if (canProcessThreadMedia) {
      val chanPostImages = chanPostImageRepository.selectPostImagesByOwnerThreadDatabaseId(ownerThreadDatabaseId)
        .peekError { error -> Logger.e(TAG, "Failed to select images by threadId: ${ownerThreadDatabaseId}", error) }
        .mapErrorToValue { emptyList<ChanPostImage>() }

      processThreadMedia(
        index = index,
        total = total,
        chanPostImages = chanPostImages,
        threadDescriptor = threadDescriptor,
        outOfDiskSpaceError = outOfDiskSpaceError,
        outputDirError = outputDirError
      )
    } else {
      Logger.d(TAG, "processThread($index/$total) " +
        "isNetworkGoodForMediaDownload=$isNetworkGoodForMediaDownload, " +
        "downloadMedia=${threadDownload.downloadMedia}, " +
        "outOfDiskSpaceError=${outOfDiskSpaceError.get()}")
    }

    val resultMessage = when {
      outOfDiskSpaceError.get() -> "Out of disk space error"
      outputDirError.get() -> "Output directory access error"
      else -> null
    }

    threadDownloadManager.onDownloadProcessed(
      threadDescriptor = threadDescriptor,
      resultMessage = resultMessage
    )

    if (downloadResult.archived || downloadResult.closed || downloadResult.deleted) {
      threadDownloadManager.completeDownloading(threadDescriptor)
    }

    val status = "archived: ${downloadResult.archived}, " +
      "closed: ${downloadResult.closed}, " +
      "deleted: ${downloadResult.deleted}, " +
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
      Logger.d(TAG, "processThreadMedia($index/$total) threadDescriptor=${threadDescriptor}, " +
        "chanPostImages=${chanPostImages.size}, nothing to process")
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

    // "* 2" because thumbnails and full images
    val progressIncrement = (1f - POSTS_PROCESSED_PROGRESS) / (chanPostImages.size.toFloat() * 2)
    val mutex = Mutex()
    var totalProgress = POSTS_PROCESSED_PROGRESS

    processDataCollectionConcurrently(
      dataList = chanPostImages,
      batchCount = batchCount,
      dispatcher = Dispatchers.IO
    ) { postImage ->
      val isNetworkGoodForMediaDownload = if (ChanSettings.threadDownloaderDownloadMediaOnMeteredNetwork.get()) {
        true
      } else {
        AppModuleAndroidUtils.isConnected(ConnectivityManager.TYPE_WIFI)
      }

      if (!isNetworkGoodForMediaDownload) {
        return@processDataCollectionConcurrently
      }

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
    private const val POSTS_PROCESSED_PROGRESS = 0.2f

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