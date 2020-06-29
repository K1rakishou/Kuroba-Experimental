package com.github.adamantcheese.chan.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.adamantcheese.chan.BuildConfig
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.utils.AndroidUtils.getFlavorType
import com.github.adamantcheese.chan.utils.AndroidUtils.getUniqueAppName
import com.github.adamantcheese.model.data.bookmark.ThreadBookmarkReplyView
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import java.util.concurrent.atomic.AtomicInteger

class ReplyNotificationsHelper(
  private val appContext: Context,
  private val notificationManagerCompat: NotificationManagerCompat,
  private val notificationManager: NotificationManager
) {
  private val notificationIdCounter = AtomicInteger(1000)
  private val notificationIdMap = mutableMapOf<ChanDescriptor.ThreadDescriptor, Int>()

  fun showNotificationForReplies(
    unseenNotificationsGrouped: MutableMap<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>
  ): Map<ChanDescriptor.ThreadDescriptor, Set<ThreadBookmarkReplyView>> {
    Logger.d(TAG, "showNotificationForReplies(${unseenNotificationsGrouped.size})")

    if (unseenNotificationsGrouped.isEmpty()) {
      return emptyMap()
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      showNotificationsForAndroidNougatAndBelow(unseenNotificationsGrouped)
      return emptyMap()
    }

    setupChannels()
    restoreNotificationIdMap(unseenNotificationsGrouped)

    val notificationsGroup = "${BuildConfig.APPLICATION_ID}_${getFlavorType().name}"

    showSummaryNotification(notificationsGroup, unseenNotificationsGrouped)
    val shownNotifications = showNotificationsForAndroidOreoAndAbove(
      notificationsGroup,
      unseenNotificationsGrouped
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      notificationManager.activeNotifications.forEach { notification ->
        Logger.d(TAG, "active notification, id: ${notification.id}, group=${notification.isGroup}, " +
          "groupKey=${notification.notification.group}, behavior=${notification.notification.groupAlertBehavior}")
      }
    }

    return shownNotifications
  }

  private fun restoreNotificationIdMap(
    unseenNotificationsGrouped: MutableMap<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>
  ) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return
    }

    val maxNotificationId = notificationManager.activeNotifications
      .maxBy { notification -> notification.id }?.id ?: 0

    if (maxNotificationId > notificationIdCounter.get()) {
      notificationIdCounter.set(maxNotificationId)
    }

    val visibleNotifications = notificationManager.activeNotifications
      .associateBy { notification -> notification.tag }

    unseenNotificationsGrouped.keys.forEach { threadDescriptor ->
      val tag = getUniqueNotificationTag(threadDescriptor)

      if (visibleNotifications.containsKey(tag)) {
        notificationIdMap[threadDescriptor] = visibleNotifications[tag]!!.id
      }
    }
  }

  private fun showNotificationsForAndroidNougatAndBelow(
    unseenNotificationsGrouped: MutableMap<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>
  ) {
    Logger.d(TAG, "showNotificationsForAndroidNougatAndBelow() " +
      "unseenNotificationsGrouped = ${unseenNotificationsGrouped.size}")
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun showSummaryNotification(
    notificationsGroup: String,
    unseenNotificationsGrouped: MutableMap<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>
  ) {
    val summaryNotificationBuilder = NotificationCompat.Builder(appContext, REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID)
    val threadsWithUnseenRepliesCount = unseenNotificationsGrouped.size
    val totalUnseenRepliesCount = unseenNotificationsGrouped.values.sumBy { replies -> replies.size }
    val titleText = "You have $totalUnseenRepliesCount replies in $threadsWithUnseenRepliesCount thread(s)"

    check(threadsWithUnseenRepliesCount > 0) { "Bad threadsWithUnseenRepliesCount" }
    check(totalUnseenRepliesCount > 0) { "Bad totalUnseenRepliesCount" }

    summaryNotificationBuilder
      .setWhen(System.currentTimeMillis())
      .setShowWhen(true)
      .setContentTitle(getUniqueAppName())
      .setContentText(titleText)
      .setSmallIcon(R.drawable.ic_stat_notify)
      .setupSummaryNotificationsStyle()
      // TODO(KurobaEx):
//      .setContentIntent(TODO)
      .setAutoCancel(true)
      .setGroup(notificationsGroup)
      .setGroupSummary(true)

    notificationManagerCompat.notify(
      SUMMARY_NOTIFICATION_TAG,
      SUMMARY_NOTIFICATION_ID,
      summaryNotificationBuilder.build()
    )

    Logger.d(TAG, "showSummaryNotification() called")
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun showNotificationsForAndroidOreoAndAbove(
    notificationsGroup: String,
    unseenNotificationsGrouped: MutableMap<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>
  ): Map<ChanDescriptor.ThreadDescriptor, Set<ThreadBookmarkReplyView>> {
    Logger.d(TAG, "showNotificationsForAndroidOreoAndAbove() called")

    val shownNotifications = mutableMapOf<ChanDescriptor.ThreadDescriptor, HashSet<ThreadBookmarkReplyView>>()
    var notificationCounter = 0

    for ((threadDescriptor, threadBookmarkReplyViewSet) in unseenNotificationsGrouped) {
      val repliesCountText = "You have ${threadBookmarkReplyViewSet.size} new replies in thread ${threadDescriptor.threadNo}"
      val notificationTag = getUniqueNotificationTag(threadDescriptor)

      val notificationBuilder = NotificationCompat.Builder(appContext, REPLY_NOTIFICATION_CHANNEL_ID)
        .setContentTitle(repliesCountText)
        .setWhen(System.currentTimeMillis())
        .setShowWhen(true)
        // TODO(KurobaEx): 
  //        .setContentIntent(TODO)
        .setSmallIcon(R.drawable.ic_stat_notify_alert)
        .setAutoCancel(true)
        .setupReplyNotificationsStyle(threadBookmarkReplyViewSet)
        .setCategory(Notification.CATEGORY_MESSAGE)
        .setGroup(notificationsGroup)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)

      notificationManagerCompat.notify(
        notificationTag,
        getOrCalculateNotificationId(threadDescriptor),
        notificationBuilder.build()
      )

      Logger.d(TAG, "showNotificationsForAndroidOreoAndAbove() created notification " +
        "with tag ${notificationTag}, counter=${notificationCounter}")

      ++notificationCounter

      shownNotifications.putIfNotContains(threadDescriptor, hashSetOf())
      shownNotifications[threadDescriptor]!!.addAll(threadBookmarkReplyViewSet)

      if (notificationCounter > MAX_VISIBLE_NOTIFICATIONS) {
        break
      }
    }

    return shownNotifications
  }

  private fun NotificationCompat.Builder.setupSummaryNotificationsStyle(): NotificationCompat.Builder {
    setStyle(NotificationCompat.InboxStyle(this).setSummaryText("Test"))
    return this
  }

  private fun NotificationCompat.Builder.setupReplyNotificationsStyle(
    threadBookmarkReplyViewSet: MutableSet<ThreadBookmarkReplyView>
  ): NotificationCompat.Builder {
    val notificationStyle = NotificationCompat.InboxStyle(this)
    val repliesSorted = threadBookmarkReplyViewSet
      .sortedByDescending { reply -> reply.postDescriptor.postNo }
      .take(MAX_LINES_IN_NOTIFICATION)

    repliesSorted.forEach { reply ->
      notificationStyle.addLine("Reply from ${reply.postDescriptor.postNo} to ${reply.repliesTo.postNo}")
    }

    setStyle(notificationStyle)

    return this
  }

  private fun setupChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    Logger.d(TAG, "setupChannels() called")

    if (notificationManagerCompat.getNotificationChannel(REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID) == null) {
      Logger.d(TAG, "setupChannels() creating ${REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID} channel")

      // notification channel for replies summary
      val summaryChannel = NotificationChannel(
        REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID,
        REPLY_SUMMARY_NOTIFICATION_NAME,
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
      summaryChannel.lightColor = -0x6e1b9a

      notificationManagerCompat.createNotificationChannel(summaryChannel)
    }

    if (notificationManagerCompat.getNotificationChannel(REPLY_NOTIFICATION_CHANNEL_ID) == null) {
      Logger.d(TAG, "setupChannels() creating ${REPLY_NOTIFICATION_CHANNEL_ID} channel")

      // notification channel for replies
      val replyChannel = NotificationChannel(
        REPLY_NOTIFICATION_CHANNEL_ID,
        REPLY_NOTIFICATION_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_LOW
      )

      notificationManagerCompat.createNotificationChannel(replyChannel)
    }
  }

  private fun getOrCalculateNotificationId(threadDescriptor: ChanDescriptor.ThreadDescriptor): Int {
    val prevNotificationId = notificationIdMap[threadDescriptor]
    if (prevNotificationId != null) {
      return prevNotificationId
    }

    val newNotificationId = notificationIdCounter.incrementAndGet()
    notificationIdMap[threadDescriptor] = newNotificationId

    return newNotificationId
  }

  private fun getUniqueNotificationTag(threadDescriptor: ChanDescriptor.ThreadDescriptor): String {
    return threadDescriptor.serializeToString()
  }

  companion object {
    private const val TAG = "ReplyNotificationsHelper"
    private const val MAX_LINES_IN_NOTIFICATION = 5
    // Android limitations
    private const val MAX_VISIBLE_NOTIFICATIONS = 20

    private const val REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_reply_summary_notifications_channel"
    private const val REPLY_SUMMARY_NOTIFICATION_NAME = "Notification channel for replies summary"

    private const val REPLY_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_replies_notifications_channel"
    private const val REPLY_NOTIFICATION_CHANNEL_NAME = "Notification channel for replies (Yous)"

    private val SUMMARY_NOTIFICATION_TAG = "REPLIES_SUMMARY_NOTIFICATION_TAG_${getFlavorType().name}"

    private const val SUMMARY_NOTIFICATION_ID = 0

  }
}