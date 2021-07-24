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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.utils.NotificationConstants
import com.github.k1rakishou.chan.utils.RequestCodes
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import dagger.Lazy
import java.util.*

class LastPageNotificationsHelper(
  private val isDevFlavor: Boolean,
  private val appContext: Context,
  private val notificationManagerCompat: NotificationManagerCompat,
  private val pageRequestManager: Lazy<PageRequestManager>,
  private val bookmarksManager: BookmarksManager,
  private val themeEngine: ThemeEngine,
  private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
) {

  fun showOrUpdateNotifications(watchingBookmarkDescriptors: List<ChanDescriptor.ThreadDescriptor>) {
    Logger.d(TAG, "showOrUpdateNotifications(${watchingBookmarkDescriptors.size})")

    if (watchingBookmarkDescriptors.isEmpty()) {
      Logger.d(TAG, "watchingBookmarkDescriptors is empty")
      return
    }

    if (!ChanSettings.watchLastPageNotify.get()) {
      Logger.d(TAG, "ChanSettings.watchLastPageNotify is disabled")
      return
    }

    val threadsOnLastPage = mutableSetOf<ChanDescriptor.ThreadDescriptor>()
    val currentlyOpenedThread = currentOpenedDescriptorStateManager.currentThreadDescriptor

    watchingBookmarkDescriptors.forEach { threadDescriptor ->
      if (threadDescriptor == currentlyOpenedThread) {
        Logger.d(TAG, "Skipping notification for currently opened thread ($currentlyOpenedThread)")
        return@forEach
      }

      if (pageRequestManager.get().canAlertAboutThreadBeingOnLastPage(threadDescriptor)) {
        threadsOnLastPage += threadDescriptor
      }
    }

    if (threadsOnLastPage.isEmpty()) {
      Logger.d(TAG, "No threads to notify about last page")
      return
    }

    val threadsWithTitles = bookmarksManager.mapBookmarks(threadsOnLastPage) { threadBookmarkView ->
      val threadTitle = threadBookmarkView.title
        ?: threadBookmarkView.threadDescriptor.threadNo.toString()

      return@mapBookmarks threadBookmarkView.threadDescriptor to threadTitle
    }

    if (threadsWithTitles.isEmpty()) {
      Logger.d(TAG, "threadsWithTitles is empty")
      return
    }

    setupChannels()

    notificationManagerCompat.notify(
      NotificationConstants.LastPageNotifications.LAST_PAGE_NOTIFICATION_TAG,
      NotificationConstants.LAST_PAGE_NOTIFICATION_ID,
      getNotification(threadsWithTitles)
    )

    Logger.d(TAG, "notificationManagerCompat.notify() called")
  }

  private fun getNotification(
    threadsWithTitles: List<Pair<ChanDescriptor.ThreadDescriptor, String>>
  ): Notification {
    val threadsOnLastPageCount = threadsWithTitles.size
    val title = appContext.resources.getString(
      R.string.last_page_notification_threads_hit_last_page_format,
      threadsOnLastPageCount
    )
    val useSoundForLastPageNotifications = ChanSettings.useSoundForLastPageNotifications.get()

    val builder = if (useSoundForLastPageNotifications) {
      NotificationCompat.Builder(
        appContext,
        NotificationConstants.LastPageNotifications.LAST_PAGE_NOTIFICATION_CHANNEL_ID
      )
    } else {
      NotificationCompat.Builder(
        appContext,
        NotificationConstants.LastPageNotifications.LAST_PAGE_SILENT_NOTIFICATION_CHANNEL_ID
      )
    }

    val priority = if (useSoundForLastPageNotifications) {
      NotificationCompat.PRIORITY_MAX
    } else {
      NotificationCompat.PRIORITY_LOW
    }

    return builder
      .setSmallIcon(R.drawable.ic_stat_notify_alert)
      .setContentTitle(title)
      .setupNotificationStyle(title, threadsWithTitles)
      .setupLastPageNotificationClicked(threadsWithTitles)
      .setPriority(priority)
      .setupSoundAndVibration(useSoundForLastPageNotifications)
      .setAutoCancel(true)
      .setWhen(System.currentTimeMillis())
      .setAllowSystemGeneratedContextualActions(false)
      .build()
  }

  private fun NotificationCompat.Builder.setupSoundAndVibration(
    useSoundForLastPageNotifications: Boolean
  ): NotificationCompat.Builder {
    Logger.d(
      TAG, "Using sound and vibration: " +
      "useSoundForLastPageNotifications=${useSoundForLastPageNotifications}")

    if (useSoundForLastPageNotifications) {
      setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
    } else {
      setDefaults(Notification.DEFAULT_VIBRATE)
    }

    setLights(themeEngine.chanTheme.accentColor, 1000, 1000)
    return this
  }

  private fun NotificationCompat.Builder.setupLastPageNotificationClicked(
    threadsWithTitles: List<Pair<ChanDescriptor.ThreadDescriptor, String>>
  ): NotificationCompat.Builder {
    Logger.d(TAG, "setupLastPageNotificationClicked() threads count = ${threadsWithTitles.size}")

    val intent = Intent(appContext, StartActivity::class.java)
    val threadDescriptorsParcelable = threadsWithTitles.map { (threadDescriptor, _) ->
      DescriptorParcelable.fromDescriptor(threadDescriptor)
    }

    intent
      .setAction(NotificationConstants.LAST_PAGE_NOTIFICATION_ACTION)
      .addCategory(Intent.CATEGORY_LAUNCHER)
      .setFlags(
        Intent.FLAG_ACTIVITY_CLEAR_TOP
          or Intent.FLAG_ACTIVITY_SINGLE_TOP
          or Intent.FLAG_ACTIVITY_NEW_TASK
          or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
      )
      .putParcelableArrayListExtra(
        NotificationConstants.LastPageNotifications.LP_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY,
        ArrayList(threadDescriptorsParcelable)
      )

    val pendingIntent = PendingIntent.getActivity(
      appContext,
      RequestCodes.nextRequestCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT
    )

    setContentIntent(pendingIntent)
    return this
  }

  private fun NotificationCompat.Builder.setupNotificationStyle(
    notificationTitle: String,
    threadsWithTitles: List<Pair<ChanDescriptor.ThreadDescriptor, String>>
  ): NotificationCompat.Builder {
    val threadsThatHitLastPageRecently = pageRequestManager.get().getThreadNoTimeModPairList(
      threadsWithTitles.map { it.first }.toSet()
    )

    val sortedAndFiltered = threadsThatHitLastPageRecently
      .sortedBy { threadNoTimeModPair -> threadNoTimeModPair.modified }
      .takeLast(NotificationConstants.MAX_LINES_IN_NOTIFICATION)
      .map { threadNoTimeModPair -> threadNoTimeModPair.threadDescriptor }

    val styleBuilder = NotificationCompat.InboxStyle(this)
      .setSummaryText(notificationTitle)

    sortedAndFiltered.forEach { threadDescriptor ->
      val threadTitle = threadsWithTitles
        .firstOrNull { threadWithTitle -> threadWithTitle.first == threadDescriptor }
        ?.second
        ?: return@forEach

      styleBuilder.addLine(threadTitle)
    }

    setStyle(styleBuilder)
    return this
  }

  private fun setupChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    Logger.d(TAG, "setupChannels() called")

    if (notificationManagerCompat.getNotificationChannel(NotificationConstants.LastPageNotifications.LAST_PAGE_NOTIFICATION_CHANNEL_ID) == null) {
      Logger.d(
        TAG, "setupChannels() creating " +
        "${NotificationConstants.LastPageNotifications.LAST_PAGE_NOTIFICATION_CHANNEL_ID} channel")

      val lastPageAlertChannel = NotificationChannel(
        NotificationConstants.LastPageNotifications.LAST_PAGE_NOTIFICATION_CHANNEL_ID,
        NotificationConstants.LastPageNotifications.LAST_PAGE_NOTIFICATION_NAME,
        NotificationManager.IMPORTANCE_HIGH
      )

      lastPageAlertChannel.setSound(
        Settings.System.DEFAULT_NOTIFICATION_URI,
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
          .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
          .build()
      )

      lastPageAlertChannel.enableLights(true)
      lastPageAlertChannel.lightColor = themeEngine.chanTheme.accentColor
      lastPageAlertChannel.enableVibration(true)

      notificationManagerCompat.createNotificationChannel(lastPageAlertChannel)
    }

    if (notificationManagerCompat.getNotificationChannel(NotificationConstants.LastPageNotifications.LAST_PAGE_SILENT_NOTIFICATION_CHANNEL_ID) == null) {
      Logger.d(
        TAG, "setupChannels() creating " +
        "${NotificationConstants.LastPageNotifications.LAST_PAGE_SILENT_NOTIFICATION_CHANNEL_ID} channel")

      // notification channel for replies summary
      val lastPageSilentChannel = NotificationChannel(
        NotificationConstants.LastPageNotifications.LAST_PAGE_SILENT_NOTIFICATION_CHANNEL_ID,
        NotificationConstants.LastPageNotifications.LAST_PAGE_SILENT_NOTIFICATION_NAME,
        NotificationManager.IMPORTANCE_LOW
      )

      notificationManagerCompat.createNotificationChannel(lastPageSilentChannel)
    }
  }

  companion object {
    private const val TAG = "LastPageNotificationsHelper"
  }
}