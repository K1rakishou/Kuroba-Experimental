package com.github.k1rakishou.chan.features.image_saver

import android.content.Context
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.utils.HashingUtil
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.download.ImageDownloadRequest
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import org.joda.time.DateTime

class ImageSaverV2(
  private val verboseLogs: Boolean,
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val gson: Gson,
  private val imageDownloadRequestRepository: ImageDownloadRequestRepository
) {
  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(appScope)

  fun retryFailedImages(uniqueId: String) {
    // TODO(KurobaEx v0.6.0):
  }

  fun save(imageSaverV2Options: ImageSaverV2Options, postImage: ChanPostImage, newFileName: String?) {
    checkInputs(listOf(postImage))

    rendezvousCoroutineExecutor.post {
      val uniqueId = calculateUniqueId(listOf(postImage))

      val imageDownloadRequest = ImageDownloadRequest(
        uniqueId = uniqueId,
        imageServerFileName = postImage.serverFilename,
        imageFullUrl = postImage.imageUrl!!,
        newFileName = newFileName,
        status = ImageDownloadRequest.Status.Queued,
        duplicatePathUri = null,
        duplicatesResolution = ImageSaverV2Options.DuplicatesResolution.fromRawValue(imageSaverV2Options.duplicatesResolution)!!,
        createdOn = DateTime.now()
      )

      val actualImageDownloadRequest = imageDownloadRequestRepository.create(imageDownloadRequest)
        .safeUnwrap { error ->
          Logger.e(TAG, "Failed to create image download request", error)
          return@post
        }

      if (actualImageDownloadRequest == null) {
        // This request is already active
        return@post
      }

      startBackgroundBookmarkWatchingWorkIfNeeded(
        uniqueId = uniqueId,
        imageSaverV2Options = imageSaverV2Options,
        downloadType = ImageSaverV2Service.SINGLE_IMAGE_DOWNLOAD_TYPE
      )
    }
  }

  fun saveMany(imageSaverV2Options: ImageSaverV2Options, postImages: Collection<ChanPostImage>) {
    checkInputs(postImages)

    val outputFileUri = requireNotNull(imageSaverV2Options.rootDirectoryUri) {
      "rootDirectoryUri is null"
    }

    // TODO(KurobaEx):
  }

  fun share(postImage: ChanPostImage) {
    checkInputs(listOf(postImage))

    // TODO(KurobaEx):
  }

  private fun startBackgroundBookmarkWatchingWorkIfNeeded(
    uniqueId: String,
    imageSaverV2Options: ImageSaverV2Options,
    downloadType: Int,
  ) {
    val imageSaverV2OptionsJson = gson.toJson(imageSaverV2Options)
    ImageSaverV2Service.startService(appContext, uniqueId, downloadType, imageSaverV2OptionsJson)

    if (verboseLogs) {
      Logger.d(TAG, "startBackgroundBookmarkWatchingWorkIfNeeded() " +
        "uniqueId='$uniqueId', imageSaverV2Options=$imageSaverV2Options")
    }
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
  }
}