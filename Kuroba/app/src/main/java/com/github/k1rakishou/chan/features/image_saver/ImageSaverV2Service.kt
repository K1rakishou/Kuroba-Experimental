package com.github.k1rakishou.chan.features.image_saver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.receiver.ImageSaverBroadcastReceiver
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.NotificationConstants
import com.github.k1rakishou.chan.utils.RequestCodes
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.appendIfNotEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.download.ImageDownloadRequest
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ImageSaverV2Service : Service() {
  @Inject
  lateinit var imageDownloadRequestRepository: ImageDownloadRequestRepository
  @Inject
  lateinit var imageSaverV2ServiceDelegate: ImageSaverV2ServiceDelegate
  @Inject
  lateinit var gson: Gson

  private val notificationManagerCompat by lazy { NotificationManagerCompat.from(applicationContext) }
  private val kurobaScope = KurobaCoroutineScope()
  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(kurobaScope)
  private val verboseLogs = ChanSettings.verboseLogs.get()

  private var job: Job? = null

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }

  override fun onCreate() {
    super.onCreate()

    Logger.d(TAG, "onCreate()")
    Chan.getComponent().inject(this)

    job = kurobaScope.launch {
      imageSaverV2ServiceDelegate.listenForNotificationUpdates()
        .collect { imageSaverDelegateResult ->
          withContext(Dispatchers.Main) {
            showDownloadNotification(imageSaverDelegateResult)
          }
        }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    Logger.d(TAG, "onDestroy()")

    job?.cancel()
    job = null

    kurobaScope.cancelChildren()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent == null) {
      return START_NOT_STICKY
    }

    startForeground(
      NotificationConstants.IMAGE_SAVER_WORKER_NOTIFICATION_ID,
      createServiceNotification()
    )

    kurobaScope.launch {
      val imageSaverInputData = convertInputData(intent)
      if (imageSaverInputData == null) {
        Logger.d(TAG, "onStartCommand() convertInputData() failed")
        return@launch
      }

      val activeDownloadsCount1 = imageSaverV2ServiceDelegate.createDownloadContext(
        imageSaverInputData.uniqueId
      )

      if (verboseLogs) {
        Logger.d(TAG, "onStartCommand() start, activeDownloadsCount=$activeDownloadsCount1")
      }

      serializedCoroutineExecutor.post {
        try {
          val activeDownloadsCount2 = withContext(Dispatchers.Default) {
            imageSaverV2ServiceDelegate.downloadImages(imageSaverInputData)
          }

          if (verboseLogs) {
            Logger.d(TAG, "onStartCommand() end, activeDownloadsCount=$activeDownloadsCount2")
          }

          if (activeDownloadsCount2 <= 0) {
            Logger.d(TAG, "onStartCommand() stopping service")
            stopSelf()
          }
        } catch (error: Throwable) {
          Logger.e(TAG, "Unhandled exception", error)
        }
      }
    }

    return START_STICKY
  }

  private suspend fun convertInputData(intent: Intent): ImageDownloadInputData? {
    val extras = intent.extras
      ?: return null

    val imageSaverV2Options = extras.getString(IMAGE_SAVER_OPTIONS)
      ?.let { optionsJson -> gson.fromJson(optionsJson, ImageSaverV2Options::class.java) }
    val uniqueId = requireNotNull(extras.getString(UNIQUE_ID))

    checkNotNull(imageSaverV2Options) { "imageSaverV2Options is null" }

    when (val downloadType = extras.getInt(DOWNLOAD_TYPE_KEY, -1)) {
      SINGLE_IMAGE_DOWNLOAD_TYPE -> {
        val imageDownloadRequests = imageDownloadRequestRepository.selectMany(uniqueId)
          .safeUnwrap { error ->
            Logger.e(TAG, "imageDownloadRequestRepository.selectMany($uniqueId) error", error)
            return null
          }

        check(imageDownloadRequests.size <= 1) { "Bad imageDownloadRequests count: ${imageDownloadRequests.size}" }

        if (imageDownloadRequests.isEmpty()) {
          Logger.d(TAG, "convertInputData(Single) imageDownloadRequests is empty")
          return null
        }

        return SingleImageDownloadInputData(
          uniqueId = uniqueId,
          imageSaverV2Options = imageSaverV2Options,
          imageDownloadRequest = imageDownloadRequests.first()
        )
      }
      BATCH_IMAGE_DOWNLOAD_TYPE -> {
        val imageDownloadRequests = imageDownloadRequestRepository.selectMany(uniqueId)
          .safeUnwrap { error ->
            Logger.e(TAG, "imageDownloadRequestRepository.selectMany($uniqueId) error", error)
            return null
          }

        if (imageDownloadRequests.isEmpty()) {
          Logger.d(TAG, "convertInputData(Batch) imageDownloadRequests is empty")
          return null
        }

        return BatchImageDownloadInputData(uniqueId, imageSaverV2Options, imageDownloadRequests)
      }
      else -> throw IllegalStateException("Unknown parameter downloadType: $downloadType")
    }
  }

  private fun createServiceNotification(): Notification {
    BackgroundUtils.ensureMainThread()
    setupChannels()

    return NotificationCompat.Builder(
      applicationContext,
      NotificationConstants.ImageSaverNotifications.IMAGE_SAVER_NOTIFICATION_CHANNEL_ID
    )
      .setContentTitle("Downloading images") // TODO(KurobaEx v0.6.0): strings
      .setSmallIcon(R.drawable.ic_stat_notify)
      .setOngoing(true)
      .build()
  }

  private fun showDownloadNotification(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ) {
    BackgroundUtils.ensureMainThread()
    setupChannels()

    if (verboseLogs) {
      Logger.d(TAG, "showDownloadNotification() uniqueId='${imageSaverDelegateResult.uniqueId}'")
    }

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
      .addOnNotificationCloseAction(imageSaverDelegateResult)
      .addCancelOrNavigateAction(imageSaverDelegateResult)
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
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ): String {
    // TODO(KurobaEx v0.6.0): strings
    return buildString {
      if (imageSaverDelegateResult.downloadedImages.isNotEmpty()) {
        appendIfNotEmpty(", ")
        append("Downloaded: ${imageSaverDelegateResult.downloadedImages.count()}")
      }

      if (imageSaverDelegateResult.canceledRequests > 0) {
        appendIfNotEmpty(", ")
        append("Canceled: ${imageSaverDelegateResult.canceledRequests}")
      }

      if (imageSaverDelegateResult.failedRequests > 0) {
        appendIfNotEmpty(", ")
        append("Failed: ${imageSaverDelegateResult.failedRequests}")
      }

      if (imageSaverDelegateResult.duplicates > 0) {
        appendIfNotEmpty(", ")
        append("Duplicates: ${imageSaverDelegateResult.duplicates}")
      }

      appendIfNotEmpty(", ")
      append("Total processed: ${imageSaverDelegateResult.processedCount()}")
    }
  }

  private fun NotificationCompat.Builder.addResolveFailedDownloadsAction(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    if (imageSaverDelegateResult.completed && imageSaverDelegateResult.duplicates > 0) {
      val intent = Intent(applicationContext, StartActivity::class.java).apply {
        setAction(ACTION_TYPE_RESOLVE_DUPLICATES)

        putExtra(UNIQUE_ID, imageSaverDelegateResult.uniqueId)
      }

      val navigate = PendingIntent.getActivity(
        applicationContext,
        RequestCodes.nextRequestCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT
      )

      addAction(0, "Resolve duplicates", navigate)  // TODO(KurobaEx v0.6.0): strings
    }

    return this
  }

  private fun NotificationCompat.Builder.addCancelOrNavigateAction(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    if (!imageSaverDelegateResult.completed) {
      val intent = Intent(applicationContext, ImageSaverBroadcastReceiver::class.java).apply {
        setAction(ACTION_TYPE_CANCEL)
        putExtra(UNIQUE_ID, imageSaverDelegateResult.uniqueId)
      }

      val cancelIntent = PendingIntent.getBroadcast(
        applicationContext,
        RequestCodes.nextRequestCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT
      )

      addAction(0, "Cancel", cancelIntent)  // TODO(KurobaEx v0.6.0): strings
      return this
    }

    val downloadedImages = imageSaverDelegateResult.downloadedImages
    if (downloadedImages.outputDirUri == null) {
      return this
    }

    val intent = Intent(applicationContext, StartActivity::class.java).apply {
      setAction(ACTION_TYPE_NAVIGATE)

      putExtra(UNIQUE_ID, imageSaverDelegateResult.uniqueId)
      putExtra(OUTPUT_DIR_URI, downloadedImages.outputDirUri.toString())
    }

    val navigate = PendingIntent.getActivity(
      applicationContext,
      RequestCodes.nextRequestCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT
    )

    addAction(0, "Navigate", navigate)  // TODO(KurobaEx v0.6.0): strings
    return this
  }

  private fun NotificationCompat.Builder.addResolveDuplicateImagesAction(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    if (imageSaverDelegateResult.completed && imageSaverDelegateResult.failedRequests > 0) {
      val intent = Intent(applicationContext, ImageSaverBroadcastReceiver::class.java).apply {
        setAction(ACTION_TYPE_RETRY_FAILED)

        putExtra(UNIQUE_ID, imageSaverDelegateResult.uniqueId)
      }

      val navigate = PendingIntent.getBroadcast(
        applicationContext,
        RequestCodes.nextRequestCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT
      )

      addAction(0, "Retry", navigate)  // TODO(KurobaEx v0.6.0): strings
    }

    return this
  }

  private fun NotificationCompat.Builder.addOnNotificationCloseAction(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    if (imageSaverDelegateResult.completed) {
      val intent = Intent(applicationContext, ImageSaverBroadcastReceiver::class.java).apply {
        setAction(ACTION_TYPE_DELETE)

        putExtra(UNIQUE_ID, imageSaverDelegateResult.uniqueId)
      }

      val deleteDownloadIntent = PendingIntent.getBroadcast(
        applicationContext,
        RequestCodes.nextRequestCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT
      )

      setDeleteIntent(deleteDownloadIntent)
    }

    return this
  }

  private fun NotificationCompat.Builder.setTimeoutAfterEx(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    if (imageSaverDelegateResult.completed && imageSaverDelegateResult.hasOnlyCompletedRequests()) {
      setTimeoutAfter(NOTIFICATION_AUTO_DISMISS_TIMEOUT_MS)
    }

    return this
  }

  private fun NotificationCompat.Builder.setProgressEx(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult,
    max: Int,
    progress: Int
  ): NotificationCompat.Builder {
    if (!imageSaverDelegateResult.completed) {
      setProgress(max, progress, false)
    }

    return this
  }

  private fun NotificationCompat.Builder.setContentTextEx(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult,
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

    fun requestsCount(): Int
  }

  data class SingleImageDownloadInputData(
    override val uniqueId: String,
    override val imageSaverV2Options: ImageSaverV2Options,
    val imageDownloadRequest: ImageDownloadRequest,
  ) : ImageDownloadInputData {
    override fun requestsCount(): Int = 1
  }

  data class BatchImageDownloadInputData(
    override val uniqueId: String,
    override val imageSaverV2Options: ImageSaverV2Options,
    val imageDownloadRequests: List<ImageDownloadRequest>
  ) : ImageDownloadInputData {
    override fun requestsCount(): Int {
      check(imageDownloadRequests.isNotEmpty()) { "Bad imageDownloadRequests size" }

      return imageDownloadRequests.size
    }
  }

  companion object {
    private const val TAG = "ImageSaverV2Service"
    private const val NOTIFICATION_AUTO_DISMISS_TIMEOUT_MS = 10_000L

    const val UNIQUE_ID = "unique_id"
    const val DOWNLOAD_TYPE_KEY = "download_type"
    const val IMAGE_SAVER_OPTIONS = "image_saver_options"
    const val OUTPUT_DIR_URI = "OUTPUT_DIR_URI"

    const val ACTION_TYPE_DELETE = "${TAG}_ACTION_DELETE"
    const val ACTION_TYPE_CANCEL = "${TAG}_ACTION_CANCEL"
    const val ACTION_TYPE_NAVIGATE = "${TAG}_ACTION_NAVIGATE"
    const val ACTION_TYPE_RESOLVE_DUPLICATES = "${TAG}_ACTION_RESOLVE_DUPLICATES"
    const val ACTION_TYPE_RETRY_FAILED = "${TAG}_ACTION_RETRY_FAILED"

    const val SINGLE_IMAGE_DOWNLOAD_TYPE = 0
    const val BATCH_IMAGE_DOWNLOAD_TYPE = 1

    fun startService(context: Context, uniqueId: String, downloadType: Int, imageSaverV2OptionsJson: String) {
      val startServiceIntent = Intent(
        context,
        ImageSaverV2Service::class.java
      )

      startServiceIntent.putExtra(UNIQUE_ID, uniqueId)
      startServiceIntent.putExtra(DOWNLOAD_TYPE_KEY, downloadType)
      startServiceIntent.putExtra(IMAGE_SAVER_OPTIONS, imageSaverV2OptionsJson)

      context.startService(startServiceIntent)
    }
  }
}
