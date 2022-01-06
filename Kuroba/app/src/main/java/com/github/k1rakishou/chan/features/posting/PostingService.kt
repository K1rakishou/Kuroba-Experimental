package com.github.k1rakishou.chan.features.posting

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
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.receiver.PostingServiceBroadcastReceiver
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.NotificationConstants
import com.github.k1rakishou.chan.utils.RequestCodes
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

class PostingService : Service() {

  @Inject
  lateinit var postingServiceDelegate: Lazy<PostingServiceDelegate>

  private val notificationManagerCompat by lazy { NotificationManagerCompat.from(applicationContext) }
  private val kurobaScope = KurobaCoroutineScope()

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }

  override fun onCreate() {
    super.onCreate()

    Logger.d(TAG, "onCreate()")
    Chan.getComponent().inject(this)

    kurobaScope.launch {
      postingServiceDelegate.get().listenForStopServiceEvents()
        .collect {
          Logger.d(TAG, "Got StopService command, stopping the service")

          stopForeground(true)
          stopSelf()
        }
    }

    kurobaScope.launch {
      postingServiceDelegate.get().listenForMainNotificationUpdates()
        .collect { mainNotificationInfo ->
          Logger.d(TAG, "mainNotificationUpdates() activeRepliesCount=${mainNotificationInfo.activeRepliesCount}")

          notificationManagerCompat.notify(
            NotificationConstants.POSTING_SERVICE_NOTIFICATION_ID,
            createMainNotification(mainNotificationInfo)
          )
        }
    }

    kurobaScope.launch {
      postingServiceDelegate.get().listenForChildNotificationUpdates()
        .collect { childNotificationInfo ->
          Logger.d(TAG, "childNotificationUpdates() descriptor=${childNotificationInfo.chanDescriptor}, " +
            "status=${childNotificationInfo.status}")

          val chanDescriptor = childNotificationInfo.chanDescriptor
          val notificationId = NotificationConstants.PostingServiceNotifications.notificationId(chanDescriptor)

          notificationManagerCompat.notify(
            "${CHILD_NOTIFICATION_TAG}_${chanDescriptor.serializeToString()}",
            notificationId,
            createChildNotification(childNotificationInfo)
          )
        }
    }

    kurobaScope.launch {
      postingServiceDelegate.get().listenForChildNotificationsToClose()
        .collect { chanDescriptor ->
          Logger.d(TAG, "cancelNotification($chanDescriptor)")
          val notificationId = NotificationConstants.PostingServiceNotifications.notificationId(chanDescriptor)

          notificationManagerCompat.cancel(
            "${CHILD_NOTIFICATION_TAG}_${chanDescriptor.serializeToString()}",
            notificationId
          )
        }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    Logger.d(TAG, "onDestroy()")
    kurobaScope.cancelChildren()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent == null) {
      Logger.e(TAG, "onStartCommand() intent == null")
      return START_NOT_STICKY
    }

    startForeground(
      NotificationConstants.POSTING_SERVICE_NOTIFICATION_ID,
      createMainNotification(mainNotificationInfo = null)
    )

    val chanDescriptor = intent.getParcelableExtra<DescriptorParcelable>(REPLY_CHAN_DESCRIPTOR)
      ?.toChanDescriptor()
    val replyMode = ReplyMode.fromString(intent.getStringExtra(REPLY_MODE))
    val retrying = intent.getBooleanExtra(RETRYING, false)

    if (chanDescriptor == null) {
      Logger.e(TAG, "onStartCommand() intent has no $REPLY_CHAN_DESCRIPTOR")
      return START_NOT_STICKY
    }

    postingServiceDelegate.get().onNewReply(chanDescriptor, replyMode, retrying)
    Logger.d(TAG, "onStartCommand() onNewReply($chanDescriptor, $retrying)")

    return START_REDELIVER_INTENT
  }

  private fun createMainNotification(
    mainNotificationInfo: MainNotificationInfo?
  ): Notification {
    BackgroundUtils.ensureMainThread()
    setupChannels()

    val titleString = if (mainNotificationInfo == null || mainNotificationInfo.activeRepliesCount <= 0) {
      getString(R.string.post_service_processing)
    } else {
      getString(R.string.post_service_processing_count, mainNotificationInfo.activeRepliesCount)
    }

    return NotificationCompat.Builder(
      applicationContext,
      NotificationConstants.PostingServiceNotifications.MAIN_NOTIFICATION_CHANNEL_ID
    )
      .setContentTitle(titleString)
      .setSmallIcon(R.drawable.ic_stat_notify)
      .setOngoing(true)
      .addCancelAllAction()
      .build()
  }

  private fun createChildNotification(
    childNotificationInfo: ChildNotificationInfo
  ): Notification {
    BackgroundUtils.ensureMainThread()
    setupChannels()

    val iconId = when (childNotificationInfo.status) {
      is ChildNotificationInfo.Status.Preparing -> R.drawable.ic_stat_notify
      is ChildNotificationInfo.Status.WaitingForSiteRateLimitToPass,
      is ChildNotificationInfo.Status.WaitingForAdditionalService -> R.drawable.ic_baseline_access_time_24
      is ChildNotificationInfo.Status.Uploading -> android.R.drawable.stat_sys_upload
      is ChildNotificationInfo.Status.Uploaded -> android.R.drawable.stat_sys_upload_done
      is ChildNotificationInfo.Status.Posted -> android.R.drawable.stat_sys_upload_done
      ChildNotificationInfo.Status.Canceled -> R.drawable.ic_stat_notify
      is ChildNotificationInfo.Status.Error -> android.R.drawable.stat_sys_warning
    }

    val style = NotificationCompat.BigTextStyle()
      .setBigContentTitle(childNotificationInfo.chanDescriptor.userReadableString())
      .setSummaryText(childNotificationInfo.status.statusText)
      .bigText(childNotificationInfo.status.statusText)

    return NotificationCompat.Builder(
      applicationContext,
      NotificationConstants.PostingServiceNotifications.CHILD_NOTIFICATION_CHANNEL_ID
    )
      .setStyle(style)
      .setContentTitle(childNotificationInfo.chanDescriptor.userReadableString())
      .setContentText(childNotificationInfo.status.statusText)
      .setSmallIcon(iconId)
      .setOngoing(childNotificationInfo.isOngoing)
      .addCancelAction(childNotificationInfo)
      .addNotificationClickAction(childNotificationInfo)
      .addNotificationSwipeAwayAction(childNotificationInfo)
      .setTimeoutEx(childNotificationInfo)
      .build()
  }

  @Synchronized
  private fun setupChannels() {
    BackgroundUtils.ensureMainThread()

    if (!AndroidUtils.isAndroidO()) {
      return
    }

    with(NotificationConstants.PostingServiceNotifications.MAIN_NOTIFICATION_CHANNEL_ID) {
      if (notificationManagerCompat.getNotificationChannel(this) == null) {
        Logger.d(TAG, "setupChannels() creating $this channel")

        val imageSaverChannel = NotificationChannel(
          this,
          NotificationConstants.PostingServiceNotifications.MAIN_NOTIFICATION_NAME,
          NotificationManager.IMPORTANCE_DEFAULT
        )

        imageSaverChannel.setSound(null, null)
        imageSaverChannel.enableLights(false)
        imageSaverChannel.enableVibration(false)

        notificationManagerCompat.createNotificationChannel(imageSaverChannel)
      }
    }

    with(NotificationConstants.PostingServiceNotifications.CHILD_NOTIFICATION_CHANNEL_ID) {
      if (notificationManagerCompat.getNotificationChannel(this) == null) {
        Logger.d(TAG, "setupChannels() creating $this channel")

        val imageSaverChannel = NotificationChannel(
          this,
          NotificationConstants.PostingServiceNotifications.CHILD_NOTIFICATION_NAME,
          NotificationManager.IMPORTANCE_DEFAULT
        )

        imageSaverChannel.setSound(null, null)
        imageSaverChannel.enableLights(false)
        imageSaverChannel.enableVibration(false)

        notificationManagerCompat.createNotificationChannel(imageSaverChannel)
      }
    }
  }

  private fun NotificationCompat.Builder.addCancelAllAction(): NotificationCompat.Builder {
    val intent = Intent(applicationContext, PostingServiceBroadcastReceiver::class.java).apply {
      setAction(ACTION_TYPE_CANCEL_ALL)
    }

    val cancelIntent = PendingIntent.getBroadcast(
      applicationContext,
      RequestCodes.nextRequestCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return addAction(0, getString(R.string.cancel_all), cancelIntent)
  }

  private fun NotificationCompat.Builder.addNotificationSwipeAwayAction(
    childNotificationInfo: ChildNotificationInfo
  ): NotificationCompat.Builder {
    val intent = Intent(applicationContext, PostingServiceBroadcastReceiver::class.java).apply {
      setAction(ACTION_TYPE_CANCEL)

      putExtra(CHAN_DESCRIPTOR, DescriptorParcelable.fromDescriptor(childNotificationInfo.chanDescriptor))
    }

    val cancelIntent = PendingIntent.getBroadcast(
      applicationContext,
      RequestCodes.nextRequestCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    setDeleteIntent(cancelIntent)
    return this
  }

  private fun NotificationCompat.Builder.addCancelAction(
    childNotificationInfo: ChildNotificationInfo
  ): NotificationCompat.Builder {
    if (!childNotificationInfo.canCancel) {
      return this
    }

    val intent = Intent(applicationContext, PostingServiceBroadcastReceiver::class.java).apply {
      setAction(ACTION_TYPE_CANCEL)

      putExtra(CHAN_DESCRIPTOR, DescriptorParcelable.fromDescriptor(childNotificationInfo.chanDescriptor))
    }

    val cancelIntent = PendingIntent.getBroadcast(
      applicationContext,
      RequestCodes.nextRequestCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return addAction(0, getString(R.string.cancel), cancelIntent)
  }

  private fun NotificationCompat.Builder.addNotificationClickAction(
    childNotificationInfo: ChildNotificationInfo
  ): NotificationCompat.Builder {
    val intent = Intent(applicationContext, StartActivity::class.java)
    val descriptorParcelable = DescriptorParcelable.fromDescriptor(childNotificationInfo.chanDescriptor)

    intent
      .setAction(NotificationConstants.POSTING_NOTIFICATION_ACTION)
      .addCategory(Intent.CATEGORY_LAUNCHER)
      .setFlags(
        Intent.FLAG_ACTIVITY_CLEAR_TOP
          or Intent.FLAG_ACTIVITY_SINGLE_TOP
          or Intent.FLAG_ACTIVITY_NEW_TASK
          or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
      )
      .putExtra(
        NotificationConstants.PostingServiceNotifications.NOTIFICATION_CLICK_CHAN_DESCRIPTOR_KEY,
        descriptorParcelable
      )

    val pendingIntent = PendingIntent.getActivity(
      applicationContext,
      RequestCodes.nextRequestCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    setContentIntent(pendingIntent)
    return this
  }

  private fun NotificationCompat.Builder.setTimeoutEx(
    childNotificationInfo: ChildNotificationInfo
  ): NotificationCompat.Builder {
    if (childNotificationInfo.status !is ChildNotificationInfo.Status.Posted
      && childNotificationInfo.status !is ChildNotificationInfo.Status.Canceled) {
      return this
    }

    setTimeoutAfter(10_000)
    return this
  }

  companion object {
    private const val TAG = "PostingService"

    const val REPLY_CHAN_DESCRIPTOR = "posting_service_reply_chan_descriptor"
    const val REPLY_MODE = "posting_service_reply_mode"
    const val RETRYING = "posting_service_retrying"

    private const val CHILD_NOTIFICATION_TAG = "${TAG}_ChildNotification"

    const val CHAN_DESCRIPTOR = "chan_descriptor"

    const val ACTION_TYPE_CANCEL_ALL = "${TAG}_ACTION_CANCEL_ALL"
    const val ACTION_TYPE_CANCEL = "${TAG}_ACTION_CANCEL"

    fun enqueueReplyChanDescriptor(
      context: Context,
      chanDescriptor: ChanDescriptor,
      replyMode: ReplyMode,
      retrying: Boolean
    ) {
      val startServiceIntent = Intent(
        context,
        PostingService::class.java
      )

      startServiceIntent.putExtra(REPLY_CHAN_DESCRIPTOR, DescriptorParcelable.fromDescriptor(chanDescriptor))
      startServiceIntent.putExtra(REPLY_MODE, replyMode.modeRaw)
      startServiceIntent.putExtra(RETRYING, retrying)

      context.startService(startServiceIntent)
    }
  }
}