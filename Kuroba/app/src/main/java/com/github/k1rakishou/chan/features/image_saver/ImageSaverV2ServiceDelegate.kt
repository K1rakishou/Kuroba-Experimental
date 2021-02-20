package com.github.k1rakishou.chan.features.image_saver

import android.net.Uri
import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.cache.FileCacheListener
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.DirectorySegment
import com.github.k1rakishou.fsaf.file.FileSegment
import com.github.k1rakishou.fsaf.file.RawFile
import com.github.k1rakishou.fsaf.file.Segment
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.download.ImageDownloadRequest
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.repository.ChanPostImageRepository
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ObsoleteCoroutinesApi::class)
class ImageSaverV2ServiceDelegate(
  private val verboseLogs: Boolean,
  private val appScope: CoroutineScope,
  private val fileCacheV2: FileCacheV2,
  private val fileManager: FileManager,
  private val chanPostImageRepository: ChanPostImageRepository,
  private val imageDownloadRequestRepository: ImageDownloadRequestRepository
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val activeDownloads = hashMapOf<String, DownloadContext>()

  private val notificationUpdatesFlow = MutableSharedFlow<ImageSaverDelegateResult>(
    extraBufferCapacity = 1024,
    onBufferOverflow = BufferOverflow.SUSPEND
  )

  fun listenForNotificationUpdates(): SharedFlow<ImageSaverDelegateResult> {
    return notificationUpdatesFlow.asSharedFlow()
  }

  suspend fun deleteDownload(uniqueId: String) {
    imageDownloadRequestRepository.deleteByUniqueId(uniqueId)
      .peekError { error -> Logger.e(TAG, "imageDownloadRequestRepository.deleteByUniqueId($uniqueId) error", error) }
      .ignore()

    mutex.withLock { activeDownloads.remove(uniqueId) }
  }

  suspend fun cancelDownload(uniqueId: String) {
    mutex.withLock { activeDownloads[uniqueId]?.cancel() }
  }

  suspend fun createDownloadContext(uniqueId: String): Int {
    return mutex.withLock {
      if (!activeDownloads.containsKey(uniqueId)) {
        activeDownloads[uniqueId] = DownloadContext()
      }

      return@withLock activeDownloads.size
    }
  }

  suspend fun downloadImages(imageDownloadInputData: ImageSaverV2Service.ImageDownloadInputData): Int {
    BackgroundUtils.ensureBackgroundThread()

    val outputDirUri = AtomicReference<Uri>(null)
    val hasResultDirAccessErrors = AtomicBoolean(false)
    val completedRequests = AtomicInteger(0)
    val failedRequests = AtomicInteger(0)
    val duplicates = AtomicInteger(0)
    val canceledRequests = AtomicInteger(0)

    try {
      val canceled = getDownloadContext(imageDownloadInputData)?.isCanceled() ?: true
      if (canceled) {
        // Canceled, no need to send even here because we do that in try/finally block
        return mutex.withLock { activeDownloads.size }
      }

      // Start event
      emitNotificationUpdate(
        uniqueId = imageDownloadInputData.uniqueId,
        completed = false,
        totalImagesCount = imageDownloadInputData.requestsCount(),
        canceledRequests = canceledRequests.get(),
        completedRequests = completedRequestsToDownloadedImagesResult(completedRequests, outputDirUri),
        duplicates = duplicates.get(),
        failedRequests = failedRequests.get(),
        hasResultDirAccessErrors = hasResultDirAccessErrors.get()
      )

      val imageDownloadRequests = when (imageDownloadInputData) {
        is ImageSaverV2Service.SingleImageDownloadInputData -> {
          listOf(imageDownloadInputData.imageDownloadRequest)
        }
        is ImageSaverV2Service.BatchImageDownloadInputData -> {
          imageDownloadInputData.imageDownloadRequests
        }
        else -> {
          throw IllegalArgumentException("Unknown imageDownloadInputData: " +
            imageDownloadInputData.javaClass.simpleName
          )
        }
      }

      // TODO(KurobaEx / @Speedup): concurrency!!!
      imageDownloadRequests.forEach { imageDownloadRequest ->
        if (verboseLogs) {
          Logger.d(TAG, "downloadImagesInternal() start uniqueId='${imageDownloadInputData.uniqueId}', " +
            "imageUrl='${imageDownloadRequest.imageFullUrl}'")
        }

        val downloadImageResult = downloadImage(
          hasResultDirAccessErrors,
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
          }
          is DownloadImageResult.Success -> {
            // Image successfully downloaded
            if (!outputDirUri.compareAndSet(null, downloadImageResult.outputDirUri)) {
              check(outputDirUri == downloadImageResult.outputDirUri) {
                "outputDirUris differ! Expected: $outputDirUri, actual: ${downloadImageResult.outputDirUri}"
              }
            }

            completedRequests.incrementAndGet()
          }
          is DownloadImageResult.ResultDirectoryError -> {
            // Something have happened with the output directory (it was deleted or something like that)
            hasResultDirAccessErrors.set(true)
            canceledRequests.incrementAndGet()
          }
        }

        val updatedImageDownloadRequest = ImageDownloadRequest(
          imageDownloadRequest.uniqueId,
          imageDownloadRequest.imageServerFileName,
          imageDownloadRequest.imageFullUrl,
          imageDownloadRequest.newFileName,
          downloadImageResultToStatus(downloadImageResult),
          getDuplicateUriOrNull(downloadImageResult),
          imageDownloadRequest.duplicatesResolution,
          imageDownloadRequest.createdOn
        )

        imageDownloadRequestRepository.updateMany(listOf(updatedImageDownloadRequest))
          .peekError { error -> Logger.e(TAG, "imageDownloadRequestRepository.updateMany() error", error) }
          .ignore()

        // Progress event
        emitNotificationUpdate(
          uniqueId = imageDownloadInputData.uniqueId,
          completed = false,
          totalImagesCount = imageDownloadInputData.requestsCount(),
          canceledRequests = canceledRequests.get(),
          completedRequests = completedRequestsToDownloadedImagesResult(completedRequests, outputDirUri),
          duplicates = duplicates.get(),
          failedRequests = failedRequests.get(),
          hasResultDirAccessErrors = hasResultDirAccessErrors.get()
        )

        if (verboseLogs) {
          Logger.d(TAG, "downloadImagesInternal() end uniqueId='${imageDownloadInputData.uniqueId}', " +
            "imageUrl='${imageDownloadRequest.imageFullUrl}', result=$downloadImageResult")
        }
      }
    } finally {
      // End event
      emitNotificationUpdate(
        uniqueId = imageDownloadInputData.uniqueId,
        completed = true,
        totalImagesCount = imageDownloadInputData.requestsCount(),
        canceledRequests = canceledRequests.get(),
        completedRequests = completedRequestsToDownloadedImagesResult(completedRequests, outputDirUri),
        duplicates = duplicates.get(),
        failedRequests = failedRequests.get(),
        hasResultDirAccessErrors = hasResultDirAccessErrors.get()
      )

      return mutex.withLock {
        activeDownloads.remove(imageDownloadInputData.uniqueId)
        return@withLock activeDownloads.size
      }
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
      is DownloadImageResult.Failure -> ImageDownloadRequest.Status.DownloadFailed
      is DownloadImageResult.ResultDirectoryError -> ImageDownloadRequest.Status.DownloadFailed
      is DownloadImageResult.Success -> ImageDownloadRequest.Status.Downloaded
    }
  }

  private suspend fun emitNotificationUpdate(
    uniqueId: String,
    completed: Boolean,
    totalImagesCount: Int,
    canceledRequests: Int,
    completedRequests: DownloadedImages,
    duplicates: Int,
    failedRequests: Int,
    hasResultDirAccessErrors: Boolean
  ) {
    BackgroundUtils.ensureBackgroundThread()

    val imageSaverDelegateResult = ImageSaverDelegateResult(
      uniqueId = uniqueId,
      completed = completed,
      totalImagesCount = totalImagesCount,
      canceledRequests = canceledRequests,
      downloadedImages = completedRequests,
      duplicates = duplicates,
      failedRequests = failedRequests,
      hasResultDirAccessErrors = hasResultDirAccessErrors
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
      ImageSaverV2Options.ImageNameOptions.UseOriginalFileName -> postImage.filename ?: postImage.serverFilename
    }

    if (imageDownloadRequest.newFileName.isNotNullNorBlank()) {
      fileName = imageDownloadRequest.newFileName!!
    }

    val extension = postImage.extension ?: "jpg"

    return "$fileName.$extension"
  }

  @Throws(FailedToOpenResultDir::class)
  private fun getFullFileUriOrThrow(
    imageSaverV2Options: ImageSaverV2Options,
    postDescriptor: PostDescriptor,
    fileName: String
  ): ResultFile {
    val rootDirectoryUri = Uri.parse(checkNotNull(imageSaverV2Options.rootDirectoryUri))

    val rootDirectory = fileManager.fromUri(rootDirectoryUri)
      ?: throw FailedToOpenResultDir(rootDirectoryUri.toString())

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

    if (imageSaverV2Options.subDirs.isNotNullNorBlank()) {
      val subDirs = imageSaverV2Options.subDirs!!.split('\\')

      subDirs.forEach { subDir -> segments += DirectorySegment(subDir) }
    }

    val resultDir = fileManager.create(rootDirectory, segments)
    if (resultDir == null) {
      throw FailedToOpenResultDir(rootDirectory.clone(segments).getFullPath())
    }

    segments += FileSegment(fileName)

    val resultFile = rootDirectory.clone(segments)
    if (fileManager.exists(resultFile) && fileManager.getLength(resultFile) > 0) {
      return ResultFile.DuplicateFound(Uri.parse(resultFile.getFullPath()))
    }

    return ResultFile.File(
      Uri.parse(resultDir.getFullPath()),
      Uri.parse(resultFile.getFullPath()),
      resultFile
    )
  }

  sealed class ResultFile {
    data class File(
      val outputDirUri: Uri,
      val outputFileUri: Uri,
      val file: AbstractFile
    ) : ResultFile()

    data class DuplicateFound(val fileUri: Uri) : ResultFile()
  }

  private suspend fun downloadImage(
    hasResultDirAccessErrors: AtomicBoolean,
    imageDownloadInputData: ImageSaverV2Service.ImageDownloadInputData,
    imageDownloadRequest: ImageDownloadRequest
  ): DownloadImageResult {
    BackgroundUtils.ensureBackgroundThread()

    return ModularResult.Try {
      val canceled = getDownloadContext(imageDownloadInputData)?.isCanceled() ?: true
      if (hasResultDirAccessErrors.get() || canceled) {
        return@Try DownloadImageResult.Canceled(imageDownloadRequest)
      }

      val imageSaverV2Options = imageDownloadInputData.imageSaverV2Options
      val imageFullUrl = imageDownloadRequest.imageFullUrl

      val chanPostImageResult = chanPostImageRepository.selectPostImageByUrl(imageFullUrl)
      if (chanPostImageResult is ModularResult.Error) {
        return@Try DownloadImageResult.Failure(chanPostImageResult.error, true)
      }

      val chanPostImage = (chanPostImageResult as ModularResult.Value).value
      if (chanPostImage == null) {
        val error = IOException("Image $imageFullUrl already deleted from the database")
        return@Try DownloadImageResult.Failure(error, false)
      }

      val fileName = formatFileName(
        imageSaverV2Options,
        chanPostImage,
        imageDownloadRequest
      )

      val postDescriptor = chanPostImage.ownerPostDescriptor
      val outputFileResult = try {
        getFullFileUriOrThrow(imageSaverV2Options, postDescriptor, fileName)
      } catch (error: FailedToOpenResultDir) {
        return@Try DownloadImageResult.ResultDirectoryError(error.path, imageDownloadRequest)
      }

      if (outputFileResult is ResultFile.DuplicateFound) {
        val duplicateImage = DuplicateImage(
          imageDownloadRequest,
          outputFileResult.fileUri
        )

        return DownloadImageResult.DuplicateFound(duplicateImage)
      }

      val outputFile = (outputFileResult as ResultFile.File).file
      val outputDirUri = (outputFileResult as ResultFile.File).outputDirUri

      val actualOutputFile = fileManager.create(outputFile)
      if (actualOutputFile == null) {
        return@Try FailedToOpenResultDir(outputFile.getFullPath())
      }

      val sourceFile = try {
        downloadFileInternal(chanPostImage)
      } catch (error: Throwable) {
        if (error is Canceled) {
          return@Try DownloadImageResult.Canceled(imageDownloadRequest)
        } else if (error is NotFoundException) {
          return@Try DownloadImageResult.Failure(error, false)
        }

        throw error
      }

      if (!fileManager.copyFileContents(sourceFile, actualOutputFile)) {
        return@Try DownloadImageResult.ResultDirectoryError(
          actualOutputFile.getFullPath(),
          imageDownloadRequest
        )
      }

      return@Try DownloadImageResult.Success(outputDirUri, imageDownloadRequest)
    }.mapErrorToValue { error -> DownloadImageResult.Failure(error, true) }
  }

  @Throws(Canceled::class, NotFoundException::class)
  private suspend fun downloadFileInternal(chanPostImage: ChanPostImage): RawFile {
    return suspendCancellableCoroutine { continuation ->
      fileCacheV2.enqueueNormalDownloadFileRequest(
        chanPostImage,
        true,
        object : FileCacheListener() {
          override fun onSuccess(file: RawFile) {
            super.onSuccess(file)

            continuation.resume(file)
          }

          override fun onNotFound() {
            super.onNotFound()
            onFail(NotFoundException())
          }

          override fun onStop(file: AbstractFile?) {
            super.onStop(file)
            onFail(Canceled())
          }

          override fun onCancel() {
            super.onCancel()
            onFail(Canceled())
          }

          override fun onFail(exception: Exception) {
            super.onFail(exception)

            continuation.resumeWithException(exception)
          }
        })
    }
  }

  private suspend fun getDownloadContext(
    imageDownloadInputData: ImageSaverV2Service.ImageDownloadInputData
  ): DownloadContext? {
    return mutex.withLock { activeDownloads.get(imageDownloadInputData.uniqueId) }
  }

  class Canceled : Exception("Canceled")
  class NotFoundException : Exception("Not found on server")
  class FailedToOpenResultDir(val path: String) : Exception("Failed to open result directory: '$path'")

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

    data class ResultDirectoryError(
      val path: String,
      val imageDownloadRequest: ImageDownloadRequest
    ) : DownloadImageResult()
  }

  data class ImageSaverDelegateResult(
    val uniqueId: String,
    val completed: Boolean,
    val totalImagesCount: Int,
    val canceledRequests: Int,
    val downloadedImages: DownloadedImages,
    val duplicates: Int,
    val failedRequests: Int,
    val hasResultDirAccessErrors: Boolean
  ) {

    fun hasDuplicatesOrFailures(): Boolean {
      return duplicates > 0 || failedRequests > 0
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
      val imageDownloadRequest: Int
    ) : DownloadedImages() {
      override fun count(): Int {
        return imageDownloadRequest
      }
    }
  }

  data class DuplicateImage(
    val imageDownloadRequest: ImageDownloadRequest,
    val imageOnDiskUri: Uri
  )

  companion object {
    private const val TAG = "ImageSaverV2ServiceDelegate"
  }
}