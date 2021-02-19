package com.github.k1rakishou.chan.features.image_saver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.receiver.CancelImagesDownloadBroadcastReceiver
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.NotificationConstants
import com.github.k1rakishou.chan.utils.RequestCodes
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.appendIfNotEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.repository.ChanPostImageRepository
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

class ImageSaverV2ForegroundWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {
  @Inject
  lateinit var chanPostImageRepository: ChanPostImageRepository
  @Inject
  lateinit var imageSaverV2Delegate: ImageSaverV2Delegate
  @Inject
  lateinit var gson: Gson

  private val notificationManagerCompat = NotificationManagerCompat.from(context)
  private val kurobaScope = KurobaCoroutineScope()
  private val verboseLogs = ChanSettings.verboseLogs.get()

  override suspend fun doWork(): Result {
    Chan.getComponent().inject(this)

    try {
      if (verboseLogs) {
        Logger.d(TAG, "ImageSaverV2ForegroundWorker.doWork() start")
      }

      val result = doWorkInternal()

      if (verboseLogs) {
        Logger.d(TAG, "ImageSaverV2ForegroundWorker.doWork() end")
      }

      return result
    } catch (error: Throwable) {
      Logger.e(TAG, "Unhandled exception", error)
      return Result.success()
    }
  }

  private suspend fun doWorkInternal(): Result {
    val imageSaverInputData = convertInputData(inputData)
    if (imageSaverInputData == null) {
      Logger.d(TAG, "convertInputData failed")
      return Result.success()
    }

    imageSaverV2Delegate.createDownloadContext(imageSaverInputData.uniqueId)

    val notificationProcessingDeferred = CompletableDeferred<Unit>()

    val job = kurobaScope.launch {
      imageSaverV2Delegate.listenForNotificationUpdates(imageSaverInputData.uniqueId)
        .collect { imageSaverDelegateResult ->
          withContext(Dispatchers.Main) {
            showDownloadNotification(imageSaverDelegateResult)

            if (imageSaverDelegateResult.completed) {
              notificationProcessingDeferred.complete(Unit)
            }
          }
        }
    }

    withContext(Dispatchers.Main) {
      setForeground(createForegroundInfo())
    }

    withContext(Dispatchers.IO) {
      imageSaverV2Delegate.downloadImages(imageSaverInputData)
    }

    notificationProcessingDeferred.await()
    job.cancel()

    return Result.success()
  }

  private suspend fun convertInputData(inputData: Data): ImageDownloadInputData? {
    val imageSaverV2Options = inputData.getString(IMAGE_SAVER_OPTIONS)
      ?.let { optionsJson -> gson.fromJson(optionsJson, ImageSaverV2Options::class.java) }
    val uniqueId = requireNotNull(inputData.getString(UNIQUE_ID))

    checkNotNull(imageSaverV2Options) { "imageSaverV2Options is null" }

    when (val downloadType = inputData.getInt(DOWNLOAD_TYPE_KEY, -1)) {
      SINGLE_IMAGE_DOWNLOAD_TYPE -> {
        val urls = inputData.getStringArray(URLS_KEY)
          ?.mapNotNull { url -> url.toHttpUrlOrNull() }

        checkNotNull(urls) { "urls is null" }

        val chanPostImages = chanPostImageRepository.selectPostImagesByUrls(urls)
          .safeUnwrap { error ->
            Logger.e(TAG, "chanPostImageRepository.selectPostImagesByUrls() error", error)
            return null
          }

        check(chanPostImages.size <= 1) { "Bad chanPostImages count: ${chanPostImages.size}" }

        if (chanPostImages.isEmpty()) {
          Logger.d(TAG, "convertInputData(Single) chanPostImages is empty")
          return null
        }

        val newFileName = inputData.getString(NEW_FILE_NAME)

        return SingleImageDownloadInputData(uniqueId, imageSaverV2Options, chanPostImages.first(), newFileName)
      }
      BATCH_IMAGE_DOWNLOAD_TYPE -> {
        val urls = inputData.getStringArray(URLS_KEY)
          ?.mapNotNull { url -> url.toHttpUrlOrNull() }

        checkNotNull(urls) { "urls is null" }

        val chanPostImages = chanPostImageRepository.selectPostImagesByUrls(urls)
          .safeUnwrap { error ->
            Logger.e(TAG, "chanPostImageRepository.selectPostImagesByUrls() error", error)
            return null
          }

        if (chanPostImages.isEmpty()) {
          Logger.d(TAG, "convertInputData(Batch) chanPostImages is empty")
          return null
        }

        return BatchImageDownloadInputData(uniqueId, imageSaverV2Options, chanPostImages)
      }
      else -> throw IllegalStateException("Unknown parameter downloadType: $downloadType")
    }
  }

