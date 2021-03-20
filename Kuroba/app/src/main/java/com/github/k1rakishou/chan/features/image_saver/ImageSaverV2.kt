package com.github.k1rakishou.chan.features.image_saver

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openIntent
import com.github.k1rakishou.chan.utils.HashingUtil
import com.github.k1rakishou.common.AndroidUtils.getAppContext
import com.github.k1rakishou.common.AndroidUtils.getAppFileProvider
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.doIoTaskWithAttempts
import com.github.k1rakishou.common.isOutOfDiskSpaceError
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.data.download.ImageDownloadRequest
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import com.github.k1rakishou.persist_state.PersistableChanState
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import java.io.File
import java.io.IOException


class ImageSaverV2(
  private val verboseLogs: Boolean,
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val gson: Gson,
  private val fileManager: FileManager,
  private val imageDownloadRequestRepository: ImageDownloadRequestRepository,
  private val imageSaverV2ServiceDelegate: ImageSaverV2ServiceDelegate
) {
  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(
    appScope,
    Dispatchers.Default
  )

  fun restartUncompleted(
    uniqueId: String,
    overrideImageSaverV2Options: ImageSaverV2Options? = null
  ) {
    Logger.d(TAG, "restartUncompleted('$uniqueId', $overrideImageSaverV2Options)")

    try {
      val imageSaverV2Options = overrideImageSaverV2Options
        ?: PersistableChanState.imageSaverV2PersistedOptions.get().copy()

      startImageSaverService(
        uniqueId = uniqueId,
        imageSaverV2Options = imageSaverV2Options,
        downloadType = ImageSaverV2Service.RESTART_UNCOMPLETED_DOWNLOAD_TYPE
      )
    } catch (error: Throwable) {
      Logger.e(TAG, "restartUncompleted($uniqueId, $overrideImageSaverV2Options) error", error)
    }
  }

  fun deleteDownload(uniqueId: String) {
    Logger.d(TAG, "deleteDownload('$uniqueId')")

    rendezvousCoroutineExecutor.post {
      imageSaverV2ServiceDelegate.deleteDownload(uniqueId)
    }
  }

  fun save(imageSaverV2Options: ImageSaverV2Options, postImage: ChanPostImage, newFileName: String?) {
    checkInputs(listOf(postImage))
    Logger.d(TAG, "save('$imageSaverV2Options', imageUrl='${postImage.imageUrl}', newFileName='$newFileName')")

    rendezvousCoroutineExecutor.post {
      val uniqueId = calculateUniqueId(listOf(postImage))
      val duplicatesResolution =
        ImageSaverV2Options.DuplicatesResolution.fromRawValue(imageSaverV2Options.duplicatesResolution)

      val imageDownloadRequest = ImageDownloadRequest(
        uniqueId = uniqueId,
        imageFullUrl = postImage.imageUrl!!,
        newFileName = newFileName,
        status = ImageDownloadRequest.Status.Queued,
        duplicateFileUri = null,
        duplicatesResolution = duplicatesResolution,
        createdOn = DateTime.now()
      )

      val actualImageDownloadRequest = imageDownloadRequestRepository.create(imageDownloadRequest)
        .safeUnwrap { error ->
          Logger.e(TAG, "Failed to create image download request", error)
          return@post
        }

      if (actualImageDownloadRequest == null) {
        // This request is already active
        Logger.d(TAG, "save('$imageSaverV2Options', imageUrl='${postImage.imageUrl}', " +
          "newFileName='$newFileName') request is already active")
        return@post
      }

      startImageSaverService(
        uniqueId = uniqueId,
        imageSaverV2Options = imageSaverV2Options,
        downloadType = ImageSaverV2Service.SINGLE_IMAGE_DOWNLOAD_TYPE
      )
    }
  }

  fun saveMany(imageSaverV2Options: ImageSaverV2Options, postImages: Collection<ChanPostImage>) {
    checkInputs(postImages)
    Logger.d(TAG, "saveMany('$imageSaverV2Options', postImagesCount=${postImages.size})")

    rendezvousCoroutineExecutor.post {
      val uniqueId = calculateUniqueId(postImages)
      val duplicatesResolution =
        ImageSaverV2Options.DuplicatesResolution.fromRawValue(imageSaverV2Options.duplicatesResolution)

      val imageDownloadRequests = postImages.mapNotNull { postImage ->
        val imageUrl = postImage.imageUrl
          ?: return@mapNotNull null

        return@mapNotNull ImageDownloadRequest(
          uniqueId = uniqueId,
          imageFullUrl = imageUrl,
          newFileName = null,
          status = ImageDownloadRequest.Status.Queued,
          duplicateFileUri = null,
          duplicatesResolution = duplicatesResolution,
          createdOn = DateTime.now()
        )
      }

      val actualImageDownloadRequests = imageDownloadRequestRepository.createMany(
        imageDownloadRequests
      )
        .safeUnwrap { error ->
          Logger.e(TAG, "Failed to create image download request", error)
          return@post
        }

      if (actualImageDownloadRequests.isEmpty()) {
        // This request is already active
        Logger.d(TAG, "saveMany('$imageSaverV2Options', postImagesCount=${postImages.size})")
        return@post
      }

      startImageSaverService(
        uniqueId = uniqueId,
        imageSaverV2Options = imageSaverV2Options,
        downloadType = ImageSaverV2Service.BATCH_IMAGE_DOWNLOAD_TYPE
      )
    }
  }

  fun share(postImage: ChanPostImage, onShareResult: (ModularResult<Unit>) -> Unit) {
    checkInputs(listOf(postImage))
    Logger.d(TAG, "share('${postImage.imageUrl}')")

    rendezvousCoroutineExecutor.post {
      val result = ModularResult.Try { shareInternal(postImage) }
      withContext(Dispatchers.Main) { onShareResult(result) }
    }
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  private suspend fun shareInternal(postImage: ChanPostImage) {
    Logger.d(TAG, "shareInternal('${postImage.imageUrl}')")

    val shareFilesDir = File(getAppContext().cacheDir, SHARE_FILES_DIR_NAME)
    if (!shareFilesDir.exists()) {
      if (!shareFilesDir.mkdirs()) {
        Logger.e(TAG, "shareInternal() failed to create share files dir, path=" + shareFilesDir.absolutePath)
        return
      }
    }

    shareFilesDir.listFiles()
      ?.forEach { prevShareFile ->
        val success = prevShareFile.delete()

        Logger.d(TAG, "shareInternal() deleting previous share " +
          "file: '${prevShareFile.absolutePath}', success: $success")
      }

    val extension = if (postImage.extension == null) {
      ""
    } else {
      ".${postImage.extension}"
    }

    val fileName = postImage.serverFilename + extension
    val outputFile = File(shareFilesDir, fileName)

    if (outputFile.exists()) {
      if (!outputFile.delete()) {
        Logger.e(TAG, "shareInternal() failed to delete ${outputFile.absolutePath}")
        return
      }
    }

    if (!outputFile.createNewFile()) {
      Logger.e(TAG, "shareInternal() failed to create ${outputFile.absolutePath}")
      return
    }

    val outputFileRaw = fileManager.fromRawFile(outputFile)

    try {
      doIoTaskWithAttempts(3) {
        try {
          imageSaverV2ServiceDelegate.downloadFileIntoFile(postImage.imageUrl!!, outputFileRaw)
        } catch (error: Throwable) {
          if (error is IOException && error.isOutOfDiskSpaceError()) {
            throw ImageSaverV2ServiceDelegate.OutOfDiskSpaceException()
          }

          throw error
        }
      }
    } catch (error: Throwable) {
      Logger.e(TAG, "shareInternal() error while downloading file ${postImage.imageUrl}", error)
      fileManager.delete(outputFileRaw)
      throw error
    }

    Logger.d(TAG, "shareInternal('${postImage.imageUrl}') file downloaded")

    withContext(Dispatchers.Main) {
      val uri = FileProvider.getUriForFile(
        getAppContext(),
        getAppFileProvider(),
        File(outputFileRaw.getFullPath())
      )

      val intent = Intent(Intent.ACTION_SEND)
      intent.type = "image/*"
      intent.putExtra(Intent.EXTRA_STREAM, uri)
      openIntent(intent)

      Logger.d(TAG, "shareInternal() success url=${postImage.imageUrl}, file=${outputFile.absolutePath}")
    }
  }

  private fun startImageSaverService(
    uniqueId: String,
    imageSaverV2Options: ImageSaverV2Options,
    downloadType: Int,
  ) {
    val imageSaverV2OptionsJson = gson.toJson(imageSaverV2Options)
    ImageSaverV2Service.startService(appContext, uniqueId, downloadType, imageSaverV2OptionsJson)

    Logger.d(TAG, "startBackgroundBookmarkWatchingWorkIfNeeded() " +
      "uniqueId='$uniqueId', imageSaverV2Options=$imageSaverV2Options, downloadType=$downloadType")
  }

  private fun calculateUniqueId(postImages: Collection<ChanPostImage>): String {
    check(postImages.isNotEmpty()) { "postImages must not be empty" }

    val urls = postImages.map { chanPostImage -> chanPostImage.imageUrl!!.toString() }
    val md5Hash = HashingUtil.stringsHash(urls)

    return "${TAG}_$md5Hash"
  }

  private fun checkInputs(postImages: Collection<ChanPostImage>) {
    postImages.forEach { postImage ->
      requireNotNull(postImage.imageUrl) { "postImage.imageUrl is null" }
    }
  }

  companion object {
    private const val TAG = "ImageSaverV2"

    private const val SHARE_FILES_DIR_NAME = "share_files"
  }
}