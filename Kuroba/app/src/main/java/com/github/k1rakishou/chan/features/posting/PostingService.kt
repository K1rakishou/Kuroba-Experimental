package com.github.k1rakishou.chan.features.posting

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.NotificationConstants
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

class PostingService : Service() {

  @Inject
  lateinit var postingServiceDelegate: PostingServiceDelegate

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
      postingServiceDelegate.listenForStopServiceEvents()
        .collect {
          Logger.d(TAG, "Got StopService command, stopping the service")

          stopForeground(true)
          stopSelf()
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
      createServiceNotification()
    )

    val chanDescriptor = intent.getParcelableExtra<DescriptorParcelable>(REPLY_CHAN_DESCRIPTOR)
      ?.toChanDescriptor()
    val retrying = intent.getBooleanExtra(RETRYING, false)

    if (chanDescriptor == null) {
      Logger.e(TAG, "onStartCommand() intent has no $REPLY_CHAN_DESCRIPTOR")
      return START_NOT_STICKY
    }

    postingServiceDelegate.onNewReply(chanDescriptor, retrying)
    Logger.d(TAG, "onStartCommand() onNewReply($chanDescriptor, $retrying)")

    return START_REDELIVER_INTENT
  }

  private fun createServiceNotification(): Notification {
    BackgroundUtils.ensureMainThread()
    setupChannels()

    return NotificationCompat.Builder(
      applicationContext,
      NotificationConstants.PostingServiceNotifications.NOTIFICATION_CHANNEL_ID
    )
      .setContentTitle(getString(R.string.post_service_processing))
      .setSmallIcon(R.drawable.ic_stat_notify)
      .setOngoing(true)
      .build()
  }

  @Synchronized
  private fun setupChannels() {
    BackgroundUtils.ensureMainThread()

    if (!AndroidUtils.isAndroidO()) {
      return
    }

    with(NotificationConstants.PostingServiceNotifications.NOTIFICATION_CHANNEL_ID) {
      if (notificationManagerCompat.getNotificationChannel(this) == null) {
        Logger.d(TAG, "setupChannels() creating $this channel")

        val imageSaverChannel = NotificationChannel(
          this,
          NotificationConstants.PostingServiceNotifications.NOTIFICATION_NAME,
          NotificationManager.IMPORTANCE_DEFAULT
        )

        imageSaverChannel.setSound(null, null)
        imageSaverChannel.enableLights(false)
        imageSaverChannel.enableVibration(false)

        notificationManagerCompat.createNotificationChannel(imageSaverChannel)
      }
    }
  }

  companion object {
    private const val TAG = "PostingService"

    const val REPLY_CHAN_DESCRIPTOR = "posting_service_reply_chan_descriptor"
    const val RETRYING = "posting_service_retrying"

    fun enqueueReplyChanDescriptor(context: Context, chanDescriptor: ChanDescriptor, retrying: Boolean) {
      val startServiceIntent = Intent(
        context,
        PostingService::class.java
      )

      startServiceIntent.putExtra(REPLY_CHAN_DESCRIPTOR, DescriptorParcelable.fromDescriptor(chanDescriptor))
      startServiceIntent.putExtra(RETRYING, retrying)

      context.startService(startServiceIntent)
    }
  }
}