  private fun createForegroundInfo(): ForegroundInfo {
    BackgroundUtils.ensureMainThread()
    setupChannels()

    val notification = NotificationCompat.Builder(
      applicationContext,
      NotificationConstants.ImageSaverNotifications.IMAGE_SAVER_NOTIFICATION_CHANNEL_ID
    )
      .setContentTitle("Downloading images") // TODO(KurobaEx v0.6.0): strings
      .setSmallIcon(R.drawable.ic_stat_notify)
      .setOngoing(true)
      .build()

    return ForegroundInfo(
      NotificationConstants.IMAGE_SAVER_WORKER_NOTIFICATION_ID,
      notification
    )
  }

  private fun showDownloadNotification(
    imageSaverDelegateResult: ImageSaverV2Delegate.ImageSaverDelegateResult
  ) {
    BackgroundUtils.ensureMainThread()
    setupChannels()

    val ongoing = imageSaverDelegateResult.completed.not()
    val totalToDownloadCount = imageSaverDelegateResult.totalImagesCount
    val processedCount = imageSaverDelegateResult.processedCount()

    val smallIcon = if (imageSaverDelegateResult.completed) {
      if (imageSaverDelegateResult.hasDuplicatesOrFailures()) {
        android.R.drawable.ic_dialog_alert
      } else {
        android.R.drawable.stat_sys_download_done
      }
    } else {
      android.R.drawable.stat_sys_download
    }

    // TODO(KurobaEx v0.6.0): strings
    val title = if (imageSaverDelegateResult.completed) {
      "Finished downloading ${totalToDownloadCount} images"
    } else {
      "Downloading ${processedCount}/${totalToDownloadCount} images"
    }

    val notification = NotificationCompat.Builder(
      applicationContext,
      NotificationConstants.ImageSaverNotifications.IMAGE_SAVER_NOTIFICATION_CHANNEL_ID
    )
      .setContentTitle(title)
      .setSmallIcon(smallIcon)
      .setOngoing(ongoing)
      .setAutoCancel(true)
      .setProgressEx(imageSaverDelegateResult, totalToDownloadCount, processedCount)
      .setContentTextEx(imageSaverDelegateResult, formatNotificationText(imageSaverDelegateResult))
      .setTimeoutAfterEx(imageSaverDelegateResult)
      .addCancelOrViewAction(imageSaverDelegateResult)
      .addResolveFailedDownloadsAction(imageSaverDelegateResult)
      .addResolveDuplicateImagesAction(imageSaverDelegateResult)
      .build()

    notificationManagerCompat.notify(
      imageSaverDelegateResult.uniqueId,
      imageSaverDelegateResult.uniqueId.hashCode(),
      notification
    )
  }

  private fun formatNotificationText(
    imageSaverDelegateResult: ImageSaverV2Delegate.ImageSaverDelegateResult
  ): String {
    // TODO(KurobaEx v0.6.0): strings
    return buildString {
      if (imageSaverDelegateResult.downloadedImages.isNotEmpty()) {
        appendIfNotEmpty(", ")
        append("Downloaded: ${imageSaverDelegateResult.downloadedImages.count()}")
      }

      if (imageSaverDelegateResult.canceled.isNotEmpty()) {
        appendIfNotEmpty(", ")
        append("Canceled: ${imageSaverDelegateResult.canceled.size}")
      }

      if (imageSaverDelegateResult.failedToDownloadImages.isNotEmpty()) {
        appendIfNotEmpty(", ")
        append("Failed: ${imageSaverDelegateResult.failedToDownloadImages.size}")
      }

      if (imageSaverDelegateResult.duplicates.isNotEmpty()) {
        appendIfNotEmpty(", ")
        append("Duplicates: ${imageSaverDelegateResult.duplicates.size}")
      }

      appendIfNotEmpty(", ")
      append("Total processed: ${imageSaverDelegateResult.processedCount()}")
    }
  }

  private fun NotificationCompat.Builder.addCancelOrViewAction(
    imageSaverDelegateResult: ImageSaverV2Delegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    if (!imageSaverDelegateResult.completed) {
      val intent = Intent(applicationContext, CancelImagesDownloadBroadcastReceiver::class.java).apply {
        setAction(ACTION_TYPE_CANCEL)
        putExtra(UNIQUE_ID, imageSaverDelegateResult.uniqueId)
      }

      val cancelIntent = PendingIntent.getBroadcast(
        applicationContext,
        RequestCodes.CANCEL_IMAGE_DOWNLOAD_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT
      )

      addAction(0, "Cancel", cancelIntent)  // TODO(KurobaEx v0.6.0): strings
      return this
    }

    val downloadedImages = imageSaverDelegateResult.downloadedImages
    if (downloadedImages !is ImageSaverV2Delegate.DownloadedImages.Single) {
      return this
    }

    if (downloadedImages.outputDirUri == null) {
      return this
    }

    val intent = Intent(applicationContext, StartActivity::class.java).apply {
      setAction(ACTION_TYPE_NAVIGATE)

      // TODO(KurobaEx v0.6.0): handle in StartActivity
      putExtra(UNIQUE_ID, imageSaverDelegateResult.uniqueId)
      putExtra(OUTPUT_DIR_URI, downloadedImages.outputDirUri?.toString())
    }

    val navigate = PendingIntent.getActivity(
      applicationContext,
      RequestCodes.NAVIGATE_TO_IMAGE_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT
    )

    addAction(0, "Navigate", navigate)  // TODO(KurobaEx v0.6.0): strings
    return this
  }

