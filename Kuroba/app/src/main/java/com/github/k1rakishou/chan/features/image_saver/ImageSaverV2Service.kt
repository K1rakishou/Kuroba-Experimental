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

      val activeDownloadsCountBefore = imageSaverV2ServiceDelegate.createDownloadContext(
        imageSaverInputData.uniqueId
      )

      if (verboseLogs) {
        Logger.d(TAG, "onStartCommand() start, activeDownloadsCount=$activeDownloadsCountBefore")
      }

      serializedCoroutineExecutor.post {
        try {
          val activeDownloadsCountAfter = withContext(Dispatchers.Default) {
            imageSaverV2ServiceDelegate.downloadImages(imageSaverInputData)
          }

          if (verboseLogs) {
            Logger.d(TAG, "onStartCommand() end, activeDownloadsCount=$activeDownloadsCountAfter")
          }

          if (activeDownloadsCountAfter <= 0) {
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

    val imageSaverOptionsJson = extras.getString(IMAGE_SAVER_OPTIONS)
    checkNotNull(imageSaverOptionsJson) { "imageSaverV2Options is null" }

    val imageSaverV2Options = gson.fromJson(imageSaverOptionsJson, ImageSaverV2Options::class.java)
    checkNotNull(imageSaverV2Options) { "imageSaverV2Options is null" }

    val uniqueId = requireNotNull(extras.getString(UNIQUE_ID))

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
          imageSaverOptionsJson = imageSaverOptionsJson,
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

        return BatchImageDownloadInputData(
          uniqueId = uniqueId,
          imageSaverOptionsJson = imageSaverOptionsJson,
          imageSaverV2Options = imageSaverV2Options,
          imageDownloadRequests = imageDownloadRequests
        )
      }
      RESTART_UNCOMPLETED_DOWNLOAD_TYPE -> {
        val allowedStatuses = listOf(
          ImageDownloadRequest.Status.DownloadFailed,
          ImageDownloadRequest.Status.ResolvingDuplicate,
        )

        val imageDownloadRequests = imageDownloadRequestRepository.selectManyWithStatus(
          uniqueId,
          allowedStatuses
        ).safeUnwrap { error ->
          Logger.e(TAG, "imageDownloadRequestRepository.selectMany($uniqueId) error", error)
          return null
        }

        if (imageDownloadRequests.isEmpty()) {
          Logger.d(TAG, "convertInputData(Retry) imageDownloadRequests is empty")
          return null
        }

        if (imageDownloadRequests.size == 1) {
          return SingleImageDownloadInputData(
            uniqueId = uniqueId,
            imageSaverOptionsJson = imageSaverOptionsJson,
            imageSaverV2Options = imageSaverV2Options,
            imageDownloadRequest = imageDownloadRequests.first()
          )
        } else {
          return BatchImageDownloadInputData(
            uniqueId = uniqueId,
            imageSaverOptionsJson = imageSaverOptionsJson,
            imageSaverV2Options = imageSaverV2Options,
            imageDownloadRequests = imageDownloadRequests
          )
        }
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
      if (imageSaverDelegateResult.hasAnyErrors()) {
        android.R.drawable.ic_dialog_alert
      } else {
        android.R.drawable.stat_sys_download_done
      }
    } else {
      android.R.drawable.stat_sys_download
    }

    // TODO(KurobaEx v0.6.0): strings
    val title = if (imageSaverDelegateResult.completed) {
      if (imageSaverDelegateResult.hasAnyErrors()) {
        "Finished downloading ${totalToDownloadCount} images with errors"
      } else {
        "Successfully finished downloading ${totalToDownloadCount} images"
      }
    } else {
      "Downloading ${processedCount}/${totalToDownloadCount} images"
    }

    val notificationContentText = formatNotificationText(imageSaverDelegateResult)

    val style = if (imageSaverDelegateResult.completed) {
      NotificationCompat.BigTextStyle().bigText(notificationContentText)
    } else {
      // Big style doesn't really work well with progress so we only use it when the download has
      // completed
      null
    }

    val notification = NotificationCompat.Builder(
      applicationContext,
      NotificationConstants.ImageSaverNotifications.IMAGE_SAVER_NOTIFICATION_CHANNEL_ID
    )
      .setContentTitle(title)
      .setSmallIcon(smallIcon)
      .setOngoing(ongoing)
      .setAutoCancel(true)
      .setSound(null)
      .setStyle(style)
      .setProgressEx(imageSaverDelegateResult, totalToDownloadCount, processedCount)
      .setContentTextEx(imageSaverDelegateResult, notificationContentText)
      .setTimeoutAfterEx(imageSaverDelegateResult)
      .addOnNotificationCloseAction(imageSaverDelegateResult)
      .addCancelOrNavigateOrShowSettingsAction(imageSaverDelegateResult)
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
      if (imageSaverDelegateResult.hasResultDirAccessErrors) {
        appendLine("Failed to access root directory!")
        appendLine("Click \"show settings\" and then check that it exists and you have access to it!")
        return@buildString
      }

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

  private fun NotificationCompat.Builder.addResolveDuplicateImagesAction(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    if (imageSaverDelegateResult.hasResultDirAccessErrors) {
      return this
    }

    if (imageSaverDelegateResult.completed && imageSaverDelegateResult.duplicates > 0) {
      val intent = Intent(applicationContext, StartActivity::class.java).apply {
        setAction(ACTION_TYPE_RESOLVE_DUPLICATES)

        putExtra(UNIQUE_ID, imageSaverDelegateResult.uniqueId)
        putExtra(IMAGE_SAVER_OPTIONS, imageSaverDelegateResult.imageSaverOptionsJson)
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

  private fun NotificationCompat.Builder.addCancelOrNavigateOrShowSettingsAction(
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

    if (imageSaverDelegateResult.hasResultDirAccessErrors) {
      val intent = Intent(applicationContext, StartActivity::class.java).apply {
        setAction(ACTION_TYPE_SHOW_IMAGE_SAVER_SETTINGS)

        putExtra(UNIQUE_ID, imageSaverDelegateResult.uniqueId)
      }

      val navigate = PendingIntent.getActivity(
        applicationContext,
        RequestCodes.nextRequestCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT
      )

      addAction(0, "Show settings", navigate)  // TODO(KurobaEx v0.6.0): strings
      return this
    }

    if (imageSaverDelegateResult.downloadedImages.outputDirUri != null) {
      val downloadedImages = imageSaverDelegateResult.downloadedImages

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

    return this
  }

  private fun NotificationCompat.Builder.addResolveFailedDownloadsAction(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    val canShowRetryAction = imageSaverDelegateResult.completed && imageSaverDelegateResult.failedRequests > 0
    if (canShowRetryAction) {
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

  @Synchronized
  private fun setupChannels() {
    BackgroundUtils.ensureMainThread()

    if (!AndroidUtils.isAndroidO()) {
      return
    }

    with(NotificationConstants.ImageSaverNotifications.IMAGE_SAVER_NOTIFICATION_CHANNEL_ID) {
      if (notificationManagerCompat.getNotificationChannel(this) == null) {
        Logger.d(TAG, "setupChannels() creating $this channel")

        val imageSaverChannel = NotificationChannel(
          this,
          NotificationConstants.ImageSaverNotifications.IMAGE_SAVER_NOTIFICATION_NAME,
          NotificationManager.IMPORTANCE_DEFAULT
        )

        imageSaverChannel.setSound(null, null)
        imageSaverChannel.enableLights(false)
        imageSaverChannel.enableVibration(false)

        notificationManagerCompat.createNotificationChannel(imageSaverChannel)
      }
    }

  }

  interface ImageDownloadInputData {
    val uniqueId: String
    val imageSaverOptionsJson: String
    val imageSaverV2Options: ImageSaverV2Options

    fun requestsCount(): Int
  }

  data class SingleImageDownloadInputData(
    override val uniqueId: String,
    override val imageSaverOptionsJson: String,
    override val imageSaverV2Options: ImageSaverV2Options,
    val imageDownloadRequest: ImageDownloadRequest,
  ) : ImageDownloadInputData {
    override fun requestsCount(): Int = 1
  }

  data class BatchImageDownloadInputData(
    override val uniqueId: String,
    override val imageSaverOptionsJson: String,
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
    const val ACTION_TYPE_SHOW_IMAGE_SAVER_SETTINGS = "${TAG}_ACTION_SHOW_IMAGE_SAVER_SETTINGS"
    const val ACTION_TYPE_RESOLVE_DUPLICATES = "${TAG}_ACTION_RESOLVE_DUPLICATES"
    const val ACTION_TYPE_RETRY_FAILED = "${TAG}_ACTION_RETRY_FAILED"

    const val SINGLE_IMAGE_DOWNLOAD_TYPE = 0
    const val BATCH_IMAGE_DOWNLOAD_TYPE = 1
    const val RESTART_UNCOMPLETED_DOWNLOAD_TYPE = 2

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
