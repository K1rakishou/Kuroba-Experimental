package com.github.adamantcheese.chan.core.manager

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
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.StartActivity
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.NotificationConstants
import com.github.adamantcheese.chan.utils.NotificationConstants.LastPageNotifications.LAST_PAGE_NOTIFICATION_CHANNEL_ID
import com.github.adamantcheese.chan.utils.NotificationConstants.LastPageNotifications.LAST_PAGE_NOTIFICATION_NAME
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.ThreadDescriptorParcelable
import java.util.*

class LastPageNotificationsHelper(
  private val isDevFlavor: Boolean,
  private val appContext: Context,
  private val notificationManagerCompat: NotificationManagerCompat,
  private val pageRequestManager: PageRequestManager,
  private val bookmarksManager: BookmarksManager
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
    val currentlyOpenedThread = bookmarksManager.currentlyOpenedThread()

    watchingBookmarkDescriptors.forEach { threadDescriptor ->
      if (threadDescriptor == currentlyOpenedThread) {
        Logger.d(TAG, "Skipping notification for currently opened thread ($currentlyOpenedThread)")
        return@forEach
      }

      if (pageRequestManager.canAlertAboutThreadBeingOnLastPage(threadDescriptor)) {
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

    if (isDevFlavor) {
      // TODO(KurobaEx): remove?
      threadsWithTitles.forEach { (threadDescriptor, title) ->
        Logger.d(TAG, "Thread $threadDescriptor (${title.take(50)}) hit last page")
      }
    }

    setupChannels()

    notificationManagerCompat.notify(
      NotificationConstants.LastPageNotifications.LAST_PAGE_NOTIFICATION_TAG,
      NotificationConstants.LastPageNotifications.LAST_PAGE_NOTIFICATION_ID,
      getNotification(threadsWithTitles)
    )

    Logger.d(TAG, "notificationManagerCompat.notify() called")
  }

  private fun getNotification(
    threadsWithTitles: List<Pair<ChanDescriptor.ThreadDescriptor, String>>
  ): Notification {
    val intent = Intent(appContext, StartActivity::class.java)

    val threadDescriptorsParcelable = threadsWithTitles.map { (threadDescriptor, _) ->
      ThreadDescriptorParcelable.fromThreadDescriptor(threadDescriptor)
    }

    intent
      .setAction(Intent.ACTION_MAIN)
      .addCategory(Intent.CATEGORY_LAUNCHER)
      .setFlags(
        Intent.FLAG_ACTIVITY_CLEAR_TOP
          or Intent.FLAG_ACTIVITY_SINGLE_TOP
          or Intent.FLAG_ACTIVITY_NEW_TASK
          or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
      )
      .putParcelableArrayListExtra(
        NotificationConstants.LastPageNotifications.NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY,
        ArrayList(threadDescriptorsParcelable)
      )

    val pendingIntent = PendingIntent.getActivity(
      appContext,
      NotificationConstants.LastPageNotifications.NOTIFICATION_CLICK_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_ONE_SHOT
    )

    val threadsOnLastPageCount = threadsWithTitles.count()
    val title = "$threadsOnLastPageCount thread(s) hit last page"

    return NotificationCompat.Builder(appContext, LAST_PAGE_NOTIFICATION_CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_notify_alert)
      .setContentTitle(title)
      .setupNotificationStyle(title, threadsWithTitles)
      .setContentIntent(pendingIntent)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
      .setLights(appContext.resources.getColor(R.color.accent), 1000, 1000)
      .setAutoCancel(true)
      .setWhen(System.currentTimeMillis())
      .build()
  }

  private fun NotificationCompat.Builder.setupNotificationStyle(
    notificationTitle: String,
    threadsWithTitles: List<Pair<ChanDescriptor.ThreadDescriptor, String>>
  ): NotificationCompat.Builder {
    val threadsThatHitLastPageRecently = pageRequestManager.getThreadNoTimeModPairList(
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

    if (notificationManagerCompat.getNotificationChannel(LAST_PAGE_NOTIFICATION_CHANNEL_ID) == null) {
      Logger.d(TAG, "setupChannels() creating ${LAST_PAGE_NOTIFICATION_CHANNEL_ID} channel")

      val lastPageAlertChannel = NotificationChannel(
        LAST_PAGE_NOTIFICATION_CHANNEL_ID,
        LAST_PAGE_NOTIFICATION_NAME,
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
      lastPageAlertChannel.lightColor = appContext.resources.getColor(R.color.accent)

      notificationManagerCompat.createNotificationChannel(lastPageAlertChannel)
    }
  }

  companion object {
    private const val TAG = "LastPageNotificationsHelper"
  }
}