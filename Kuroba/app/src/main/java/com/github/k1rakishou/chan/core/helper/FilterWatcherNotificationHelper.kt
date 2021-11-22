package com.github.k1rakishou.chan.core.helper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.NotificationConstants
import com.github.k1rakishou.chan.utils.RequestCodes
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import dagger.Lazy
import org.joda.time.DateTime

class FilterWatcherNotificationHelper(
  private val appContext: Context,
  private val notificationManagerCompat: NotificationManagerCompat,
  private val _themeEngine: Lazy<ThemeEngine>
) {
  private val themeEngine: ThemeEngine
    get() = _themeEngine.get()

  fun showBookmarksCreatedNotification(createdBookmarks: Map<String, MutableList<ChanDescriptor.ThreadDescriptor>>) {
    if (createdBookmarks.isEmpty()) {
      return
    }

    val descriptorsEmpty = createdBookmarks.values.all { bookmarkDescriptors -> bookmarkDescriptors.isEmpty() }
    if (descriptorsEmpty) {
      return
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      showNotificationsForAndroidNougatAndBelow(createdBookmarks)
    } else {
      setupChannels()

      showSummaryNotification(createdBookmarks)
      showNotificationsForAndroidOreoAndAbove(createdBookmarks)
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun showSummaryNotification(createdBookmarks: Map<String, MutableList<ChanDescriptor.ThreadDescriptor>>): Boolean {
    val summaryNotificationBuilder = NotificationCompat.Builder(
      appContext,
      NotificationConstants.FilterWatcherNotifications.FW_SUMMARY_NOTIFICATION_CHANNEL_ID
    )

    val patternsCount = createdBookmarks.keys.size
    val bookmarkDescriptors = createdBookmarks.values.flatten()
    val bookmarksCount = bookmarkDescriptors.size

    val titleText = appContext.resources.getString(
      R.string.filter_watcher_new_bookmarks_total,
      patternsCount,
      bookmarksCount
    )

    summaryNotificationBuilder
      .setWhen(DateTime.now().millis)
      .setShowWhen(true)
      .setContentTitle(AndroidUtils.getApplicationLabel())
      .setContentText(titleText)
      .setSmallIcon(R.drawable.ic_stat_notify_alert)
      .setupClickOnNotificationIntent(
        requestCode = RequestCodes.nextRequestCode(),
        threadDescriptors = bookmarkDescriptors
      )
      .setAllowSystemGeneratedContextualActions(false)
      .setAutoCancel(true)
      .setGroup(notificationsGroup)
      .setGroupSummary(true)

    notificationManagerCompat.notify(
      NotificationConstants.FilterWatcherNotifications.SUMMARY_NOTIFICATION_TAG,
      NotificationConstants.FILTER_SUMMARY_NOTIFICATION_ID,
      summaryNotificationBuilder.build()
    )

    Logger.d(TAG, "showSummaryNotification() notificationManagerCompat.notify() called")
    return true
  }

  private fun showNotificationsForAndroidOreoAndAbove(
    createdBookmarks: Map<String, MutableList<ChanDescriptor.ThreadDescriptor>>
  ) {
    createdBookmarks.entries
      .take(NotificationConstants.MAX_VISIBLE_NOTIFICATIONS)
      .forEach { (pattern, bookmarkDescriptors) ->
        val titleText = getString(R.string.filter_watcher_new_bookmarks_per_pattern, createdBookmarks.size, pattern)
        val notificationTag = "${NotificationConstants.FilterWatcherNotifications.NOTIFICATION_TAG}_${pattern}"
        val notificationId = NotificationConstants.FilterWatcherNotifications.notificationId(pattern)

        val notificationBuilder = NotificationCompat.Builder(
          appContext,
          NotificationConstants.FilterWatcherNotifications.FW_NOTIFICATION_CHANNEL_ID
        )
          .setContentTitle(AndroidUtils.getApplicationLabel())
          .setContentText(titleText)
          .setWhen(DateTime.now().millis)
          .setShowWhen(true)
          .setSmallIcon(R.drawable.ic_stat_notify_alert)
          .setAutoCancel(true)
          .setupClickOnNotificationIntent(
            requestCode = RequestCodes.nextRequestCode(),
            threadDescriptors = bookmarkDescriptors
          )
          .setAllowSystemGeneratedContextualActions(false)
          .setCategory(Notification.CATEGORY_MESSAGE)
          .setPriority(NotificationCompat.PRIORITY_MAX)
          .setGroup(notificationsGroup)
          .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)

        notificationManagerCompat.notify(
          notificationTag,
          notificationId,
          notificationBuilder.build()
        )
      }
  }

  private fun showNotificationsForAndroidNougatAndBelow(
    createdBookmarks: Map<String, MutableList<ChanDescriptor.ThreadDescriptor>>
  ) {
    val patternsCount = createdBookmarks.keys.size
    val bookmarkDescriptors = createdBookmarks.values.flatten()
    val bookmarksCount = bookmarkDescriptors.size

    val titleText = appContext.resources.getString(
      R.string.filter_watcher_new_bookmarks_total,
      patternsCount,
      bookmarksCount
    )
    val notificationTag = NotificationConstants.FilterWatcherNotifications.NOTIFICATION_TAG
    val notificationId = NotificationConstants.FilterWatcherNotifications.notificationId(notificationTag)

    val notificationBuilder = NotificationCompat.Builder(appContext)
      .setContentTitle(AndroidUtils.getApplicationLabel())
      .setContentText(titleText)
      .setWhen(DateTime.now().millis)
      .setShowWhen(true)
      .setSmallIcon(R.drawable.ic_stat_notify_alert)
      .setAutoCancel(true)
      .setupClickOnNotificationIntent(
        requestCode = RequestCodes.nextRequestCode(),
        threadDescriptors = bookmarkDescriptors
      )
      .setAllowSystemGeneratedContextualActions(false)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(Notification.CATEGORY_MESSAGE)

    notificationManagerCompat.notify(
      notificationTag,
      notificationId,
      notificationBuilder.build()
    )
  }

  private fun NotificationCompat.Builder.setupClickOnNotificationIntent(
    requestCode: Int,
    threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>,
  ): NotificationCompat.Builder {
    val intent = Intent(appContext, StartActivity::class.java)
    val threadDescriptorsParcelable = threadDescriptors.map { threadDescriptor ->
      DescriptorParcelable.fromDescriptor(threadDescriptor)
    }

    intent
      .setAction(NotificationConstants.FILTER_WATCHER_NOTIFICATION_ACTION)
      .addCategory(Intent.CATEGORY_LAUNCHER)
      .setFlags(
        Intent.FLAG_ACTIVITY_CLEAR_TOP
          or Intent.FLAG_ACTIVITY_SINGLE_TOP
          or Intent.FLAG_ACTIVITY_NEW_TASK
          or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
      )
      .putParcelableArrayListExtra(
        NotificationConstants.FilterWatcherNotifications.FW_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY,
        ArrayList(threadDescriptorsParcelable)
      )

    val pendingIntent = PendingIntent.getActivity(
      appContext,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    setContentIntent(pendingIntent)
    return this
  }


  private fun setupChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    Logger.d(TAG, "setupChannels() called")

    if (notificationManagerCompat.getNotificationChannel(NotificationConstants.FilterWatcherNotifications.FW_NOTIFICATION_CHANNEL_ID) == null) {
      Logger.d(TAG, "setupChannels() creating ${NotificationConstants.FilterWatcherNotifications.FW_NOTIFICATION_CHANNEL_ID} channel")

      val summaryChannel = NotificationChannel(
        NotificationConstants.FilterWatcherNotifications.FW_NOTIFICATION_CHANNEL_ID,
        NotificationConstants.FilterWatcherNotifications.FW_NOTIFICATION_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
      )

      summaryChannel.setSound(
        Settings.System.DEFAULT_NOTIFICATION_URI,
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_NOTIFICATION)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
          .build()
      )

      summaryChannel.enableLights(true)
      summaryChannel.lightColor = themeEngine.chanTheme.accentColor
      summaryChannel.enableVibration(true)

      notificationManagerCompat.createNotificationChannel(summaryChannel)
    }

    if (notificationManagerCompat.getNotificationChannel(NotificationConstants.FilterWatcherNotifications.FW_SUMMARY_NOTIFICATION_CHANNEL_ID) == null) {
      Logger.d(TAG, "setupChannels() creating ${NotificationConstants.FilterWatcherNotifications.FW_SUMMARY_NOTIFICATION_CHANNEL_ID} channel")

      val summaryChannel = NotificationChannel(
        NotificationConstants.FilterWatcherNotifications.FW_SUMMARY_NOTIFICATION_CHANNEL_ID,
        NotificationConstants.FilterWatcherNotifications.FW_SUMMARY_NOTIFICATION_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
      )

      summaryChannel.setSound(
        Settings.System.DEFAULT_NOTIFICATION_URI,
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_NOTIFICATION)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
          .build()
      )

      summaryChannel.enableLights(true)
      summaryChannel.lightColor = themeEngine.chanTheme.accentColor
      summaryChannel.enableVibration(true)

      notificationManagerCompat.createNotificationChannel(summaryChannel)
    }
  }

  companion object {
    private const val TAG = "FilterWatcherNotificationHelper"

    private val notificationsGroup by lazy { "${TAG}_${BuildConfig.APPLICATION_ID}_${AppModuleAndroidUtils.getFlavorType().name}" }
  }

}