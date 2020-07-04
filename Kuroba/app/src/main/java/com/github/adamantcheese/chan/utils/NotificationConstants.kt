package com.github.adamantcheese.chan.utils

import com.github.adamantcheese.chan.BuildConfig
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import java.util.concurrent.atomic.AtomicInteger

object NotificationConstants {
  const val MAX_LINES_IN_NOTIFICATION = 5
  // Android limitations
  const val MAX_VISIBLE_NOTIFICATIONS = 20

  val NOTIFICATION_THUMBNAIL_SIZE = AndroidUtils.dp(96f)

  object ReplyNotifications {
    val notificationIdCounter = AtomicInteger(1000)
    val notificationIdMap = mutableMapOf<ChanDescriptor.ThreadDescriptor, Int>()

    const val REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_reply_summary_notifications_channel"
    const val REPLY_SUMMARY_NOTIFICATION_NAME = "Notification channel for new replies summary"
    const val REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_reply_summary_silent_notifications_channel"
    const val REPLY_SUMMARY_SILENT_NOTIFICATION_NAME = "Notification channel for old replies summary"
    const val REPLY_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_replies_notifications_channel"
    const val REPLY_NOTIFICATION_CHANNEL_NAME = "Notification channel for replies (Yous)"

    val SUMMARY_NOTIFICATION_TAG = "REPLIES_SUMMARY_NOTIFICATION_TAG_${AndroidUtils.getFlavorType().name}"
    val REPLIES_PRE_OREO_NOTIFICATION_TAG = "REPLIES_PRE_OREO_NOTIFICATION_TAG_${AndroidUtils.getFlavorType().name}"

    const val SUMMARY_NOTIFICATION_ID = 0
    const val REPLIES_PRE_OREO_NOTIFICATION_ID = 1

    const val NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY = "notification_click_thread_descriptors"
    const val NOTIFICATION_CLICK_REQUEST_CODE = 1

    const val NOTIFICATION_SWIPE_THREAD_DESCRIPTORS_KEY = "notification_swipe_thread_descriptors"
    const val NOTIFICATION_SWIPE_REQUEST_CODE = 2
  }

}