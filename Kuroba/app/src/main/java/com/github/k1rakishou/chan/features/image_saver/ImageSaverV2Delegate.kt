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
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.persist_state.ImageNameOptions
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ImageSaverV2Delegate(
  private val verboseLogs: Boolean,
  private val fileCacheV2: FileCacheV2,
  private val fileManager: FileManager
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val activeDownloads = hashMapOf<String, DownloadContext>()

  suspend fun listenForNotificationUpdates(uniqueId: String): SharedFlow<ImageSaverDelegateResult> {
    return mutex.withLock { activeDownloads[uniqueId]!!.getSharedFlow() }
  }

  suspend fun cancelDownload(uniqueId: String) {
    mutex.withLock { activeDownloads[uniqueId]?.cancel() }
  }

  suspend fun createDownloadContext(uniqueId: String) {
    BackgroundUtils.ensureBackgroundThread()

    mutex.withLock {
      if (!activeDownloads.containsKey(uniqueId)) {
        val sharedFlow = MutableSharedFlow<ImageSaverDelegateResult>(
          replay = 1,
          extraBufferCapacity = 1,
          onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        activeDownloads[uniqueId] = DownloadContext(sharedFlow)
      }
    }
  }

  suspend fun downloadImages(
    imageSaverInputData: ImageSaverV2ForegroundWorker.ImageDownloadInputData
  ) {
    BackgroundUtils.ensureBackgroundThread()

    emitNotificationUpdate(
      uniqueId = imageSaverInputData.uniqueId,
      completed = false,
      totalImagesCount = imageSaverInputData.imagesCount(),
      canceled = emptyList(),
      downloadedImages = DownloadedImages.Empty,
      duplicates = emptyList(),
      failedToDownloadImages = emptyList()
    )

    when (imageSaverInputData) {
      is ImageSaverV2ForegroundWorker.SingleImageDownloadInputData -> {
        var outputDirUri: Uri? = null
        val downloadedImages = mutableListOf<ChanPostImage>()
        val failedToDownloadImages = mutableListOf<ChanPostImage>()
        val duplicates = mutableListOf<DuplicateImage>()
        val canceled = mutableListOf<ChanPostImage>()
        val postImage = imageSaverInputData.postImage

        // TODO(KurobaEx v0.6.0): check duplicates, check whether all the directories exist, etc

        val downloadImageResult = downloadImage(
          imageSaverInputData,
          postImage
        )

        when (downloadImageResult) {
          is DownloadImageResult.Canceled -> {
            canceled += downloadImageResult.postImage
          }
          is DownloadImageResult.DuplicateFound -> {
            duplicates += downloadImageResult.duplicate
          }
          is DownloadImageResult.Failure -> {
            failedToDownloadImages += imageSaverInputData.postImage
          }
          is DownloadImageResult.Success -> {
            outputDirUri = downloadImageResult.outputDirUri

            downloadedImages += downloadImageResult.postImage
          }
        }

        val image = downloadedImages.firstOrNull()
        val downloadedImagesResult = if (image == null || outputDirUri == null) {
          DownloadedImages.Empty
        } else {
          DownloadedImages.Single(outputDirUri, image)
        }

        emitNotificationUpdate(
          uniqueId = imageSaverInputData.uniqueId,
          completed = true,
          totalImagesCount = imageSaverInputData.imagesCount(),
          canceled = canceled,
          downloadedImages = downloadedImagesResult,
          duplicates = duplicates,
          failedToDownloadImages = failedToDownloadImages
        )
      }
      is ImageSaverV2ForegroundWorker.BatchImageDownloadInputData -> {
        // TODO(KurobaEx v0.6.0):

        TODO()
      }
    }

    mutex.withLock { activeDownloads.remove(imageSaverInputData.uniqueId) }
  }

  private suspend fun emitNotificationUpdate(
    uniqueId: String,
    completed: Boolean,
    totalImagesCount: Int,
    canceled: Collection<ChanPostImage>,
    downloadedImages: DownloadedImages,
    duplicates: List<DuplicateImage>,
    failedToDownloadImages: Collection<ChanPostImage>
  ) {
    BackgroundUtils.ensureBackgroundThread()

    mutex.withLock {
      activeDownloads[uniqueId]?.let { notificationUpdates ->
        val imageSaverDelegateResult = ImageSaverDelegateResult(
          uniqueId = uniqueId,
          completed = completed,
          totalImagesCount = totalImagesCount,
          canceled = canceled,
          downloadedImages = downloadedImages,
          duplicates = duplicates,
          failedToDownloadImages = failedToDownloadImages
        )

        notificationUpdates.emit(imageSaverDelegateResult)
      }
    }
  }

  private fun formatFileName(
    imageSaverV2Options: ImageSaverV2Options,
    postImage: ChanPostImage,
    imageSaverInputData: ImageSaverV2ForegroundWorker.SingleImageDownloadInputData
  ): String {
    var fileName = when (ImageNameOptions.fromRawValue(imageSaverV2Options.imageNameOptions)) {
      ImageNameOptions.UseServerFileName -> postImage.serverFilename
      ImageNameOptions.UseOriginalFileName -> postImage.filename ?: postImage.serverFilename
    }

    if (imageSaverInputData.newFileName.isNotNullNorBlank()) {
      fileName = imageSaverInputData.newFileName
    }

    val extension = postImage.extension ?: "jpg"

    return "$fileName.$extension"
  }

  private fun getFullFileUriOrThrow(
    imageSaverV2Options: ImageSaverV2Options,
    postDescriptor: PostDescriptor,
    fileName: String
  ): ResultFile {
    val rootDirectoryUri = Uri.parse(checkNotNull(imageSaverV2Options.rootDirectoryUri))

    val rootDirectory = fileManager.fromUri(rootDirectoryUri)
      ?: throw IOException("$rootDirectoryUri does not exist on the disk")

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
    imageSaverInputData: ImageSaverV2ForegroundWorker.SingleImageDownloadInputData,
    postImage: ChanPostImage
  ): DownloadImageResult {
    BackgroundUtils.ensureBackgroundThread()

    // TODO(KurobaEx): remove me!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
//    delay(3_000)

    return ModularResult.Try {
      val canceled = getDownloadContext(imageSaverInputData)?.isCanceled() ?: true
      if (canceled) {
        return@Try DownloadImageResult.Canceled(postImage)
      }

      val imageSaverV2Options = imageSaverInputData.imageSaverV2Options

      val fileName = formatFileName(
        imageSaverV2Options,
        postImage,
        imageSaverInputData
      )

      val postDescriptor = postImage.ownerPostDescriptor
      val outputFileResult = getFullFileUriOrThrow(imageSaverV2Options, postDescriptor, fileName)

      if (outputFileResult is ResultFile.DuplicateFound) {
        return DownloadImageResult.DuplicateFound(DuplicateImage(postImage, outputFileResult.fileUri))
      }

      val outputFile = (outputFileResult as ResultFile.File).file
      val outputDirUri = (outputFileResult as ResultFile.File).outputDirUri

      if (verboseLogs) {
        Logger.d(TAG, "downloadImage() start url=${postImage.imageUrl}, output=${outputFile.getFullPath()}")
      }

      val actualOutputFile = fileManager.create(outputFile)
        ?: throw FailedToCreateOutputFile(outputFile.getFullPath())

      val sourceFile = suspendCancellableCoroutine<RawFile> { continuation ->
        fileCacheV2.enqueueNormalDownloadFileRequest(postImage, true, object : FileCacheListener() {
          override fun onSuccess(file: RawFile) {
            super.onSuccess(file)

            continuation.resume(file)
          }

          override fun onNotFound() {
            super.onNotFound()

            onFail(NotFoundException())
          }

          override fun onFail(exception: Exception) {
            super.onFail(exception)

            continuation.resumeWithException(exception)
          }
        })
      }

      if (!fileManager.copyFileContents(sourceFile, actualOutputFile)) {
        throw FailedToCopyFileContents(sourceFile.getFullPath(), actualOutputFile.getFullPath())
      }

      if (verboseLogs) {
        Logger.d(TAG, "downloadImage() success url=${postImage.imageUrl}, output=${outputFile.getFullPath()}")
      }

      return@Try DownloadImageResult.Success(outputDirUri, postImage)
    }.mapErrorToValue { error -> DownloadImageResult.Failure(error) }
  }

  private suspend fun getDownloadContext(
    imageSaverInputData: ImageSaverV2ForegroundWorker.SingleImageDownloadInputData
  ): DownloadContext? {
    return mutex.withLock { activeDownloads.get(imageSaverInputData.uniqueId) }
  }

  class NotFoundException : Exception("Not found on server")
  class FailedToOpenResultDir(path: String) : Exception("Failed to open result directory: '$path'")
  class FailedToCreateOutputFile(path: String) : Exception("Failed to create file \"$path\"")
  class FailedToCopyFileContents(sourceFilePath: String, destinationFilePath: String)
    : Exception("Failed to copy file \"$sourceFilePath\" to \"$destinationFilePath\"")

  class DownloadContext(
    private val notificationUpdatesFlow: MutableSharedFlow<ImageSaverDelegateResult>,
    private val canceled: AtomicBoolean = AtomicBoolean(false)
  ) {

    fun isCanceled(): Boolean = canceled.get()

    fun cancel() {
      canceled.compareAndSet(false, true)
    }

    fun getSharedFlow(): SharedFlow<ImageSaverDelegateResult> {
      return notificationUpdatesFlow.asSharedFlow()
    }

    fun emit(imageSaverDelegateResult: ImageSaverDelegateResult) {
      notificationUpdatesFlow.tryEmit(imageSaverDelegateResult)
    }

  }

  sealed class DownloadImageResult {
    data class Success(
      val outputDirUri: Uri,
      val postImage: ChanPostImage,
    ) : DownloadImageResult()

    data class Failure(val error: Throwable) : DownloadImageResult()

    data class Canceled(val postImage: ChanPostImage) : DownloadImageResult()

    data class DuplicateFound(val duplicate: DuplicateImage) : DownloadImageResult()
  }

  data class ImageSaverDelegateResult(
    val uniqueId: String,
    val completed: Boolean,
    val totalImagesCount: Int,
    val canceled: Collection<ChanPostImage>,
    val downloadedImages: DownloadedImages,
    val duplicates: List<DuplicateImage>,
    val failedToDownloadImages: Collection<ChanPostImage>
  ) {

    fun hasDuplicatesOrFailures(): Boolean {
      return duplicates.isNotEmpty() || failedToDownloadImages.isNotEmpty()
    }

    fun hasOnlyDownloadedImages(): Boolean {
      // We allow notification auto-hide after the user manually cancels the download
      return duplicates.isEmpty() && failedToDownloadImages.isEmpty() && downloadedImages.isNotEmpty()
    }

    fun processedCount(): Int {
      return canceled.size + downloadedImages.count() + duplicates.size + failedToDownloadImages.size
    }
  }

  sealed class DownloadedImages {
    abstract fun count(): Int

    fun isNotEmpty(): Boolean = count() > 0

    object Empty : DownloadedImages() {
      override fun count(): Int = 0
    }

    data class Single(
      val outputDirUri: Uri?,
      val image: ChanPostImage?
    ) : DownloadedImages() {
      override fun count(): Int {
        if (image == null) {
          return 0
        }

        return 1
      }
    }

    data class Multiple(
      val outputDir: Uri,
      val images: Collection<ChanPostImage>
    ) : DownloadedImages() {
      override fun count(): Int {
        return images.size
      }
    }
  }

  data class DuplicateImage(
    val serverImage: ChanPostImage,
    val imageOnDisk: Uri
  )

  companion object {
    private const val TAG = "ImageSaverV2Delegate"
  }
}