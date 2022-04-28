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
import com.github.k1rakishou.chan.core.base.KeyBasedSerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
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
import dagger.Lazy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

class ImageSaverV2Service : Service() {
  @Inject
  lateinit var imageDownloadRequestRepository: ImageDownloadRequestRepository
  @Inject
  lateinit var imageSaverV2ServiceDelegate: Lazy<ImageSaverV2ServiceDelegate>
  @Inject
  lateinit var gson: Gson

  private val notificationManagerCompat by lazy { NotificationManagerCompat.from(applicationContext) }
  private val kurobaScope = KurobaCoroutineScope()
  private val verboseLogs = ChanSettings.verboseLogs.get()
  private val notificationUpdateExecutor = KeyBasedSerializedCoroutineExecutor<String>(kurobaScope)

  private var stopServiceJob: Job? = null

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }

  override fun onCreate() {
    super.onCreate()

    Logger.d(TAG, "onCreate()")
    Chan.getComponent().inject(this)

    kurobaScope.launch {
      imageSaverV2ServiceDelegate.get().listenForNotificationUpdates()
        .collect { imageSaverDelegateResult ->
          notificationUpdateExecutor.post(imageSaverDelegateResult.uniqueId) {
            showDownloadNotification(imageSaverDelegateResult)
          }
        }
    }

    kurobaScope.launch {
      imageSaverV2ServiceDelegate.get().listenForStopServiceEvent()
        .collect { serviceStopCommand ->
          Logger.d(TAG, "Got serviceStopCommand: $serviceStopCommand")

          when (serviceStopCommand) {
            ImageSaverV2ServiceDelegate.ServiceStopCommand.Enqueue -> {
              stopServiceJob?.cancel()
              stopServiceJob = null

              stopServiceJob = kurobaScope.launch {
                delay(1000L)

                Logger.d(TAG, "Stopping the service")
                stopForeground(true)
                stopSelf()
              }
            }
            ImageSaverV2ServiceDelegate.ServiceStopCommand.Cancel -> {
              stopServiceJob?.cancel()
              stopServiceJob = null
            }
          }
        }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    Logger.d(TAG, "onDestroy()")

    notificationUpdateExecutor.cancelAll()
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

      val activeDownloadsCountBefore = imageSaverV2ServiceDelegate.get().createDownloadContext(
        imageSaverInputData.uniqueId
      )

      if (verboseLogs) {
        Logger.d(TAG, "onStartCommand() start, activeDownloadsCount=$activeDownloadsCountBefore")
      }

      imageSaverV2ServiceDelegate.get().downloadImages(imageSaverInputData)
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

        if (imageDownloadRequests.size != 1) {
          Logger.e(TAG, "convertInputData(Single) " +
            "imageDownloadRequests (${imageDownloadRequests.size}) != 1")
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
          Logger.e(TAG, "convertInputData(Batch) imageDownloadRequests is empty")
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
      .setContentTitle(getString(R.string.image_saver_downloading_images))
      .setSmallIcon(R.drawable.ic_stat_notify)
      .setOngoing(true)
      .build()
  }

  private suspend fun showDownloadNotification(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ) {
    BackgroundUtils.ensureMainThread()
    setupChannels()

    if (verboseLogs) {
      Logger.d(TAG, "showDownloadNotification() " +
        "uniqueId='${imageSaverDelegateResult.uniqueId}', " +
        "completed=${imageSaverDelegateResult.completed}")
    }

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

    val title = if (imageSaverDelegateResult.completed) {
      if (imageSaverDelegateResult.hasAnyErrors()) {
        getString(R.string.image_saver_finished_downloading_images_with_errors, totalToDownloadCount)
      } else {
        getString(R.string.image_saver_finished_downloading_images_success, totalToDownloadCount)
      }
    } else {
      getString(R.string.image_saver_finished_downloading_images_progress, processedCount, totalToDownloadCount)
    }

    val notificationContentText = formatNotificationText(imageSaverDelegateResult)

    val style = if (imageSaverDelegateResult.completed) {
      NotificationCompat.BigTextStyle()
        .setBigContentTitle(title)
        .setSummaryText(imageSaverDelegateResult.notificationSummary)
        .bigText(notificationContentText)
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
      .setOngoing(isNotificationOngoing(imageSaverDelegateResult))
      .setAutoCancel(false)
      .setSound(null)
      .setStyle(style)
      .setContentText(notificationContentText)
      .setProgressEx(imageSaverDelegateResult, totalToDownloadCount, processedCount)
      .setTimeoutAfterEx(imageSaverDelegateResult)
      .addOnNotificationCloseAction(imageSaverDelegateResult)
      .addCancelOrNavigateOrShowSettingsAction(imageSaverDelegateResult)
      .addResolveFailedDownloadsAction(imageSaverDelegateResult)
      .addResolveDuplicateImagesAction(imageSaverDelegateResult)
      .build()

    showNotification(notificationManagerCompat, imageSaverDelegateResult.uniqueId, notification)

    // Wait some time for the notification to actually get updated (since this process
    // is async and sometimes race conditions occur)
    delay(32L)
  }

  private fun isNotificationOngoing(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ): Boolean {
    if (!imageSaverDelegateResult.completed) {
      return true
    }

    if (imageSaverDelegateResult.hasOnlyCompletedRequests()) {
      return false
    }

    return true
  }

  private fun formatNotificationText(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ): String {
    return buildString {
      if (!imageSaverDelegateResult.completed) {
        appendLine(imageSaverDelegateResult.notificationSummary ?: "")
        return@buildString
      }

      if (imageSaverDelegateResult.hasOutOfDiskSpaceErrors) {
        appendLine(getString(R.string.image_saver_out_of_disk_space))
        return@buildString
      }

      if (imageSaverDelegateResult.hasResultDirAccessErrors) {
        appendLine(getString(R.string.image_saver_failed_to_access_root_dir))
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
    if (imageSaverDelegateResult.hasResultDirAccessErrors || imageSaverDelegateResult.hasOutOfDiskSpaceErrors) {
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
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      addAction(0, getString(R.string.image_saver_resolve_duplicates), navigate)

      addCancelIntent(imageSaverDelegateResult)
    }

    return this
  }

  private fun NotificationCompat.Builder.addCancelOrNavigateOrShowSettingsAction(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    if (!imageSaverDelegateResult.completed) {
      addCancelIntent(imageSaverDelegateResult)
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
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      addAction(0, getString(R.string.image_saver_show_images_saver_settings), navigate)
      return this
    }

    // The documentsui default Android application crashes on Android 9 and below, when attempting
    // to start it with Intent.ACTION_VIEW intent, somewhere inside the logging code trying
    // to extract rootId from the passed uri (for logging, it doesn't even use it afterwards).
    if (AndroidUtils.isAndroid10() && imageSaverDelegateResult.downloadedImages.outputDirUri != null) {
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
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      addAction(0, getString(R.string.image_saver_navigate), navigate)
      return this
    }

    return this
  }

  private fun NotificationCompat.Builder.addCancelIntent(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ) {
    val intent = Intent(applicationContext, ImageSaverBroadcastReceiver::class.java).apply {
      setAction(ACTION_TYPE_CANCEL)
      putExtra(UNIQUE_ID, imageSaverDelegateResult.uniqueId)
    }

    val cancelIntent = PendingIntent.getBroadcast(
      applicationContext,
      RequestCodes.nextRequestCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    addAction(0, getString(R.string.cancel), cancelIntent)
  }

  private fun NotificationCompat.Builder.addResolveFailedDownloadsAction(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    val canShowRetryAction = imageSaverDelegateResult.completed
      && imageSaverDelegateResult.hasRequestsThatCanBeRetried
      && imageSaverDelegateResult.failedRequests > 0

    val canShowCancelButton = imageSaverDelegateResult.completed
      && imageSaverDelegateResult.failedRequests > 0

    if (canShowRetryAction) {
      val intent = Intent(applicationContext, ImageSaverBroadcastReceiver::class.java).apply {
        setAction(ACTION_TYPE_RETRY_FAILED)

        putExtra(UNIQUE_ID, imageSaverDelegateResult.uniqueId)
      }

      val navigate = PendingIntent.getBroadcast(
        applicationContext,
        RequestCodes.nextRequestCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      addAction(0, getString(R.string.retry), navigate)
    }

    if (canShowCancelButton) {
      addCancelIntent(imageSaverDelegateResult)
    }

    return this
  }

  private fun NotificationCompat.Builder.addOnNotificationCloseAction(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    val canUseDeleteIntent = imageSaverDelegateResult.completed
      && imageSaverDelegateResult.hasOnlyCompletedRequests()
      && !imageSaverDelegateResult.hasAnyErrors()

    if (canUseDeleteIntent) {
      val intent = Intent(applicationContext, ImageSaverBroadcastReceiver::class.java).apply {
        setAction(ACTION_TYPE_DELETE)

        putExtra(UNIQUE_ID, imageSaverDelegateResult.uniqueId)
      }

      val deleteDownloadIntent = PendingIntent.getBroadcast(
        applicationContext,
        RequestCodes.nextRequestCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      setDeleteIntent(deleteDownloadIntent)
    }

    return this
  }

  private fun NotificationCompat.Builder.setTimeoutAfterEx(
    imageSaverDelegateResult: ImageSaverV2ServiceDelegate.ImageSaverDelegateResult
  ): NotificationCompat.Builder {
    val canAutoDismiss = imageSaverDelegateResult.completed
      && imageSaverDelegateResult.totalImagesCount == 1
      && imageSaverDelegateResult.hasOnlyCompletedRequests()
      && !imageSaverDelegateResult.hasAnyErrors()

    if (canAutoDismiss) {
      imageSaverV2ServiceDelegate.get().enqueueDeleteNotification(
        imageSaverDelegateResult.uniqueId,
        NOTIFICATION_AUTO_DISMISS_TIMEOUT_MS
      )
    } else {
      imageSaverV2ServiceDelegate.get().cancelDeleteNotification(
        imageSaverDelegateResult.uniqueId
      )
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
    private const val IMAGE_SAVER_NOTIFICATIONS_TAG = "ImageSaverNotification"

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

    fun showNotification(
      notificationManagerCompat: NotificationManagerCompat,
      uniqueId: String,
      notification: Notification
    ) {
      notificationManagerCompat.notify(
        IMAGE_SAVER_NOTIFICATIONS_TAG,
        NotificationConstants.ImageSaverNotifications.notificationId(uniqueId),
        notification
      )
    }

    fun cancelNotification(
      notificationManagerCompat: NotificationManagerCompat,
      uniqueId: String
    ) {
      val notificationId = NotificationConstants.ImageSaverNotifications.notificationId(uniqueId)
      Logger.d(TAG, "cancelNotification('$uniqueId', '$notificationId')")

      notificationManagerCompat.cancel(
        IMAGE_SAVER_NOTIFICATIONS_TAG,
        notificationId
      )
    }

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
