package com.github.k1rakishou.chan.features.image_saver

import android.net.Uri
import androidx.annotation.GuardedBy
import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.base.okhttp.RealDownloaderOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.helper.ImageSaverFileManagerWrapper
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.HashingUtil
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.doIoTaskWithAttempts
import com.github.k1rakishou.common.extractFileName
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.isOutOfDiskSpaceError
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.DirectorySegment
import com.github.k1rakishou.fsaf.file.FileSegment
import com.github.k1rakishou.fsaf.file.Segment
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.download.ImageDownloadRequest
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.repository.ChanPostImageRepository
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@OptIn(ObsoleteCoroutinesApi::class)
class ImageSaverV2ServiceDelegate(
  private val verboseLogs: Boolean,
  private val appScope: CoroutineScope,
  private val appConstants: AppConstants,
  private val cacheHandler: Lazy<CacheHandler>,
  private val downloaderOkHttpClient: Lazy<RealDownloaderOkHttpClient>,
  private val notificationManagerCompat: NotificationManagerCompat,
  private val imageSaverFileManager: ImageSaverFileManagerWrapper,
  private val siteResolver: SiteResolver,
  private val chanPostImageRepository: ChanPostImageRepository,
  private val imageDownloadRequestRepository: ImageDownloadRequestRepository,
  private val chanThreadManager: ChanThreadManager,
  private val threadDownloadManager: ThreadDownloadManager
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val activeDownloads = hashMapOf<String, DownloadContext>()
  @GuardedBy("mutex")
  private val activeNotificationIdQueue = LinkedList<String>()
  private val cancelNotificationJobMap = ConcurrentHashMap<String, Job>()

  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

  private val fileManager: FileManager
    get() = imageSaverFileManager.fileManager

  private val notificationUpdatesFlow = MutableSharedFlow<ImageSaverDelegateResult>(
    extraBufferCapacity = 16,
    onBufferOverflow = BufferOverflow.SUSPEND
  )

  private val stopServiceFlow = MutableSharedFlow<ServiceStopCommand>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  fun listenForNotificationUpdates(): SharedFlow<ImageSaverDelegateResult> {
    return notificationUpdatesFlow.asSharedFlow()
  }

  fun listenForStopServiceEvent(): SharedFlow<ServiceStopCommand> {
    return stopServiceFlow.asSharedFlow()
  }

  suspend fun deleteDownload(uniqueId: String) {
    Logger.d(TAG, "deleteDownload('$uniqueId')")
    ImageSaverV2Service.cancelNotification(notificationManagerCompat, uniqueId)

    imageDownloadRequestRepository.deleteByUniqueId(uniqueId)
      .peekError { error -> Logger.e(TAG, "imageDownloadRequestRepository.deleteByUniqueId($uniqueId) error", error) }
      .ignore()

    mutex.withLock {
      activeNotificationIdQueue.remove(uniqueId)
      activeDownloads.remove(uniqueId)
    }
  }

  suspend fun cancelDownload(uniqueId: String) {
    Logger.d(TAG, "cancelDownload('$uniqueId')")

    ImageSaverV2Service.cancelNotification(notificationManagerCompat, uniqueId)

    mutex.withLock {
      activeNotificationIdQueue.remove(uniqueId)
      activeDownloads[uniqueId]?.cancel()
    }
  }

  suspend fun createDownloadContext(uniqueId: String): Int {
    return mutex.withLock {
      if (!activeDownloads.containsKey(uniqueId)) {
        activeDownloads[uniqueId] = DownloadContext()
      }

      // If we were waiting the timeout before auto-closing the notification, we need to cancel it
      // since we are restarting the download request. Otherwise the notification may get hidden
      // while we preparing to start downloading it.
      cancelNotificationJobMap[uniqueId]?.cancel()
      cancelNotificationJobMap.remove(uniqueId)

      // Otherwise the download already exist, just wait until it's completed. But this shouldn't
      // really happen since we always check duplicate requests in the database before even starting
      // the service.

      return@withLock activeDownloads.size
    }
  }

  suspend fun downloadImages(imageDownloadInputData: ImageSaverV2Service.ImageDownloadInputData) {
    stopServiceFlow.emit(ServiceStopCommand.Cancel)

    serializedCoroutineExecutor.post {
      try {
        val activeDownloadsBefore = mutex.withLock { activeDownloads.size }
        Logger.d(TAG, "downloadImages() start, activeDownloadsBefore=$activeDownloadsBefore")

        val activeDownloadsCountAfter = withContext(Dispatchers.Default) {
          downloadImagesInternal(imageDownloadInputData)
        }

        Logger.d(TAG, "downloadImages() end, activeDownloadsCountAfter=$activeDownloadsCountAfter")

        if (activeDownloadsCountAfter <= 0) {
          stopServiceFlow.emit(ServiceStopCommand.Enqueue)
        }
      } catch (error: Throwable) {
        Logger.e(TAG, "downloadImages() Unhandled exception", error)
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun downloadImagesInternal(
    imageDownloadInputData: ImageSaverV2Service.ImageDownloadInputData
  ): Int {
    BackgroundUtils.ensureBackgroundThread()

    val uniqueId = imageDownloadInputData.uniqueId
    val outputDirUri = AtomicReference<Uri>(null)
    val currentChanPostImage = AtomicReference<ChanPostImage>(null)
    val hasResultDirAccessErrors = AtomicBoolean(false)
    val hasOutOfDiskSpaceErrors = AtomicBoolean(false)
    val hasRequestsThatCanBeRetried = AtomicBoolean(false)
    val completedRequests = AtomicInteger(0)
    val failedRequests = AtomicInteger(0)
    val duplicates = AtomicInteger(0)
    val canceledRequests = AtomicInteger(0)

    val imageDownloadRequests = when (imageDownloadInputData) {
      is ImageSaverV2Service.SingleImageDownloadInputData -> {
        listOf(imageDownloadInputData.imageDownloadRequest)
      }
      is ImageSaverV2Service.BatchImageDownloadInputData -> {
        imageDownloadInputData.imageDownloadRequests
      }
      else -> {
        throw IllegalArgumentException(
          "Unknown imageDownloadInputData: " +
            imageDownloadInputData.javaClass.simpleName
        )
      }
    }

    try {
      val canceled = getDownloadContext(imageDownloadInputData)?.isCanceled() ?: true
      if (canceled) {
        Logger.d(TAG, "downloadImagesInternal() " +
          "imageDownloadInputData=${imageDownloadInputData.javaClass.simpleName}, " +
          "imagesCount=${imageDownloadInputData.requestsCount()} canceled, uniqueId=${uniqueId}")

        // Canceled, no need to send even here because we do that in try/finally block
        return mutex.withLock { activeDownloads.size }
      }

      Logger.d(TAG, "downloadImagesInternal() " +
        "imageDownloadInputData=${imageDownloadInputData.javaClass.simpleName}, " +
        "imagesCount=${imageDownloadInputData.requestsCount()}")

      handleNewNotificationId(imageDownloadInputData.uniqueId)

      // Start event
      emitNotificationUpdate(
        uniqueId = imageDownloadInputData.uniqueId,
        imageSaverOptionsJson = imageDownloadInputData.imageSaverOptionsJson,
        completed = false,
        notificationSummary = null,
        totalImagesCount = imageDownloadInputData.requestsCount(),
        canceledRequests = canceledRequests.get(),
        completedRequests = completedRequestsToDownloadedImagesResult(
          completedRequests = completedRequests,
          outputDirUri = outputDirUri
        ),
        duplicates = duplicates.get(),
        failedRequests = failedRequests.get(),
        hasResultDirAccessErrors = hasResultDirAccessErrors.get(),
        hasOutOfDiskSpaceErrors = hasOutOfDiskSpaceErrors.get(),
        hasRequestsThatCanBeRetried = hasRequestsThatCanBeRetried.get()
      )

      supervisorScope {
        imageDownloadRequests
          .chunked(appConstants.processorsCount * 2)
          .forEach { imageDownloadRequestBatch ->
            val updatedImageDownloadRequestBatch = imageDownloadRequestBatch.map { imageDownloadRequest ->
              return@map appScope.async(Dispatchers.IO) {
                val (outImageDownloadRequest, duration) = measureTimedValue {
                  return@measureTimedValue downloadSingleImage(
                    imageDownloadInputData = imageDownloadInputData,
                    imageDownloadRequest = imageDownloadRequest,
                    hasResultDirAccessErrors = hasResultDirAccessErrors,
                    hasOutOfDiskSpaceErrors = hasOutOfDiskSpaceErrors,
                    hasRequestsThatCanBeRetried = hasRequestsThatCanBeRetried,
                    currentChanPostImage = currentChanPostImage,
                    canceledRequests = canceledRequests,
                    duplicates = duplicates,
                    failedRequests = failedRequests,
                    outputDirUri = outputDirUri,
                    completedRequests = completedRequests
                  )
                }

                if (verboseLogs) {
                  Logger.d(TAG, "downloadSingleImage(${imageDownloadRequest.imageFullUrl}) took $duration")
                }

                return@async outImageDownloadRequest
              }
            }.awaitAll()

            imageDownloadRequestRepository.completeMany(updatedImageDownloadRequestBatch)
              .peekError { error -> Logger.e(TAG, "imageDownloadRequestRepository.updateMany() error", error) }
              .ignore()

            val canceledNow = (getDownloadContext(imageDownloadInputData)?.isCanceled() ?: true)
              || hasResultDirAccessErrors.get() || hasOutOfDiskSpaceErrors.get()

            if (!canceledNow) {
              val notificationSummary = extractNotificationSummaryText(
                imageDownloadInputData = imageDownloadInputData,
                currentChanPostImage = currentChanPostImage,
                imageDownloadRequests = imageDownloadRequests,
                isCompleted = false
              )

              // Progress event
              emitNotificationUpdate(
                uniqueId = imageDownloadInputData.uniqueId,
                imageSaverOptionsJson = imageDownloadInputData.imageSaverOptionsJson,
                completed = false,
                notificationSummary = notificationSummary,
                totalImagesCount = imageDownloadInputData.requestsCount(),
                canceledRequests = canceledRequests.get(),
                completedRequests = completedRequestsToDownloadedImagesResult(
                  completedRequests = completedRequests,
                  outputDirUri = outputDirUri
                ),
                duplicates = duplicates.get(),
                failedRequests = failedRequests.get(),
                hasResultDirAccessErrors = hasResultDirAccessErrors.get(),
                hasOutOfDiskSpaceErrors = hasOutOfDiskSpaceErrors.get(),
                hasRequestsThatCanBeRetried = hasRequestsThatCanBeRetried.get()
              )
            }
          }
      }
    } finally {
      val notificationSummary = extractNotificationSummaryText(
        imageDownloadInputData = imageDownloadInputData,
        currentChanPostImage = currentChanPostImage,
        imageDownloadRequests = imageDownloadRequests,
        isCompleted = true
      )

      // End event
      emitNotificationUpdate(
        uniqueId = imageDownloadInputData.uniqueId,
        imageSaverOptionsJson = imageDownloadInputData.imageSaverOptionsJson,
        completed = true,
        notificationSummary = notificationSummary,
        totalImagesCount = imageDownloadInputData.requestsCount(),
        canceledRequests = canceledRequests.get(),
        completedRequests = completedRequestsToDownloadedImagesResult(
          completedRequests = completedRequests,
          outputDirUri = outputDirUri
        ),
        duplicates = duplicates.get(),
        failedRequests = failedRequests.get(),
        hasResultDirAccessErrors = hasResultDirAccessErrors.get(),
        hasOutOfDiskSpaceErrors = hasOutOfDiskSpaceErrors.get(),
        hasRequestsThatCanBeRetried = hasRequestsThatCanBeRetried.get()
      )

      mutex.withLock { activeDownloads.remove(imageDownloadInputData.uniqueId) }
    }

    return mutex.withLock { activeDownloads.size }
  }

  private fun extractNotificationSummaryText(
    imageDownloadInputData: ImageSaverV2Service.ImageDownloadInputData,
    currentChanPostImage: AtomicReference<ChanPostImage>,
    imageDownloadRequests: List<ImageDownloadRequest>,
    isCompleted: Boolean
  ): String? {
    return currentChanPostImage.get()?.let { chanPostImage ->
      if (!isCompleted || imageDownloadRequests.size <= 1) {
        val imageNameOptions = ImageSaverV2Options.ImageNameOptions.fromRawValue(
          imageDownloadInputData.imageSaverV2Options.imageNameOptions
        )

        val imageFileName = when (imageNameOptions) {
          ImageSaverV2Options.ImageNameOptions.UseServerFileName -> chanPostImage.serverFilename
          ImageSaverV2Options.ImageNameOptions.UseOriginalFileName -> chanPostImage.filename
        }

        if (imageFileName.isNotNullNorBlank()) {
          val extension = chanPostImage.extension
          if (extension.isNullOrBlank()) {
            return@let imageFileName
          }

          return@let "$imageFileName.$extension"
        }

        return@let chanPostImage.imageUrl?.extractFileName()
      }

      val threadDescriptor = chanPostImage.ownerPostDescriptor.threadDescriptor()

      return@let buildString {
        append(threadDescriptor.siteName())
        append("/")
        append(threadDescriptor.boardCode())
        append("/")
        append(threadDescriptor.threadNo)
        append(" (")
        append(imageDownloadRequests.size)
        append(")")
      }
    }
  }

  private suspend fun downloadSingleImage(
    imageDownloadInputData: ImageSaverV2Service.ImageDownloadInputData,
    imageDownloadRequest: ImageDownloadRequest,
    hasResultDirAccessErrors: AtomicBoolean,
    hasOutOfDiskSpaceErrors: AtomicBoolean,
    hasRequestsThatCanBeRetried: AtomicBoolean,
    currentChanPostImage: AtomicReference<ChanPostImage>,
    canceledRequests: AtomicInteger,
    duplicates: AtomicInteger,
    failedRequests: AtomicInteger,
    outputDirUri: AtomicReference<Uri>,
    completedRequests: AtomicInteger
  ): ImageDownloadRequest {
    BackgroundUtils.ensureBackgroundThread()

    if (verboseLogs) {
      Logger.d(TAG, "downloadSingleImage() start uniqueId='${imageDownloadInputData.uniqueId}', " +
          "imageUrl='${imageDownloadRequest.imageFullUrl}'")
    }

    val downloadImageResult = downloadSingleImageInternal(
      hasResultDirAccessErrors,
      hasOutOfDiskSpaceErrors,
      currentChanPostImage,
      imageDownloadInputData,
      imageDownloadRequest
    )

    when (downloadImageResult) {
      is DownloadImageResult.Canceled -> {
        // Canceled by user by clicking "Cancel" notification action
        canceledRequests.incrementAndGet()
      }
      is DownloadImageResult.DuplicateFound -> {
        // Duplicate image (the same result file name) was found on disk and DuplicateResolution
        // setting is set to ask the user what to do.
        duplicates.incrementAndGet()
      }
      is DownloadImageResult.Failure -> {
        // Some error happened
        failedRequests.incrementAndGet()

        if (downloadImageResult.canRetry) {
          hasRequestsThatCanBeRetried.set(true)
        }
      }
      is DownloadImageResult.Success -> {
        // Image successfully downloaded
        if (!outputDirUri.compareAndSet(null, downloadImageResult.outputDirUri)) {
          check(outputDirUri.get() == downloadImageResult.outputDirUri) {
            "outputDirUris differ! Expected: $outputDirUri, actual: ${downloadImageResult.outputDirUri}"
          }
        }

        completedRequests.incrementAndGet()
      }
      is DownloadImageResult.OutOfDiskSpaceError -> {
        hasOutOfDiskSpaceErrors.set(true)
        canceledRequests.incrementAndGet()
      }
      is DownloadImageResult.ResultDirectoryError -> {
        // Something have happened with the output directory (it was deleted or something like that)
        hasResultDirAccessErrors.set(true)
        canceledRequests.incrementAndGet()
      }
    }

    if (verboseLogs) {
      Logger.d(TAG, "downloadSingleImage() end uniqueId='${imageDownloadInputData.uniqueId}', " +
        "imageUrl='${imageDownloadRequest.imageFullUrl}', result=$downloadImageResult")
    }

    return ImageDownloadRequest(
      imageDownloadRequest.uniqueId,
      imageDownloadRequest.imageFullUrl,
      imageDownloadRequest.postDescriptorString,
      imageDownloadRequest.newFileName,
      downloadImageResultToStatus(downloadImageResult),
      getDuplicateUriOrNull(downloadImageResult),
      imageDownloadRequest.duplicatesResolution,
      imageDownloadRequest.createdOn
    )
  }

  private suspend fun handleNewNotificationId(uniqueId: String) {
    val notificationsIdToDelete = mutex.withLock {
      if (!activeNotificationIdQueue.contains(uniqueId)) {
        activeNotificationIdQueue.push(uniqueId)
      }

      val maxNotifications = if (AppModuleAndroidUtils.isDevBuild()) {
        MAX_VISIBLE_NOTIFICATIONS_TEST
      } else {
        MAX_VISIBLE_NOTIFICATIONS_PROD
      }

      if (activeNotificationIdQueue.size > maxNotifications) {
        var count = (maxNotifications - activeNotificationIdQueue.size).coerceAtLeast(1)
        val notificationsIdToDelete = mutableListOf<String>()

        for (notificationId in activeNotificationIdQueue.asReversed()) {
          if (count <= 0) {
            break
          }

          val notExistsOrCanceled = activeDownloads[notificationId]?.isCanceled()
            ?: true

          if (notExistsOrCanceled) {
            notificationsIdToDelete += notificationId
            --count
          }
        }

        return@withLock notificationsIdToDelete
      }

      return@withLock emptyList<String>()
    }

    if (notificationsIdToDelete.isEmpty()) {
      return
    }

    Logger.d(TAG, "handleNewNotificationId() notificationsIdToDelete=${notificationsIdToDelete}")

    notificationsIdToDelete.forEach { notificationIdToDelete ->
      deleteDownload(notificationIdToDelete)
    }
  }

  private fun completedRequestsToDownloadedImagesResult(
    completedRequests: AtomicInteger,
    outputDirUri: AtomicReference<Uri>
  ): DownloadedImages {
    return if (completedRequests.get() == 0) {
      DownloadedImages.Empty
    } else {
      DownloadedImages.Multiple(outputDirUri.get(), completedRequests.get())
    }
  }

  private fun getDuplicateUriOrNull(downloadImageResult: DownloadImageResult): Uri? {
    if (downloadImageResult is DownloadImageResult.DuplicateFound) {
      return downloadImageResult.duplicate.imageOnDiskUri
    }

    return null
  }

  private fun downloadImageResultToStatus(
    downloadImageResult: DownloadImageResult
  ): ImageDownloadRequest.Status {
    return when (downloadImageResult) {
      is DownloadImageResult.Canceled -> ImageDownloadRequest.Status.Canceled
      is DownloadImageResult.DuplicateFound -> ImageDownloadRequest.Status.ResolvingDuplicate
      is DownloadImageResult.Success -> ImageDownloadRequest.Status.Downloaded
      is DownloadImageResult.Failure,
      is DownloadImageResult.ResultDirectoryError,
      is DownloadImageResult.OutOfDiskSpaceError -> ImageDownloadRequest.Status.DownloadFailed
    }
  }

  private suspend fun emitNotificationUpdate(
    uniqueId: String,
    imageSaverOptionsJson: String,
    completed: Boolean,
    notificationSummary: String?,
    totalImagesCount: Int,
    canceledRequests: Int,
    completedRequests: DownloadedImages,
    duplicates: Int,
    failedRequests: Int,
    hasResultDirAccessErrors: Boolean,
    hasOutOfDiskSpaceErrors: Boolean,
    hasRequestsThatCanBeRetried: Boolean
  ) {
    BackgroundUtils.ensureBackgroundThread()

    val imageSaverDelegateResult = ImageSaverDelegateResult(
      uniqueId = uniqueId,
      imageSaverOptionsJson = imageSaverOptionsJson,
      completed = completed,
      notificationSummary = notificationSummary,
      totalImagesCount = totalImagesCount,
      canceledRequests = canceledRequests,
      downloadedImages = completedRequests,
      duplicates = duplicates,
      failedRequests = failedRequests,
      hasResultDirAccessErrors = hasResultDirAccessErrors,
      hasOutOfDiskSpaceErrors =  hasOutOfDiskSpaceErrors,
      hasRequestsThatCanBeRetried = hasRequestsThatCanBeRetried
    )

    notificationUpdatesFlow.emit(imageSaverDelegateResult)
  }

  private fun formatFileName(
    imageSaverV2Options: ImageSaverV2Options,
    postImage: ChanPostImage,
    imageDownloadRequest: ImageDownloadRequest
  ): String {
    var fileName = when (ImageSaverV2Options.ImageNameOptions.fromRawValue(imageSaverV2Options.imageNameOptions)) {
      ImageSaverV2Options.ImageNameOptions.UseServerFileName -> postImage.serverFilename
      ImageSaverV2Options.ImageNameOptions.UseOriginalFileName -> postImage.filename
        ?: postImage.serverFilename
    }

    if (imageDownloadRequest.newFileName.isNotNullNorBlank()) {
      fileName = imageDownloadRequest.newFileName!!
    }

    val extension = postImage.extension ?: "jpg"

    return "$fileName.$extension"
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  private fun getFullFileUri(
    chanPostImage: ChanPostImage,
    imageSaverV2Options: ImageSaverV2Options,
    imageDownloadRequest: ImageDownloadRequest,
    postDescriptor: PostDescriptor,
    fileName: String
  ): ResultFile {
    val rootDirectoryUri = Uri.parse(checkNotNull(imageSaverV2Options.rootDirectoryUri))

    val rootDirectory = fileManager.fromUri(rootDirectoryUri)
      ?: return ResultFile.FailedToOpenResultDir(rootDirectoryUri.toString())

    val segments = mutableListOf<Segment>()

    if (imageSaverV2Options.appendSiteName) {
      segments += DirectorySegment(postDescriptor.siteDescriptor().siteName)
    }

    if (imageSaverV2Options.appendBoardCode) {
      segments += DirectorySegment(postDescriptor.boardDescriptor().boardCode)
    }

    if (imageSaverV2Options.appendThreadId) {
      segments += DirectorySegment(postDescriptor.getThreadNo().toString())
    }

    if (imageSaverV2Options.appendThreadSubject) {
      val threadSubject = chanThreadManager.getSafeToUseThreadSubject(
        chanPostImage.ownerPostDescriptor.threadDescriptor()
      )

      if (threadSubject.isNotNullNorBlank()) {
        segments += DirectorySegment(StringUtils.dirNameRemoveBadCharacters(threadSubject)!!)
      }
    }

    if (imageSaverV2Options.subDirs.isNotNullNorBlank()) {
      val subDirs = imageSaverV2Options.subDirs!!.split('\\')

      subDirs.forEach { subDir ->
        if (subDir.isBlank()) {
          return@forEach
        }

        segments += DirectorySegment(subDir)
      }
    }

    val resultDir = fileManager.create(rootDirectory, segments)
    if (resultDir == null) {
      return ResultFile.FailedToOpenResultDir(rootDirectory.clone(segments).getFullPath())
    }

    val resultFileName = StringUtils.fileNameRemoveBadCharacters(fileName)!!
    segments += FileSegment(resultFileName)

    val resultFile = rootDirectory.clone(segments)
    val resultFileUri = Uri.parse(resultFile.getFullPath())
    val resultDirUri = Uri.parse(resultDir.getFullPath())

    if (!fileManager.exists(resultFile)) {
      return ResultFile.File(
        resultDirUri,
        resultFileUri,
        resultFile
      )
    }

    var duplicatesResolution =
      ImageSaverV2Options.DuplicatesResolution.fromRawValue(imageSaverV2Options.duplicatesResolution)

    // If the setting is set to DuplicatesResolution.AskWhatToDo then check the duplicatesResolution
    // of imageDownloadRequest
    if (duplicatesResolution == ImageSaverV2Options.DuplicatesResolution.AskWhatToDo) {
      duplicatesResolution = imageDownloadRequest.duplicatesResolution
    }

    // Do not process images with the same name, size and hash as the local ones
    val areImagesExactlyTheSame = areImagesExactlyTheSame(chanPostImage, resultFile)
    if (areImagesExactlyTheSame) {
      val fileIsNotEmpty = fileManager.getLength(resultFile) > 0
      return ResultFile.Skip(resultDirUri, resultFileUri, fileIsNotEmpty)
    }

    when (duplicatesResolution) {
      ImageSaverV2Options.DuplicatesResolution.AskWhatToDo -> {
        return ResultFile.DuplicateFound(resultFileUri)
      }
      ImageSaverV2Options.DuplicatesResolution.Skip -> {
        val fileIsNotEmpty = fileManager.getLength(resultFile) > 0
        return ResultFile.Skip(resultDirUri, resultFileUri, fileIsNotEmpty)
      }
      ImageSaverV2Options.DuplicatesResolution.SaveAsDuplicate -> {
        var duplicateId = 1

        while (true) {
          val fileNameNoExtension = StringUtils.removeExtensionFromFileName(fileName)
          val extension = StringUtils.extractFileNameExtension(fileName)
          val newResultFile = resultDir.clone(FileSegment("${fileNameNoExtension}_($duplicateId).$extension"))

          if (!fileManager.exists(newResultFile)) {
            return ResultFile.File(
              resultDirUri,
              Uri.parse(newResultFile.getFullPath()),
              newResultFile
            )
          }

          ++duplicateId
        }
      }
      ImageSaverV2Options.DuplicatesResolution.Overwrite -> {
        if (!fileManager.delete(resultFile)) {
          return ResultFile.FailedToOpenResultDir(resultFile.getFullPath())
        }

        // Fallthrough, continue downloading the file
      }
    }

    return ResultFile.File(
      resultDirUri,
      resultFileUri,
      resultFile
    )
  }

  private fun areImagesExactlyTheSame(
    chanPostImage: ChanPostImage,
    resultFile: AbstractFile
  ): Boolean {
    if (chanPostImage.size != fileManager.getLength(resultFile)) {
      return false
    }

    if (chanPostImage.fileHash.isNullOrEmpty()) {
      return false
    }

    val localFileMd5 = fileManager.getInputStream(resultFile)
      ?.let { inputStream -> HashingUtil.inputStreamMd5(inputStream) }

    return chanPostImage.fileHash.equals(localFileMd5, ignoreCase = true)
  }

  sealed class ResultFile {
    data class File(
      val outputDirUri: Uri,
      val outputFileUri: Uri,
      val file: AbstractFile
    ) : ResultFile()

    data class DuplicateFound(val fileUri: Uri) : ResultFile()

    data class FailedToOpenResultDir(val dirPath: String) : ResultFile()

    data class Skip(
      val outputDirUri: Uri,
      val outputFileUri: Uri,
      val fileIsNotEmpty: Boolean
    ) : ResultFile()
  }

  // TODO(KurobaEx): more logs
  private suspend fun downloadSingleImageInternal(
    hasResultDirAccessErrors: AtomicBoolean,
    hasOutOfDiskSpaceErrors: AtomicBoolean,
    currentChanPostImage: AtomicReference<ChanPostImage>,
    imageDownloadInputData: ImageSaverV2Service.ImageDownloadInputData,
    imageDownloadRequest: ImageDownloadRequest
  ): DownloadImageResult {
    BackgroundUtils.ensureBackgroundThread()

    return ModularResult.Try {
      val canceled = getDownloadContext(imageDownloadInputData)
        ?.isCanceled()
        ?: true

      if (hasResultDirAccessErrors.get() || hasOutOfDiskSpaceErrors.get() || canceled) {
        return@Try DownloadImageResult.Canceled(imageDownloadRequest)
      }

      val imageSaverV2Options = imageDownloadInputData.imageSaverV2Options
      val imageFullUrl = imageDownloadRequest.imageFullUrl

      var postDescriptor: PostDescriptor? = PostDescriptor.deserializeFromString(imageDownloadRequest.postDescriptorString)
      var chanPostImage: ChanPostImage? = null

      if (postDescriptor != null) {
        chanPostImage = chanThreadManager.getPost(postDescriptor)
          ?.firstPostImageOrNull { cpi -> cpi.imageUrl == imageFullUrl }
      } else {
        val chanPostImageResult = chanPostImageRepository.selectPostImageByUrl(imageFullUrl)
        if (chanPostImageResult is ModularResult.Error) {
          return@Try DownloadImageResult.Failure(chanPostImageResult.error, true)
        }

        chanPostImage = (chanPostImageResult as ModularResult.Value).value
        postDescriptor = chanPostImage?.ownerPostDescriptor
      }

      if (chanPostImage == null) {
        val error = IOException("Failed to find image '$imageFullUrl' in thread cache/database")
        return@Try DownloadImageResult.Failure(error, false)
      }

      if (postDescriptor == null) {
        val error = IOException("Failed to find post descriptor of image '$imageFullUrl'")
        return@Try DownloadImageResult.Failure(error, false)
      }

      currentChanPostImage.set(chanPostImage)

      val fileName = formatFileName(
        imageSaverV2Options,
        chanPostImage!!,
        imageDownloadRequest
      )

      val outputFileResult = getFullFileUri(
        chanPostImage = chanPostImage!!,
        imageSaverV2Options = imageSaverV2Options,
        imageDownloadRequest = imageDownloadRequest,
        postDescriptor = postDescriptor,
        fileName = fileName
      )

      when (outputFileResult) {
        is ResultFile.DuplicateFound -> {
          val duplicateImage = DuplicateImage(
            imageDownloadRequest,
            outputFileResult.fileUri
          )

          return DownloadImageResult.DuplicateFound(duplicateImage)
        }
        is ResultFile.FailedToOpenResultDir -> {
          return@Try DownloadImageResult.ResultDirectoryError(
            outputFileResult.dirPath,
            imageDownloadRequest
          )
        }
        is ResultFile.Skip -> {
          if (outputFileResult.fileIsNotEmpty) {
            return@Try DownloadImageResult.Success(
              outputFileResult.outputDirUri,
              imageDownloadRequest
            )
          }

          val error = IOException("Duplicate file is empty")
          return@Try DownloadImageResult.Failure(error, true)
        }
        is ResultFile.File -> {
          // no-op
        }
      }

      val outputFile = outputFileResult.file
      val outputDirUri = outputFileResult.outputDirUri

      val actualOutputFile = fileManager.create(outputFile)
      if (actualOutputFile == null) {
        return@Try DownloadImageResult.ResultDirectoryError(
          outputFile.getFullPath(),
          imageDownloadRequest
        )
      }

      val imageUrl = checkNotNull(chanPostImage!!.imageUrl) { "Image url is empty!" }
      val threadDescriptor = chanPostImage!!.ownerPostDescriptor.threadDescriptor()

      try {
        doIoTaskWithAttempts(MAX_IO_ERROR_RETRIES_COUNT) {
          try {
            downloadFileIntoFile(imageUrl, actualOutputFile, threadDescriptor)
          } catch (error: IOException) {
            if (error.isOutOfDiskSpaceError()) {
              throw OutOfDiskSpaceException()
            }

            throw error
          }
        }
      } catch (error: Throwable) {
        fileManager.delete(actualOutputFile)

        return@Try when (error) {
          is OutOfDiskSpaceException -> {
            DownloadImageResult.OutOfDiskSpaceError(imageDownloadRequest)
          }
          is ResultFileAccessError -> {
            DownloadImageResult.ResultDirectoryError(error.resultFileUri, imageDownloadRequest)
          }
          is NotFoundException -> DownloadImageResult.Failure(error, false)
          is IOException -> DownloadImageResult.Failure(error, true)
          else -> throw error
        }
      }

      return@Try DownloadImageResult.Success(outputDirUri, imageDownloadRequest)
    }.mapErrorToValue { error -> DownloadImageResult.Failure(error, true) }
  }

  @Throws(ResultFileAccessError::class, IOException::class, NotFoundException::class)
  suspend fun downloadFileIntoFile(
    imageUrl: HttpUrl,
    outputFile: AbstractFile,
    threadDescriptor: ChanDescriptor.ThreadDescriptor?
  ) {
    BackgroundUtils.ensureBackgroundThread()

    val fileUrl = imageUrl.toString()
    val cacheFileType = CacheFileType.PostMediaFull
    var localInputStream: InputStream? = null

    try {
      if (cacheHandler.get().cacheFileExists(cacheFileType, fileUrl)) {
        val cachedFile = cacheHandler.get().getCacheFileOrNull(cacheFileType, fileUrl)
        if (cachedFile != null && cachedFile.canRead() && cachedFile.length() > 0) {
          localInputStream = cachedFile.inputStream()
        }
      }

      if (localInputStream == null
        && threadDescriptor != null
        && threadDownloadManager.canUseThreadDownloaderCache(threadDescriptor)
      ) {
        localInputStream = threadDownloadManager.findDownloadedFile(imageUrl, threadDescriptor)
          ?.let { file -> fileManager.getInputStream(file) }
      }

      if (localInputStream == null) {
        localInputStream = downloadAndGetResponseBody(imageUrl).source().inputStream()
      }

      val outputFileStream = fileManager.getOutputStream(outputFile)
        ?: throw ResultFileAccessError(outputFile.getFullPath())

      runInterruptible {
        localInputStream!!.use { inputStream ->
          outputFileStream.use { outputStream ->
            inputStream.copyTo(outputStream)
          }
        }
      }
    } finally {
      localInputStream?.closeQuietly()
      localInputStream = null
    }
  }

  private suspend fun downloadAndGetResponseBody(imageUrl: HttpUrl): ResponseBody {
    val requestBuilder = Request.Builder()
      .url(imageUrl)

    siteResolver.findSiteForUrl(imageUrl.toString())?.let { site ->
      site.requestModifier().modifyMediaDownloadRequest(site, requestBuilder)
    }

    val response = downloaderOkHttpClient.get().okHttpClient().suspendCall(requestBuilder.build())

    if (!response.isSuccessful) {
      if (response.code == 404) {
        throw NotFoundException()
      }

      throw BadStatusResponseException(response.code)
    }

    return response.body
      ?: throw EmptyBodyResponseException()
  }

  private suspend fun getDownloadContext(
    imageDownloadInputData: ImageSaverV2Service.ImageDownloadInputData
  ): DownloadContext? {
    return mutex.withLock { activeDownloads.get(imageDownloadInputData.uniqueId) }
  }

  fun enqueueDeleteNotification(uniqueId: String, timeoutMs: Long) {
    cancelDeleteNotification(uniqueId)

    val job = appScope.launch {
      delay(timeoutMs)
      cancelNotificationJobMap.remove(uniqueId)

      if (isActive) {
        ImageSaverV2Service.cancelNotification(notificationManagerCompat, uniqueId)
      }
    }

    cancelNotificationJobMap[uniqueId] = job
  }

  fun cancelDeleteNotification(uniqueId: String) {
    cancelNotificationJobMap.remove(uniqueId)?.cancel()
  }

  class ResultFileAccessError(val resultFileUri: String) : Exception("Failed to access result file: $resultFileUri")
  class NotFoundException : Exception("Not found on server")
  class OutOfDiskSpaceException : Exception("Out of disk space")

  class DownloadContext(
    private val canceled: AtomicBoolean = AtomicBoolean(false)
  ) {

    fun isCanceled(): Boolean = canceled.get()

    fun cancel() {
      canceled.compareAndSet(false, true)
    }

  }

  sealed class DownloadImageResult {
    data class Success(
      val outputDirUri: Uri,
      val imageDownloadRequest: ImageDownloadRequest,
    ) : DownloadImageResult()

    data class Failure(
      val error: Throwable,
      val canRetry: Boolean
    ) : DownloadImageResult()

    data class Canceled(val imageDownloadRequest: ImageDownloadRequest) : DownloadImageResult()

    data class DuplicateFound(val duplicate: DuplicateImage) : DownloadImageResult()

    data class OutOfDiskSpaceError(val imageDownloadRequest: ImageDownloadRequest) : DownloadImageResult()

    data class ResultDirectoryError(
      val path: String,
      val imageDownloadRequest: ImageDownloadRequest
    ) : DownloadImageResult()
  }

  data class ImageSaverDelegateResult(
    val uniqueId: String,
    val imageSaverOptionsJson: String,
    val completed: Boolean,
    val notificationSummary: String?,
    val totalImagesCount: Int,
    val canceledRequests: Int,
    val downloadedImages: DownloadedImages,
    val duplicates: Int,
    val failedRequests: Int,
    val hasResultDirAccessErrors: Boolean,
    val hasOutOfDiskSpaceErrors: Boolean,
    val hasRequestsThatCanBeRetried: Boolean
  ) {

    fun hasAnyErrors(): Boolean {
      return duplicates > 0 || failedRequests > 0 || hasResultDirAccessErrors || hasOutOfDiskSpaceErrors
    }

    fun hasOnlyCompletedRequests(): Boolean {
      // We allow notification auto-hide after the user manually cancels the download
      return duplicates == 0 && failedRequests == 0 && downloadedImages.count() > 0
    }

    fun processedCount(): Int {
      return canceledRequests + downloadedImages.count() + duplicates + failedRequests
    }
  }

  sealed class DownloadedImages {
    abstract val outputDirUri: Uri?
    abstract fun count(): Int

    fun isNotEmpty(): Boolean = count() > 0

    object Empty : DownloadedImages() {
      override val outputDirUri: Uri?
        get() = null

      override fun count(): Int = 0
    }

    data class Multiple(
      override val outputDirUri: Uri?,
      val imageDownloadRequestsCount: Int
    ) : DownloadedImages() {
      override fun count(): Int {
        return imageDownloadRequestsCount
      }
    }
  }

  data class DuplicateImage(
    val imageDownloadRequest: ImageDownloadRequest,
    val imageOnDiskUri: Uri
  )

  enum class ServiceStopCommand {
    Enqueue,
    Cancel
  }

  companion object {
    private const val TAG = "ImageSaverV2ServiceDelegate"
    private const val MAX_VISIBLE_NOTIFICATIONS_PROD = 12
    private const val MAX_VISIBLE_NOTIFICATIONS_TEST = 3

    const val MAX_IO_ERROR_RETRIES_COUNT = 3
  }
}