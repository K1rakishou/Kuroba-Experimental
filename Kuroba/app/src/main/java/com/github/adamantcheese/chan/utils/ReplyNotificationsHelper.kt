package com.github.adamantcheese.chan.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.adamantcheese.chan.BuildConfig
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.base.SuspendDebouncer
import com.github.adamantcheese.chan.core.image.ImageLoaderV2
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.utils.AndroidUtils.*
import com.github.adamantcheese.model.data.bookmark.ThreadBookmarkReplyView
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.repository.ChanPostRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.reactive.asFlow
import org.joda.time.DateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class ReplyNotificationsHelper(
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val notificationManagerCompat: NotificationManagerCompat,
  private val notificationManager: NotificationManager,
  private val bookmarksManager: BookmarksManager,
  private val chanPostRepository: ChanPostRepository,
  private val imageLoaderV2: ImageLoaderV2
) {
  private val notificationIdCounter = AtomicInteger(1000)
  private val notificationIdMap = mutableMapOf<ChanDescriptor.ThreadDescriptor, Int>()
  private val debouncer = SuspendDebouncer(appScope)

  init {
    appScope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .asFlow()
        // We only care about bookmark updates here since we use this listener to close seen
        // notifications
        .filter { bookmarkChange ->
          return@filter bookmarkChange is BookmarksManager.BookmarkChange.BookmarksUpdated
            || bookmarkChange is BookmarksManager.BookmarkChange.BookmarksInitialized
        }
        .collect { bookmarkChange ->
          Logger.d(TAG, "bookmarksManager.listenForBookmarksChanges(), " +
            "bookmarkChange=${bookmarkChange.javaClass.simpleName}")
          showOrUpdateNotifications()
        }
    }
  }

  fun showOrUpdateNotifications() {
    debouncer.post(NOTIFICATIONS_UPDATE_DEBOUNCE_TIME) { showOrUpdateNotificationsInternal() }
  }

  private suspend fun showOrUpdateNotificationsInternal() {
    val unseenNotificationsGrouped = mutableMapOf<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>()

    bookmarksManager.mapBookmarksOrdered { threadBookmarkView ->
      val threadDescriptor = threadBookmarkView.threadDescriptor

      return@mapBookmarksOrdered threadBookmarkView.threadBookmarkReplyViews.forEach { (_, threadBookmarkReplyView) ->
        if (threadBookmarkReplyView.seen) {
          return@forEach
        }

        unseenNotificationsGrouped.putIfNotContains(threadDescriptor, mutableSetOf())
        unseenNotificationsGrouped[threadDescriptor]!!.add(threadBookmarkReplyView)
      }
    }

    val shownNotifications = showNotificationForReplies(unseenNotificationsGrouped)
    if (shownNotifications.isEmpty()) {
      return
    }

    // Mark all shown notifications as notified so we won't show them again
    bookmarksManager.updateBookmarks(
      shownNotifications.keys,
      BookmarksManager.NotifyListenersOption.NotifyDelayed
    ) { threadBookmark ->
      shownNotifications[threadBookmark.threadDescriptor]?.forEach { threadBookmarkReplyView ->
        threadBookmark.threadBookmarkReplies[threadBookmarkReplyView.postDescriptor]?.alreadyNotified = true
      }
    }
  }

  private suspend fun showNotificationForReplies(
    unseenNotificationsGrouped: MutableMap<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>
  ): Map<ChanDescriptor.ThreadDescriptor, Set<ThreadBookmarkReplyView>> {
    Logger.d(TAG, "showNotificationForReplies(${unseenNotificationsGrouped.size})")

    if (unseenNotificationsGrouped.isEmpty()) {
      // No bookmarks left, so close all notifications

      Logger.d(TAG, "showNotificationForReplies(${unseenNotificationsGrouped.size}) " +
        "unseenNotificationsGrouped is empty, closing all notifications")
      notificationManagerCompat.cancelAll()
      return emptyMap()
    }

    val notificationsGroup = "${BuildConfig.APPLICATION_ID}_${getFlavorType().name}"

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return showNotificationsForAndroidNougatAndBelow(
        notificationsGroup,
        unseenNotificationsGrouped
      )
    }

    setupChannels()
    restoreNotificationIdMap(unseenNotificationsGrouped)

    val sortedUnseenNotificationsGrouped = sortNotifications(unseenNotificationsGrouped)

    val notificationTime = sortedUnseenNotificationsGrouped.values.flatten()
      .maxBy { threadBookmarkReply -> threadBookmarkReply.time }
      ?.time
      ?: DateTime.now()

    showSummaryNotification(
      notificationsGroup,
      notificationTime,
      sortedUnseenNotificationsGrouped
    )

    val shownNotifications = showNotificationsForAndroidOreoAndAbove(
      notificationsGroup,
      notificationTime,
      sortedUnseenNotificationsGrouped
    )

    // TODO(KurobaEx): delete me
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      notificationManager.activeNotifications.forEach { notification ->
        Logger.d(TAG, "active notification, id: ${notification.id}, " +
          "group=${notification.isGroup}, " +
          "groupKey=${notification.notification.group}, " +
          "behavior=${notification.notification.groupAlertBehavior}")
      }
    }

    return shownNotifications
  }

  private fun sortNotifications(
    unseenNotificationsGrouped: Map<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>
  ): Map<ChanDescriptor.ThreadDescriptor, List<ThreadBookmarkReplyView>> {
    val sortedNotifications = mutableMapOf<ChanDescriptor.ThreadDescriptor, MutableList<ThreadBookmarkReplyView>>()

    unseenNotificationsGrouped.forEach { (threadDescriptor, replies) ->
      if (replies.isEmpty()) {
        return@forEach
      }

      sortedNotifications.putIfNotContains(threadDescriptor, ArrayList(replies.size))
      sortedNotifications[threadDescriptor]!!.addAll(replies.sortedWith(REPLIES_COMPARATOR))
    }

    return sortedNotifications
  }

  @RequiresApi(Build.VERSION_CODES.M)
  private fun restoreNotificationIdMap(
    unseenNotificationsGrouped: MutableMap<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>
  ) {
    require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      "This method cannot be called on pre-marshmallow devices!"
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
    notificationsGroup: String,
    unseenNotificationsGrouped: MutableMap<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>
  ): Map<ChanDescriptor.ThreadDescriptor, Set<ThreadBookmarkReplyView>> {
    val threadsWithUnseenRepliesCount = unseenNotificationsGrouped.size
    val totalUnseenRepliesCount = unseenNotificationsGrouped.values.sumBy { replies -> replies.size }
    // TODO(KurobaEx): strings
    val titleText = "You have $totalUnseenRepliesCount replies in $threadsWithUnseenRepliesCount thread(s)"

    val unseenRepliesCount = unseenNotificationsGrouped.values
      .flatten()
      .count { threadBookmarkReplyView -> !threadBookmarkReplyView.seen }
    val hasUnseenReplies = unseenRepliesCount > 0

    val newRepliesCount = unseenNotificationsGrouped.values
      .flatten()
      .count { threadBookmarkReplyView -> !threadBookmarkReplyView.notified }
    val hasNewReplies = newRepliesCount > 0

    Logger.d(TAG, "showNotificationsForAndroidNougatAndBelow() " +
      "unseenNotificationsGrouped = ${unseenNotificationsGrouped.size}, " +
      "unseenRepliesCount=$unseenRepliesCount, newRepliesCount=$newRepliesCount")

    val iconId = if (hasUnseenReplies) {
      Logger.d(TAG, "showNotificationsForAndroidNougatAndBelow() Using R.drawable.ic_stat_notify_alert icon")
      R.drawable.ic_stat_notify_alert
    } else {
      Logger.d(TAG, "showNotificationsForAndroidNougatAndBelow() Using R.drawable.ic_stat_notify icon")
      R.drawable.ic_stat_notify
    }

    val notificationPriority = if (hasNewReplies) {
      Logger.d(TAG, "showNotificationsForAndroidNougatAndBelow() Using NotificationCompat.PRIORITY_MAX")
      NotificationCompat.PRIORITY_MAX
    } else {
      Logger.d(TAG, "showNotificationsForAndroidNougatAndBelow() Using NotificationCompat.PRIORITY_LOW")
      NotificationCompat.PRIORITY_LOW
    }

    val unseenThreadBookmarkReplies = unseenNotificationsGrouped
      .flatMap { (_, replies) -> replies }
      .filter { threadBookmarkReplyView -> !threadBookmarkReplyView.seen }
      .sortedWith(REPLIES_COMPARATOR)
      .takeLast(MAX_LINES_IN_NOTIFICATION)

    if (unseenThreadBookmarkReplies.isEmpty()) {
      notificationManagerCompat.cancel(
        REPLIES_PRE_OREO_NOTIFICATION_TAG,
        REPLIES_PRE_OREO_NOTIFICATION_ID
      )

      Logger.d(TAG, "showNotificationsForAndroidNougatAndBelow() notification closed")
    } else {
      val notificationTime = unseenThreadBookmarkReplies
        .maxBy { threadBookmarkReply -> threadBookmarkReply.time }
        ?.time
        ?: DateTime.now()

      val preOreoNotificationBuilder = NotificationCompat.Builder(appContext)
        .setWhen(notificationTime.millis)
        .setShowWhen(true)
        .setContentTitle(getApplicationLabel())
        .setContentText(titleText)
        .setSmallIcon(iconId)
        // TODO(KurobaEx):
//      .setContentIntent(TODO)
        .setAutoCancel(true)
        .setAllowSystemGeneratedContextualActions(false)
        .setPriority(notificationPriority)
        .setupSoundAndVibration(hasNewReplies)
        .setupReplyNotificationsStyle(titleText, unseenThreadBookmarkReplies)
        .setGroup(notificationsGroup)
        .setGroupSummary(true)

      notificationManagerCompat.notify(
        REPLIES_PRE_OREO_NOTIFICATION_TAG,
        REPLIES_PRE_OREO_NOTIFICATION_ID,
        preOreoNotificationBuilder.build()
      )

      Logger.d(TAG, "showNotificationsForAndroidNougatAndBelow() notification updated")
    }

    return unseenNotificationsGrouped
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun showSummaryNotification(
    notificationsGroup: String,
    notificationTime: DateTime,
    unseenNotificationsGrouped: Map<ChanDescriptor.ThreadDescriptor, List<ThreadBookmarkReplyView>>
  ) {
    val unseenRepliesCount = unseenNotificationsGrouped.values
      .flatten()
      .count { threadBookmarkReplyView -> !threadBookmarkReplyView.seen }
    val hasUnseenReplies = unseenRepliesCount > 0

    val newRepliesCount = unseenNotificationsGrouped.values
      .flatten()
      .count { threadBookmarkReplyView -> !threadBookmarkReplyView.notified }
    val hasNewReplies = newRepliesCount > 0

    Logger.d(TAG, "showSummaryNotification() " +
      "unseenNotificationsGrouped = ${unseenNotificationsGrouped.size}, " +
      "unseenRepliesCount=$unseenRepliesCount, newRepliesCount=$newRepliesCount")

    val iconId = if (hasUnseenReplies) {
      Logger.d(TAG, "showSummaryNotification() Using R.drawable.ic_stat_notify_alert icon")
      R.drawable.ic_stat_notify_alert
    } else {
      Logger.d(TAG, "showSummaryNotification() Using R.drawable.ic_stat_notify icon")
      R.drawable.ic_stat_notify
    }

    val summaryNotificationBuilder = if (hasNewReplies) {
      Logger.d(TAG, "showSummaryNotification() Using REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID")
      NotificationCompat.Builder(appContext, REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID)
    } else {
      Logger.d(TAG, "showSummaryNotification() Using REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID")
      NotificationCompat.Builder(appContext, REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID)
    }

    val threadsWithUnseenRepliesCount = unseenNotificationsGrouped.size
    val totalUnseenRepliesCount = unseenNotificationsGrouped.values.sumBy { replies -> replies.size }
    // TODO(KurobaEx): strings
    val titleText = "You have $totalUnseenRepliesCount replies in $threadsWithUnseenRepliesCount thread(s)"

    check(threadsWithUnseenRepliesCount > 0) { "Bad threadsWithUnseenRepliesCount" }
    check(totalUnseenRepliesCount > 0) { "Bad totalUnseenRepliesCount" }

    summaryNotificationBuilder
      .setWhen(notificationTime.millis)
      .setShowWhen(true)
      .setContentTitle(getApplicationLabel())
      .setContentText(titleText)
      .setSmallIcon(iconId)
      .setupSummaryNotificationsStyle(titleText)
      .setAllowSystemGeneratedContextualActions(false)
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
  private suspend fun showNotificationsForAndroidOreoAndAbove(
    notificationsGroup: String,
    notificationTime: DateTime,
    unseenNotificationsGrouped: Map<ChanDescriptor.ThreadDescriptor, List<ThreadBookmarkReplyView>>
  ): Map<ChanDescriptor.ThreadDescriptor, Set<ThreadBookmarkReplyView>> {
    Logger.d(TAG, "showNotificationsForAndroidOreoAndAbove() called")

    val shownNotifications = mutableMapOf<ChanDescriptor.ThreadDescriptor, HashSet<ThreadBookmarkReplyView>>()
    var notificationCounter = 0

    val originalPosts = getOriginalPostsForNotifications(unseenNotificationsGrouped.keys)
    Logger.d(TAG, "Loaded ${originalPosts.size} original posts")

    val thumbnailBitmaps = getThreadThumbnails(originalPosts)
    Logger.d(TAG, "Loaded ${thumbnailBitmaps.size} thumbnail bitmaps")

    for ((threadDescriptor, threadBookmarkReplies) in unseenNotificationsGrouped) {
      val threadTitle = getThreadTitle(originalPosts, threadDescriptor)

      // TODO(KurobaEx): strings
      val titleText = "You have ${threadBookmarkReplies.size} new replies in thread ${threadDescriptor.threadNo}"
      val notificationTag = getUniqueNotificationTag(threadDescriptor)
      val notificationId = getOrCalculateNotificationId(threadDescriptor)
      val hasUnseenReplies = threadBookmarkReplies.any { threadBookmarkReplyView -> !threadBookmarkReplyView.seen }

      if (!hasUnseenReplies) {
        notificationManagerCompat.cancel(
          notificationTag,
          notificationId
        )

        continue
      }

      val notificationBuilder = NotificationCompat.Builder(appContext, REPLY_NOTIFICATION_CHANNEL_ID)
        .setContentTitle(titleText)
        .setWhen(notificationTime.millis)
        .setShowWhen(true)
        .setupLargeIcon(thumbnailBitmaps[threadDescriptor])
        // TODO(KurobaEx):
        //        .setContentIntent(TODO)
        .setSmallIcon(R.drawable.ic_stat_notify_alert)
        .setAutoCancel(true)
        .setupReplyNotificationsStyle(threadTitle, threadBookmarkReplies)
        .setAllowSystemGeneratedContextualActions(false)
        .setCategory(Notification.CATEGORY_MESSAGE)
        .setGroup(notificationsGroup)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)

      notificationManagerCompat.notify(
        notificationTag,
        notificationId,
        notificationBuilder.build()
      )

      Logger.d(TAG, "showNotificationsForAndroidOreoAndAbove() created notification " +
        "with tag ${notificationTag}, counter=${notificationCounter}")

      ++notificationCounter

      shownNotifications.putIfNotContains(threadDescriptor, hashSetOf())
      shownNotifications[threadDescriptor]!!.addAll(threadBookmarkReplies)

      if (notificationCounter > MAX_VISIBLE_NOTIFICATIONS) {
        break
      }
    }

    return shownNotifications
  }

  private suspend fun getThreadThumbnails(
    originalPosts: Map<ChanDescriptor.ThreadDescriptor, ChanPost>
  ): Map<ChanDescriptor.ThreadDescriptor, BitmapDrawable> {
    val resultMap = mutableMapOf<ChanDescriptor.ThreadDescriptor, BitmapDrawable>()

    supervisorScope {
      originalPosts.entries
        .chunked(MAX_THUMBNAIL_REQUESTS_PER_BATCH)
        .forEach { chunk ->
          val results = chunk.mapNotNull { (threadDescriptor, originalPost) ->
            val thumbnailUrl = originalPost.postImages.firstOrNull()?.thumbnailUrl
              ?: return@mapNotNull null

            return@mapNotNull appScope.async {
              return@async suspendCancellableCoroutine<Pair<ChanDescriptor.ThreadDescriptor, BitmapDrawable>?> { cancellableContinuation ->
                imageLoaderV2.loadFromNetwork(
                  appContext,
                  thumbnailUrl.toString(),
                  NOTIFICATION_THUMBNAIL_SIZE,
                  NOTIFICATION_THUMBNAIL_SIZE,
                  object : ImageLoaderV2.ImageListener {
                    override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
                      cancellableContinuation.resume(threadDescriptor to drawable)
                    }

                    override fun onNotFound() {
                      cancellableContinuation.resume(null)
                    }

                    override fun onResponseError(error: Throwable) {
                      Logger.e(TAG, "Error while trying to load thumbnail for notification image, " +
                        "error: ${error.errorMessageOrClassName()}")

                      cancellableContinuation.resume(null)
                    }
                  }
                )
              }
            }
          }
            .awaitAll()
            .filterNotNull()

          results.forEach { (threadDescriptor, bitmapDrawable) ->
            resultMap[threadDescriptor] = bitmapDrawable
          }
        }
    }

    return resultMap
  }

  private fun getThreadTitle(
    originalPosts: Map<ChanDescriptor.ThreadDescriptor, ChanPost>,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): String {
    var title = originalPosts[threadDescriptor]?.subject?.text

    if (title.isNullOrBlank()) {
      title = originalPosts[threadDescriptor]?.postComment?.text
    }

    if (title.isNullOrBlank()) {
      return threadDescriptor.threadNo.toString()
    }

    return title.ellipsizeEnd(MAX_THREAD_TITLE_LENGTH)
  }

  private suspend fun getOriginalPostsForNotifications(
    threadDescriptors: Set<ChanDescriptor.ThreadDescriptor>
  ): Map<ChanDescriptor.ThreadDescriptor, ChanPost> {
    return chanPostRepository.getCatalogOriginalPosts(
      threadDescriptors,
      ArchiveDescriptor.NO_ARCHIVE_ID
    ).mapErrorToValue { error ->
      Logger.e(TAG, "chanPostRepository.getCatalogOriginalPosts() failed", error)
      return@mapErrorToValue emptyMap<ChanDescriptor.ThreadDescriptor, ChanPost>()
    }
  }

  private fun NotificationCompat.Builder.setupLargeIcon(bitmapDrawable: BitmapDrawable?): NotificationCompat.Builder {
    if (bitmapDrawable != null) {
      setLargeIcon(bitmapDrawable.bitmap)
    }

    return this
  }

  private fun NotificationCompat.Builder.setupSoundAndVibration(hasNewReplies: Boolean): NotificationCompat.Builder {
    if (hasNewReplies) {
      Logger.d(TAG, "Using sound and vibration (For Nougat and below)")
      setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
      setLights(appContext.resources.getColor(R.color.accent), 1000, 1000)
    }

    return this
  }

  private fun NotificationCompat.Builder.setupSummaryNotificationsStyle(summaryText: String): NotificationCompat.Builder {
    setStyle(
      NotificationCompat.InboxStyle(this)
        .setSummaryText(summaryText)
    )

    return this
  }

  private fun NotificationCompat.Builder.setupReplyNotificationsStyle(
    titleText: String,
    threadBookmarkReplyViewSet: Collection<ThreadBookmarkReplyView>
  ): NotificationCompat.Builder {
    val notificationStyle = NotificationCompat.InboxStyle(this)
      .setSummaryText(titleText)

    val repliesSorted = threadBookmarkReplyViewSet
      .sortedWith(REPLIES_COMPARATOR)
      .takeLast(MAX_LINES_IN_NOTIFICATION)

    repliesSorted.forEach { reply ->
      // TODO(KurobaEx): strings
      notificationStyle.addLine("Reply from ${reply.postDescriptor.postNo} to post ${reply.repliesTo.postNo}")
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
      summaryChannel.lightColor = appContext.resources.getColor(R.color.accent)

      notificationManagerCompat.createNotificationChannel(summaryChannel)
    }

    if (notificationManagerCompat.getNotificationChannel(REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID) == null) {
      Logger.d(TAG, "setupChannels() creating ${REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID} channel")

      // notification channel for replies summary
      val summaryChannel = NotificationChannel(
        REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID,
        REPLY_SUMMARY_SILENT_NOTIFICATION_NAME,
        NotificationManager.IMPORTANCE_LOW
      )

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
    private const val REPLY_SUMMARY_NOTIFICATION_NAME = "Notification channel for new replies summary"
    private const val REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_reply_summary_silent_notifications_channel"
    private const val REPLY_SUMMARY_SILENT_NOTIFICATION_NAME = "Notification channel for old replies summary"
    private const val REPLY_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_replies_notifications_channel"
    private const val REPLY_NOTIFICATION_CHANNEL_NAME = "Notification channel for replies (Yous)"

    private val SUMMARY_NOTIFICATION_TAG = "REPLIES_SUMMARY_NOTIFICATION_TAG_${getFlavorType().name}"
    private val REPLIES_PRE_OREO_NOTIFICATION_TAG = "REPLIES_PRE_OREO_NOTIFICATION_TAG_${getFlavorType().name}"

    private const val SUMMARY_NOTIFICATION_ID = 0
    private const val REPLIES_PRE_OREO_NOTIFICATION_ID = 1

    private const val NOTIFICATIONS_UPDATE_DEBOUNCE_TIME = 1000L
    private const val MAX_THREAD_TITLE_LENGTH = 50
    private const val MAX_THUMBNAIL_REQUESTS_PER_BATCH = 8
    private val NOTIFICATION_THUMBNAIL_SIZE = dp(96f)

    private val REPLIES_COMPARATOR = Comparator<ThreadBookmarkReplyView> { o1, o2 ->
      o1.postDescriptor.postNo.compareTo(o2.postDescriptor.postNo)
    }
  }
}
