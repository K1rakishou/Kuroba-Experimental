package com.github.k1rakishou.chan.utils

import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getFlavorType
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.util.concurrent.atomic.AtomicInteger

object NotificationConstants {
  const val MAX_LINES_IN_NOTIFICATION = 5
  // Android limitations
  const val MAX_VISIBLE_NOTIFICATIONS = 20

  val NOTIFICATION_THUMBNAIL_SIZE = dp(96f)

  const val REPLIES_SUMMARY_NOTIFICATION_ID = 0
  const val REPLIES_PRE_OREO_NOTIFICATION_ID = 1
  const val LAST_PAGE_NOTIFICATION_ID = 2
  const val IMAGE_SAVER_WORKER_NOTIFICATION_ID = 3
  const val POSTING_SERVICE_NOTIFICATION_ID = 4

  const val REPLY_NOTIFICATION_ACTION = "${BuildConfig.APPLICATION_ID}_reply_notification_action"
  const val LAST_PAGE_NOTIFICATION_ACTION = "${BuildConfig.APPLICATION_ID}_last_page_notification_action"

  object ReplyNotifications {
    val notificationIdCounter = AtomicInteger(100000)
    val notificationIdMap = mutableMapOf<ChanDescriptor.ThreadDescriptor, Int>()

    const val NOTIFICATION_TAG_PREFIX = "reply_"

    const val REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_reply_summary_notifications_channel"
    const val REPLY_SUMMARY_NOTIFICATION_NAME = "Notification channel for new replies summary"
    const val REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_reply_summary_silent_notifications_channel"
    const val REPLY_SUMMARY_SILENT_NOTIFICATION_NAME = "Notification channel for new replies summary (silent)"
    const val REPLY_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_replies_notifications_channel"
    const val REPLY_NOTIFICATION_CHANNEL_NAME = "Notification channel for replies (Yous)"

    val SUMMARY_NOTIFICATION_TAG = "REPLIES_SUMMARY_NOTIFICATION_TAG_${getFlavorType().name}"
    val REPLIES_PRE_OREO_NOTIFICATION_TAG = "REPLIES_PRE_OREO_NOTIFICATION_TAG_${getFlavorType().name}"

    const val R_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY = "reply_notification_click_thread_descriptors"
    const val R_NOTIFICATION_SWIPE_THREAD_DESCRIPTORS_KEY = "reply_notification_swipe_thread_descriptors"
  }

  object LastPageNotifications {
    const val LAST_PAGE_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_last_page_notifications_channel"
    const val LAST_PAGE_NOTIFICATION_NAME = "Notification channel for threads last pages alerts"
    const val LAST_PAGE_SILENT_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_last_page_silent_notifications_channel"
    const val LAST_PAGE_SILENT_NOTIFICATION_NAME = "Notification channel for threads last pages alerts (silent)"

    val LAST_PAGE_NOTIFICATION_TAG = "LAST_PAGE_NOTIFICATION_TAG_${getFlavorType().name}"

    const val LP_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY = "last_page_notification_click_thread_descriptors"
  }

  object ImageSaverNotifications {
    const val IMAGE_SAVER_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_image_saver_notification_channel"
    const val IMAGE_SAVER_NOTIFICATION_NAME = "Notification channel for downloading images"
  }

  object PostingServiceNotifications {
    const val MAIN_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_posting_service_main_notification_channel"
    const val MAIN_NOTIFICATION_NAME = "Notification channel for posting service"

    const val CHILD_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_posting_service_child_notification_channel"
    const val CHILD_NOTIFICATION_NAME = "Notification channel for posting updates"

    const val NOTIFICATION_CLICK_CHAN_DESCRIPTOR_KEY = "posting_service_child_notification_click_chan_descriptor"
  }

}