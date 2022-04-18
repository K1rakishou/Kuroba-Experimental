package com.github.k1rakishou.chan.features.image_saver

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openIntent
import com.github.k1rakishou.chan.utils.HashingUtil
import com.github.k1rakishou.common.AndroidUtils.getAppContext
import com.github.k1rakishou.common.AndroidUtils.getAppFileProvider
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.doIoTaskWithAttempts
import com.github.k1rakishou.common.isOutOfDiskSpaceError
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.download.ImageDownloadRequest
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import com.github.k1rakishou.persist_state.PersistableChanState
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
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

  fun restartUnfinished(
    uniqueId: String,
    overrideImageSaverV2Options: ImageSaverV2Options? = null
  ) {
    Logger.d(TAG, "restartUnfinished('$uniqueId', $overrideImageSaverV2Options)")

    try {
      val imageSaverV2Options = overrideImageSaverV2Options
        ?: PersistableChanState.imageSaverV2PersistedOptions.get().copy()

      startImageSaverService(
        uniqueId = uniqueId,
        imageSaverV2Options = imageSaverV2Options,
        downloadType = ImageSaverV2Service.RESTART_UNCOMPLETED_DOWNLOAD_TYPE
      )
    } catch (error: Throwable) {
      Logger.e(TAG, "restartUnfinished($uniqueId, $overrideImageSaverV2Options) error", error)
    }
  }

  fun deleteDownload(uniqueId: String) {
    Logger.d(TAG, "deleteDownload('$uniqueId')")

    rendezvousCoroutineExecutor.post {
      imageSaverV2ServiceDelegate.deleteDownload(uniqueId)
    }
  }

  fun save(imageSaverV2Options: ImageSaverV2Options, simpleSaveableMediaInfo: SimpleSaveableMediaInfo, newFileName: String?) {
    Logger.d(TAG, "save('$imageSaverV2Options', mediaUrl='${simpleSaveableMediaInfo.mediaUrl}', newFileName='$newFileName')")

    rendezvousCoroutineExecutor.post {
      val uniqueId = calculateUniqueId(listOf(simpleSaveableMediaInfo))
      val duplicatesResolution =
        ImageSaverV2Options.DuplicatesResolution.fromRawValue(imageSaverV2Options.duplicatesResolution)

      val imageDownloadRequest = ImageDownloadRequest(
        uniqueId = uniqueId,
        imageFullUrl = simpleSaveableMediaInfo.mediaUrl,
        postDescriptorString = simpleSaveableMediaInfo.ownerPostDescriptor.serializeToString(),
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
        Logger.d(TAG, "save('$imageSaverV2Options', mediaUrl='${simpleSaveableMediaInfo.mediaUrl}', " +
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

  fun saveMany(imageSaverV2Options: ImageSaverV2Options, simpleSaveableMediaInfoList: Collection<SimpleSaveableMediaInfo>) {
    Logger.d(TAG, "saveMany('$imageSaverV2Options', simpleSaveableMediaInfoListCount=${simpleSaveableMediaInfoList.size})")

    rendezvousCoroutineExecutor.post {
      val uniqueId = calculateUniqueId(simpleSaveableMediaInfoList)
      val duplicatesResolution =
        ImageSaverV2Options.DuplicatesResolution.fromRawValue(imageSaverV2Options.duplicatesResolution)

      val imageDownloadRequests = simpleSaveableMediaInfoList.mapNotNull { postImage ->
        return@mapNotNull ImageDownloadRequest(
          uniqueId = uniqueId,
          imageFullUrl = postImage.mediaUrl,
          postDescriptorString = postImage.ownerPostDescriptor.serializeToString(),
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
        Logger.d(TAG, "saveMany('$imageSaverV2Options', simpleSaveableMediaInfoListCount=${simpleSaveableMediaInfoList.size})")
        return@post
      }

      startImageSaverService(
        uniqueId = uniqueId,
        imageSaverV2Options = imageSaverV2Options,
        downloadType = ImageSaverV2Service.BATCH_IMAGE_DOWNLOAD_TYPE
      )
    }
  }

  fun downloadMediaAndShare(postImage: ChanPostImage, onShareResult: (ModularResult<Unit>) -> Unit) {
    Logger.d(TAG, "share('${postImage.imageUrl}')")

    rendezvousCoroutineExecutor.post {
      val result = ModularResult.Try {
        val extension = if (postImage.extension == null) {
          ""
        } else {
          ".${postImage.extension}"
        }

        downloadMediaAndShare(
          mediaUrl = postImage.imageUrl!!,
          downloadFileName = postImage.serverFilename + extension,
          threadDescriptor = postImage.ownerPostDescriptor.threadDescriptor()
        )
      }
      withContext(Dispatchers.Main) { onShareResult(result) }
    }
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  suspend fun downloadMediaIntoUserProvidedFile(
    mediaUrl: HttpUrl,
    outputFileUri: Uri
  ): ModularResult<Unit> {
    return ModularResult.Try {
      return@Try withContext(Dispatchers.IO) {
        Logger.d(TAG, "downloadMediaIntoUserProvidedFile('${mediaUrl}', '${outputFileUri}')")

        val outputFile = fileManager.fromUri(outputFileUri)
        if (outputFile == null) {
          throw ImageSaverV2ServiceDelegate.ResultFileAccessError(outputFileUri.toString())
        }

        try {
          doIoTaskWithAttempts(ImageSaverV2ServiceDelegate.MAX_IO_ERROR_RETRIES_COUNT) {
            try {
              imageSaverV2ServiceDelegate.downloadFileIntoFile(mediaUrl, outputFile, null)
            } catch (error: Throwable) {
              if (error is IOException && error.isOutOfDiskSpaceError()) {
                throw ImageSaverV2ServiceDelegate.OutOfDiskSpaceException()
              }

              throw error
            }
          }
        } catch (error: Throwable) {
          Logger.e(TAG, "downloadMediaIntoUserProvidedFile() error while downloading file $mediaUrl}", error)
          fileManager.delete(outputFile)
          throw error
        }

        Logger.d(TAG, "downloadMediaIntoUserProvidedFile('${mediaUrl}') file downloaded")
      }
    }
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  suspend fun downloadMediaAndShare(
    mediaUrl: HttpUrl,
    downloadFileName: String,
    threadDescriptor: ChanDescriptor.ThreadDescriptor?
  ) {
    withContext(Dispatchers.IO) {
      Logger.d(TAG, "shareInternal('${mediaUrl}')")

      val shareFilesDir = File(getAppContext().cacheDir, SHARE_FILES_DIR_NAME)
      if (!shareFilesDir.exists()) {
        if (!shareFilesDir.mkdirs()) {
          Logger.e(TAG, "shareInternal() failed to create share files dir, path=" + shareFilesDir.absolutePath)
          return@withContext
        }
      }

      shareFilesDir.listFiles()
        ?.forEach { prevShareFile ->
          val success = prevShareFile.delete()

          Logger.d(TAG, "shareInternal() deleting previous share " +
            "file: '${prevShareFile.absolutePath}', success: $success")
        }

      val outputFile = File(shareFilesDir, downloadFileName)
      if (outputFile.exists()) {
        if (!outputFile.delete()) {
          Logger.e(TAG, "shareInternal() failed to delete ${outputFile.absolutePath}")
          return@withContext
        }
      }

      if (!outputFile.createNewFile()) {
        Logger.e(TAG, "shareInternal() failed to create ${outputFile.absolutePath}")
        return@withContext
      }

      val outputFileRaw = fileManager.fromRawFile(outputFile)

      try {
        doIoTaskWithAttempts(ImageSaverV2ServiceDelegate.MAX_IO_ERROR_RETRIES_COUNT) {
          try {
            imageSaverV2ServiceDelegate.downloadFileIntoFile(mediaUrl, outputFileRaw, threadDescriptor)
          } catch (error: Throwable) {
            if (error is IOException && error.isOutOfDiskSpaceError()) {
              throw ImageSaverV2ServiceDelegate.OutOfDiskSpaceException()
            }

            throw error
          }
        }
      } catch (error: Throwable) {
        Logger.e(TAG, "shareInternal() error while downloading file $mediaUrl}", error)
        fileManager.delete(outputFileRaw)
        throw error
      }

      Logger.d(TAG, "shareInternal('${mediaUrl}') file downloaded")

      sendShareIntent(outputFile, mediaUrl)
    }
  }

  suspend fun sendShareIntent(outputFile: File, mediaUrl: HttpUrl) {
    withContext(Dispatchers.Main) {
      val uri = FileProvider.getUriForFile(
        getAppContext(),
        getAppFileProvider(),
        outputFile
      )

      val intent = Intent(Intent.ACTION_SEND)
      intent.type = "image/*"
      intent.putExtra(Intent.EXTRA_STREAM, uri)
      openIntent(intent)

      Logger.d(TAG, "sendShareIntent() success mediaUrl=${mediaUrl}, file=${outputFile.absolutePath}")
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

  private fun calculateUniqueId(simpleSaveableMediaInfoList: Collection<SimpleSaveableMediaInfo>): String {
    check(simpleSaveableMediaInfoList.isNotEmpty()) { "simpleSaveableMediaInfoList must not be empty" }

    val urls = simpleSaveableMediaInfoList.map { chanPostImage -> chanPostImage.mediaUrl.toString() }
    val md5Hash = HashingUtil.stringsHash(urls)

    return "${TAG}_$md5Hash"
  }

  data class SimpleSaveableMediaInfo(
    val mediaUrl: HttpUrl,
    val ownerPostDescriptor: PostDescriptor,
    val serverFilename: String,
    val originalFileName: String?,
    val extension: String
  ) {
    companion object {

      @JvmStatic
      fun fromChanPostImage(chanPostImage: ChanPostImage): SimpleSaveableMediaInfo? {
        val mediaUrl = chanPostImage.imageUrl
          ?: return null
        val originalFileName = chanPostImage.filename
          ?: return null
        val extension = chanPostImage.extension
          ?: return null
        val serverFilename = chanPostImage.serverFilename

        return SimpleSaveableMediaInfo(
          mediaUrl = mediaUrl,
          ownerPostDescriptor = chanPostImage.ownerPostDescriptor,
          serverFilename = serverFilename,
          originalFileName = originalFileName,
          extension = extension
        )
      }

      @JvmStatic
      fun fromViewableMedia(viewableMedia: ViewableMedia): SimpleSaveableMediaInfo? {
        val mediaUrl = (viewableMedia.mediaLocation as? MediaLocation.Remote)?.url
          ?: return null
        val originalFileName = viewableMedia.viewableMediaMeta.originalMediaName
          ?: return null
        val serverMediaName = viewableMedia.viewableMediaMeta.serverMediaName
          ?: return null
        val extension = viewableMedia.viewableMediaMeta.extension
          ?: return null
        val postDescriptor = viewableMedia.viewableMediaMeta.ownerPostDescriptor
          ?: return null

        return SimpleSaveableMediaInfo(
          mediaUrl = mediaUrl,
          ownerPostDescriptor = postDescriptor,
          serverFilename = serverMediaName,
          originalFileName = originalFileName,
          extension = extension
        )
      }
    }
  }

  companion object {
    private const val TAG = "ImageSaverV2"

    private const val SHARE_FILES_DIR_NAME = "share_files"
  }
}