package com.github.k1rakishou.chan.features.image_saver

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.utils.HashingUtil
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope

class ImageSaverV2(
  private val verboseLogs: Boolean,
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val gson: Gson
) {
  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(appScope)

  fun save(imageSaverV2Options: ImageSaverV2Options, postImage: ChanPostImage, newFileName: String?) {
    checkInputs(listOf(postImage))

    rendezvousCoroutineExecutor.post {
      startBackgroundBookmarkWatchingWorkIfNeeded(imageSaverV2Options, listOf(postImage), newFileName)
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

  private suspend fun startBackgroundBookmarkWatchingWorkIfNeeded(
    imageSaverV2Options: ImageSaverV2Options,
    postImages: Collection<ChanPostImage>,
    newFileName: String?
  ) {
    val imageUrls = postImages.map { it.imageUrl!!.toString() }.toTypedArray()
    val imageSaverV2OptionsJson = gson.toJson(imageSaverV2Options)

    val downloadType = if (postImages.size == 1) {
      ImageSaverV2ForegroundWorker.SINGLE_IMAGE_DOWNLOAD_TYPE
    } else {
      ImageSaverV2ForegroundWorker.BATCH_IMAGE_DOWNLOAD_TYPE
    }

    val uniqueId = calculateUniqueId(postImages)

    val data = Data.Builder()
      .putString(ImageSaverV2ForegroundWorker.UNIQUE_ID, uniqueId)
      .putInt(ImageSaverV2ForegroundWorker.DOWNLOAD_TYPE_KEY, downloadType)
      .putString(ImageSaverV2ForegroundWorker.IMAGE_SAVER_OPTIONS, imageSaverV2OptionsJson)
      .putStringArray(ImageSaverV2ForegroundWorker.URLS_KEY, imageUrls)
      .putString(ImageSaverV2ForegroundWorker.NEW_FILE_NAME, newFileName)
      .build()

    val workRequest = OneTimeWorkRequestBuilder<ImageSaverV2ForegroundWorker>()
      .setInputData(data)
      .build()

    WorkManager
      .getInstance(appContext)
      .enqueueUniqueWork(uniqueId, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
      .await()

    if (verboseLogs) {
      Logger.d(TAG, "startBackgroundBookmarkWatchingWorkIfNeeded() uniqueId='$uniqueId', " +
        "postImages=${postImages.size}, newFileName=$newFileName, imageSaverV2Options=$imageSaverV2Options")
    }
  }

  private fun calculateUniqueId(postImages: Collection<ChanPostImage>): String {
    check(postImages.isNotEmpty()) { "postImages must not be empty" }

    val md5Hash = if (postImages.size == 1) {
      // In case of a single download - imageUrl is the id of the download
      HashingUtil.stringHash(postImages.first().imageUrl!!.toString())
    } else {
      // In case of batch download - the whole thread unique identifier is the id of the download
      HashingUtil.stringHash(postImages.first().ownerPostDescriptor.serializeToString())
    }

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