  private fun NotificationCompat.Builder.addResolveFailedDownloadsAction(
    imageSaverDelegateResult: ImageSaverV2Delegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    if (imageSaverDelegateResult.completed) {
      // TODO(KurobaEx v0.6.0):
    }

    return this
  }

  private fun NotificationCompat.Builder.addResolveDuplicateImagesAction(
    imageSaverDelegateResult: ImageSaverV2Delegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    if (imageSaverDelegateResult.completed) {
      // TODO(KurobaEx v0.6.0):
    }

    return this
  }

  private fun NotificationCompat.Builder.setTimeoutAfterEx(
    imageSaverDelegateResult: ImageSaverV2Delegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    if (imageSaverDelegateResult.completed && imageSaverDelegateResult.hasOnlyDownloadedImages()) {
      setTimeoutAfter(NOTIFICATION_AUTO_DISMISS_TIMEOUT_MS)
    }

    return this
  }

  private fun NotificationCompat.Builder.setProgressEx(
    imageSaverDelegateResult: ImageSaverV2Delegate.ImageSaverDelegateResult,
    max: Int,
    progress: Int
  ): NotificationCompat.Builder {
    if (!imageSaverDelegateResult.completed) {
      setProgress(max, progress, false)
    }

    return this
  }

  private fun NotificationCompat.Builder.setContentTextEx(
    imageSaverDelegateResult: ImageSaverV2Delegate.ImageSaverDelegateResult,
    text: String
  ): NotificationCompat.Builder {
    if (imageSaverDelegateResult.completed) {
      setContentText(text)
    }

    return this
  }

  private fun setupChannels() {
    BackgroundUtils.ensureMainThread()

    if (!AndroidUtils.isAndroidO()) {
      return
    }

    with(NotificationConstants.ImageSaverNotifications.IMAGE_SAVER_NOTIFICATION_CHANNEL_ID) {
      if (notificationManagerCompat.getNotificationChannel(this) == null) {
        Logger.d(TAG, "setupChannels() creating $this channel")

        // notification channel for replies summary
        val lastPageSilentChannel = NotificationChannel(
          this,
          NotificationConstants.ImageSaverNotifications.IMAGE_SAVER_NOTIFICATION_NAME,
          NotificationManager.IMPORTANCE_DEFAULT
        )

        notificationManagerCompat.createNotificationChannel(lastPageSilentChannel)
      }
    }

  }

  interface ImageDownloadInputData {
    val uniqueId: String
    val imageSaverV2Options: ImageSaverV2Options

    fun imagesCount(): Int
  }

  data class SingleImageDownloadInputData(
    override val uniqueId: String,
    override val imageSaverV2Options: ImageSaverV2Options,
    val postImage: ChanPostImage,
    val newFileName: String?
  ) : ImageDownloadInputData {
    override fun imagesCount(): Int = 1
  }

  data class BatchImageDownloadInputData(
    override val uniqueId: String,
    override val imageSaverV2Options: ImageSaverV2Options,
    val postImages: List<ChanPostImage>
  ) : ImageDownloadInputData {
    override fun imagesCount(): Int {
      check(postImages.isNotEmpty()) { "Bad postImages size" }

      return postImages.size
    }
  }

  companion object {
    private const val TAG = "ImageSaverV2ForegroundWorker"
    private const val NOTIFICATION_AUTO_DISMISS_TIMEOUT_MS = 10_000L

    const val UNIQUE_ID = "unique_id"
    const val DOWNLOAD_TYPE_KEY = "download_type"
    const val IMAGE_SAVER_OPTIONS = "image_saver_options"
    const val URLS_KEY = "URLS"
    const val NEW_FILE_NAME = "NEW_FILE_NAME"
    const val OUTPUT_DIR_URI = "OUTPUT_DIR_URI"

    const val ACTION_TYPE_CANCEL = "${TAG}_ACTION_CANCEL"
    const val ACTION_TYPE_NAVIGATE = "${TAG}_ACTION_NAVIGATE"

    const val SINGLE_IMAGE_DOWNLOAD_TYPE = 0
    const val BATCH_IMAGE_DOWNLOAD_TYPE = 1
  }
}
