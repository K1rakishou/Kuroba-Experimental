package com.github.k1rakishou.chan.features.thread_downloading

import android.net.Uri
import com.github.k1rakishou.chan.core.base.okhttp.DownloaderOkHttpClient
import com.github.k1rakishou.chan.core.helper.ThreadDownloaderFileManagerWrapper
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.isOutOfDiskSpaceError
import com.github.k1rakishou.common.processDataCollectionConcurrentlyIndexed
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.DirectorySegment
import com.github.k1rakishou.fsaf.file.FileSegment
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.options.ChanReadOptions
import com.github.k1rakishou.model.data.thread.ChanThread
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.closeQuietly
import java.util.concurrent.atomic.AtomicBoolean

class ThreadDownloadingDelegate(
  private val appConstants: AppConstants,
  private val downloaderOkHttpClient: DownloaderOkHttpClient,
  private val siteManager: SiteManager,
  private val siteResolver: SiteResolver,
  private val threadDownloadManager: ThreadDownloadManager,
  private val chanThreadManager: ChanThreadManager,
  private val chanPostRepository: ChanPostRepository,
  private val threadDownloaderFileManagerWrapper: ThreadDownloaderFileManagerWrapper
) {
  private val fileManager: FileManager
    get() = threadDownloaderFileManagerWrapper.fileManager
  private val okHttpClient: OkHttpClient
    get() = downloaderOkHttpClient.okHttpClient()

  suspend fun doWork(rootDir: Uri): ModularResult<Unit> {
    return ModularResult.Try { doWorkInternal(rootDir) }
  }

  private suspend fun doWorkInternal(rootDir: Uri) {
    siteManager.awaitUntilInitialized()
    threadDownloadManager.awaitUntilInitialized()
    chanPostRepository.awaitUntilInitialized()

    if (!threadDownloadManager.hasActiveThreads()) {
      Logger.d(TAG, "doWorkInternal() no active threads left, exiting")
      return
    }

    val threadDownloads = threadDownloadManager.getAllActiveThreadDownloads()
    val batchCount = appConstants.processorsCount

    Logger.d(TAG, "doWorkInternal() start, batchCount=$batchCount, rootDir='$rootDir'")
    threadDownloads.forEach { threadDownload ->
      Logger.d(TAG, "doWorkInternal() threadDownload=$threadDownload")
    }

    val outOfDiskSpaceError = AtomicBoolean(false)
    val outputDirError = AtomicBoolean(false)

    processDataCollectionConcurrentlyIndexed(
      dataList = threadDownloads,
      batchCount = batchCount
    ) { index, threadDownload ->
      try {
        if (outOfDiskSpaceError.get()) {
          return@processDataCollectionConcurrentlyIndexed
        }

        processThread(
          threadDownload = threadDownload,
          rootDirUri = rootDir,
          index = index + 1,
          total = threadDownloads.size,
          outOfDiskSpaceError = outOfDiskSpaceError,
          outputDirError = outputDirError
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
    rootDirUri: Uri,
    index: Int,
    total: Int,
    outOfDiskSpaceError: AtomicBoolean,
    outputDirError: AtomicBoolean,
  ) {
    val threadDescriptor = threadDownload.threadDescriptor
    Logger.d(TAG, "processThread($index/$total) loadThreadOrCatalog($threadDescriptor) start")

    // Preload thread from the database if we have anything there first.
    // We want to do this so that we don't have to reparse full threads over and over again.
    // Plus some sites support incremental thread updating so we might as well use it here.
    // If we fail to preload for some reason then just do everything from scratch.
    chanPostRepository.preloadForThread(threadDescriptor)
      .peekError { error -> Logger.e(TAG, "chanPostRepository.preloadForThread($threadDescriptor) error", error) }
      .ignore()

    val threadLoadResult = chanThreadManager.loadThreadOrCatalog(
      threadDescriptor,
      ChanCacheUpdateOptions.UpdateCache,
      ChanLoadOptions.retainAll(),
      ChanCacheOptions.cacheEverywhere(),
      ChanReadOptions.default()
    )

    if (threadLoadResult is ThreadLoadResult.Error) {
      Logger.e(TAG, "processThread($index/$total) loadThreadOrCatalog($threadDescriptor) " +
          "error: ${threadLoadResult.exception.errorMessage}")

      return
    }

    val chanThread = chanThreadManager.getChanThread(threadDescriptor)
    if (chanThread == null) {
      Logger.d(TAG, "processThread($index/$total) getChanThread($threadDescriptor) returned null")
      return
    }

    if (threadDownload.downloadMedia && !outOfDiskSpaceError.get()) {
      val rootDir = fileManager.fromUri(rootDirUri)

      processThreadMedia(
        index = index,
        total = total,
        rootDir = rootDir,
        chanThread = chanThread,
        outOfDiskSpaceError = outOfDiskSpaceError,
        outputDirError = outputDirError
      )
    }

    threadDownloadManager.onDownloadProcessed(
      threadDescriptor = threadDescriptor,
      outOfDiskSpaceError = outOfDiskSpaceError.get(),
      outputDirError = outputDirError.get()
    )

    val originalPost = chanThread.getOriginalPost()
    if (originalPost.archived || originalPost.closed || originalPost.deleted) {
      threadDownloadManager.completeDownloading(threadDescriptor)
    }

    val status = "archived: ${originalPost.archived}, " +
      "closed: ${originalPost.closed}, " +
      "deleted: ${originalPost.deleted}, " +
      "postsCount: ${chanThread.postsCount}, " +
      "outOfDiskSpace: ${outOfDiskSpaceError.get()}, " +
      "outputDirError: ${outputDirError.get()}, "

    Logger.d(TAG, "processThread($index/$total) loadThreadOrCatalog($threadDescriptor) end, status: $status")
  }

  private suspend fun processThreadMedia(
    index: Int,
    total: Int,
    rootDir: AbstractFile?,
    chanThread: ChanThread,
    outOfDiskSpaceError: AtomicBoolean,
    outputDirError: AtomicBoolean,
  ) {
    if (rootDir == null) {
      Logger.d(TAG, "processThreadMedia($index/$total) rootDir == null")
      outputDirError.set(true)
      return
    }

    Logger.d(TAG, "processThreadMedia($index/$total) chanThread=${chanThread.threadDescriptor}")

    val threadSubject = StringUtils.dirNameRemoveBadCharacters(
      ChanPostUtils.getTitle(chanThread.getOriginalPost(), chanThread.threadDescriptor)
    )

    val directoryName = buildString {
      append(chanThread.threadDescriptor.siteName())
      append("_")
      append(chanThread.threadDescriptor.boardCode())
      append("_")
      append(chanThread.threadDescriptor.threadNo)
      append("_")

      if (threadSubject.isNotNullNorBlank()) {
        append(StringUtils.dirNameRemoveBadCharacters(threadSubject))
      }
    }

    val outputDirectory = fileManager.create(rootDir, DirectorySegment(directoryName))
    if (outputDirectory == null) {
      Logger.d(TAG, "processThreadMedia($index/$total) " +
        "chanThread=${chanThread.threadDescriptor} failure! outputDirectory is null")
      outputDirError.set(true)
      return
    }

    supervisorScope {
      for (postImage in chanThread.getThreadPostImages()) {
        ensureActive()

        if (outOfDiskSpaceError.get()) {
          return@supervisorScope
        }

        if (outputDirError.get()) {
          return@supervisorScope
        }

        val thumbnailUrl = postImage.actualThumbnailUrl
        val thumbnailName = postImage.actualThumbnailUrl?.pathSegments?.lastOrNull()

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

        val fullImageUrl = postImage.imageUrl
        val fullImageName = postImage.imageUrl?.pathSegments?.lastOrNull()

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
      }
    }

    Logger.d(TAG, "processThreadMedia($index/$total) chanThread=${chanThread.threadDescriptor} success")
  }

  private suspend fun downloadImage(
    outputDirectory: AbstractFile,
    isThumbnail: Boolean,
    name: String,
    imageUrl: HttpUrl,
    outOfDiskSpaceError: AtomicBoolean,
    outputDirError: AtomicBoolean,
  ) {
    val outputFile = fileManager.create(outputDirectory, FileSegment(name))
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

      responseBody.byteStream().use { inputStream ->
        outputStream.use { os ->
          inputStream.copyTo(os)
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
  }

}