package com.github.k1rakishou.chan.utils

import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
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
  const val FILTER_SUMMARY_NOTIFICATION_ID = 5

  private const val ReplyNotificationsIdCounterStart = 1_000_000
  private const val ImageSaverNotificationsIdCounterStart = 2_000_000
  private const val PostingServiceNotificationsIdCounterStart = 3_000_000
  private const val FilterWatcherNotificationsIdCounterStart = 4_000_000
  private const val GenericNotificationsIdCounterStart = 999_000_000

  const val REPLY_NOTIFICATION_ACTION = "${BuildConfig.APPLICATION_ID}_reply_notification_action"
  const val LAST_PAGE_NOTIFICATION_ACTION = "${BuildConfig.APPLICATION_ID}_last_page_notification_action"
  const val POSTING_NOTIFICATION_ACTION = "${BuildConfig.APPLICATION_ID}_posting_notification_action"
  const val FILTER_WATCHER_NOTIFICATION_ACTION = "${BuildConfig.APPLICATION_ID}_filter_watcher_notification_action"

  object ReplyNotifications {
    val notificationIdCounter = AtomicInteger(ReplyNotificationsIdCounterStart)
    val notificationIdMap = mutableMapOf<ChanDescriptor.ThreadDescriptor, Int>()

    fun notificationId(threadDescriptor: ChanDescriptor.ThreadDescriptor): Int {
      val prevNotificationId = notificationIdMap[threadDescriptor]
      if (prevNotificationId != null) {
        return prevNotificationId
      }

      val newNotificationId = notificationIdCounter.incrementAndGet()
      notificationIdMap[threadDescriptor] = newNotificationId

      return newNotificationId
    }

    const val NOTIFICATION_TAG_PREFIX = "reply_"

    const val REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_reply_summary_notifications_channel"
    const val REPLY_SUMMARY_NOTIFICATION_NAME = "Notification channel for new replies summary"
    const val REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_reply_summary_silent_notifications_channel"
    const val REPLY_SUMMARY_SILENT_NOTIFICATION_NAME = "Notification channel for new replies summary (silent)"
    const val REPLY_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_replies_notifications_channel"
    const val REPLY_NOTIFICATION_CHANNEL_NAME = "Notification channel for replies (Yous)"

    val SUMMARY_NOTIFICATION_TAG = "REPLIES_SUMMARY_NOTIFICATION_TAG_${AppModuleAndroidUtils.buildType.name}"
    val REPLIES_PRE_OREO_NOTIFICATION_TAG = "REPLIES_PRE_OREO_NOTIFICATION_TAG_${AppModuleAndroidUtils.buildType.name}"

    const val R_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY = "reply_notification_click_thread_descriptors"
    const val R_NOTIFICATION_CLICK_POST_DESCRIPTORS_KEY = "reply_notification_click_post_descriptors"
    const val R_NOTIFICATION_SWIPE_THREAD_DESCRIPTORS_KEY = "reply_notification_swipe_thread_descriptors"
  }

  object LastPageNotifications {
    const val LAST_PAGE_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_last_page_notifications_channel"
    const val LAST_PAGE_NOTIFICATION_NAME = "Notification channel for threads last pages alerts"
    const val LAST_PAGE_SILENT_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_last_page_silent_notifications_channel"
    const val LAST_PAGE_SILENT_NOTIFICATION_NAME = "Notification channel for threads last pages alerts (silent)"

    val LAST_PAGE_NOTIFICATION_TAG = "LAST_PAGE_NOTIFICATION_TAG_${AppModuleAndroidUtils.buildType.name}"

    const val LP_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY = "last_page_notification_click_thread_descriptors"
  }

  object ImageSaverNotifications {
    private val notificationIdCounter = AtomicInteger(ImageSaverNotificationsIdCounterStart)
    private val notificationIdMap = mutableMapOf<String, Int>()

    fun notificationId(uniqueDownloadId: String): Int {
      val prevNotificationId = notificationIdMap[uniqueDownloadId]
      if (prevNotificationId != null) {
        return prevNotificationId
      }

      val newNotificationId = notificationIdCounter.incrementAndGet()
      notificationIdMap[uniqueDownloadId] = newNotificationId

      return newNotificationId
    }

    const val IMAGE_SAVER_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_image_saver_notification_channel"
    const val IMAGE_SAVER_NOTIFICATION_NAME = "Notification channel for downloading images"
  }

  object PostingServiceNotifications {
    private val notificationIdCounter = AtomicInteger(PostingServiceNotificationsIdCounterStart)
    private val notificationIdMap = mutableMapOf<ChanDescriptor, Int>()

    fun notificationId(chanDescriptor: ChanDescriptor): Int {
      val prevNotificationId = notificationIdMap[chanDescriptor]
      if (prevNotificationId != null) {
        return prevNotificationId
      }

      val newNotificationId = notificationIdCounter.incrementAndGet()
      notificationIdMap[chanDescriptor] = newNotificationId

      return newNotificationId
    }

    const val MAIN_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_posting_service_main_notification_channel"
    const val MAIN_NOTIFICATION_NAME = "Notification channel for posting service"

    const val CHILD_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_posting_service_child_notification_channel"
    const val CHILD_NOTIFICATION_NAME = "Notification channel for posting updates"

    const val NOTIFICATION_CLICK_CHAN_DESCRIPTOR_KEY = "posting_service_child_notification_click_chan_descriptor"
  }

  object FilterWatcherNotifications {
    private val notificationIdCounter = AtomicInteger(FilterWatcherNotificationsIdCounterStart)
    private val notificationIdMap = mutableMapOf<String, Int>()

    fun notificationId(pattern: String): Int {
      val prevNotificationId = notificationIdMap[pattern]
      if (prevNotificationId != null) {
        return prevNotificationId
      }

      val newNotificationId = notificationIdCounter.incrementAndGet()
      notificationIdMap[pattern] = newNotificationId

      return newNotificationId
    }

    const val NOTIFICATION_TAG = "filter_watcher_notification"
    const val SUMMARY_NOTIFICATION_TAG = "filter_watcher_summary_notification"

    const val FW_SUMMARY_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_filter_watcher_summary_notifications_channel"
    const val FW_SUMMARY_NOTIFICATION_CHANNEL_NAME = "Notification channel for filter watcher summary"

    const val FW_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_filter_watcher_notifications_channel"
    const val FW_NOTIFICATION_CHANNEL_NAME = "Notification channel for filter watcher"

    const val FW_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY = "filter_watcher_notification_click_thread_descriptors"
  }

  object Generic {
    private val notificationIdCounter = AtomicInteger(GenericNotificationsIdCounterStart)
    private val notificationIdMap = mutableMapOf<String, Int>()

    enum class Ids {
      NewAppVersionAvailable,
      NewMpvListVersionAvailable,
    }

    fun notificationId(notificationId: String): Int {
      val prevNotificationId = notificationIdMap[notificationId]
      if (prevNotificationId != null) {
        return prevNotificationId
      }

      val newNotificationId = notificationIdCounter.incrementAndGet()
      notificationIdMap[notificationId] = newNotificationId

      return newNotificationId
    }

    const val CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_generic"
    const val CHANNEL_NAME = "Notification channel for generic app events"

    val TAG = "GENERIC_TAG_${AppModuleAndroidUtils.buildType.name}"
  }

}