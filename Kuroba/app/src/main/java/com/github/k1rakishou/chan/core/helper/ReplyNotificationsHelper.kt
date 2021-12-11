package com.github.k1rakishou.chan.core.helper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil.transform.CircleCropTransformation
import coil.transform.Transformation
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.receiver.ReplyNotificationDeleteIntentBroadcastReceiver
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getFlavorType
import com.github.k1rakishou.chan.utils.NotificationConstants
import com.github.k1rakishou.chan.utils.NotificationConstants.MAX_LINES_IN_NOTIFICATION
import com.github.k1rakishou.chan.utils.NotificationConstants.MAX_VISIBLE_NOTIFICATIONS
import com.github.k1rakishou.chan.utils.NotificationConstants.NOTIFICATION_THUMBNAIL_SIZE
import com.github.k1rakishou.chan.utils.RequestCodes
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AndroidUtils.getApplicationLabel
import com.github.k1rakishou.common.ellipsizeEnd
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.processDataCollectionConcurrently
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkReplyView
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptorParcelable
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.repository.ChanPostRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.joda.time.DateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class ReplyNotificationsHelper(
  private val isDevFlavor: Boolean,
  private val verboseLogsEnabled: Boolean,
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val notificationManagerCompat: NotificationManagerCompat,
  private val notificationManager: NotificationManager,
  private val _bookmarksManager: Lazy<BookmarksManager>,
  private val _chanPostRepository: Lazy<ChanPostRepository>,
  private val _imageLoaderV2: Lazy<ImageLoaderV2>,
  private val _themeEngine: Lazy<ThemeEngine>,
  private val _simpleCommentParser: Lazy<SimpleCommentParser>
) {
  private val debouncer = DebouncingCoroutineExecutor(appScope)
  private val working = AtomicBoolean(false)

  val bookmarksManager: BookmarksManager
    get() = _bookmarksManager.get()
  val chanPostRepository: ChanPostRepository
    get() = _chanPostRepository.get()
  val imageLoaderV2: ImageLoaderV2
    get() = _imageLoaderV2.get()
  val themeEngine: ThemeEngine
    get() = _themeEngine.get()
  val simpleCommentParser: SimpleCommentParser
    get() = _simpleCommentParser.get()

  init {
    appScope.launch {
      bookmarksManager.awaitUntilInitialized()

      bookmarksManager.listenForBookmarksChanges()
        .filter { bookmarkChange ->
          return@filter bookmarkChange is BookmarksManager.BookmarkChange.BookmarksUpdated
            || bookmarkChange is BookmarksManager.BookmarkChange.BookmarksInitialized
            || bookmarkChange is BookmarksManager.BookmarkChange.BookmarksDeleted
        }
        .collect { showOrUpdateNotifications() }
    }
  }

  fun showOrUpdateNotifications() {
    debouncer.post(NOTIFICATIONS_UPDATE_DEBOUNCE_TIME) {
      if (!working.compareAndSet(false, true)) {
        return@post
      }

      try {
        showOrUpdateNotificationsInternal()
      } finally {
        working.set(false)
      }
    }
  }

  private suspend fun showOrUpdateNotificationsInternal() {
    if (!ChanSettings.replyNotifications.get()) {
      Logger.d(TAG, "showOrUpdateNotificationsInternal() ChanSettings.replyNotifications == false")
      return
    }

    chanPostRepository.awaitUntilInitialized()
    bookmarksManager.awaitUntilInitialized()

    val unreadNotificationsGrouped =
      mutableMapOf<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>()

    bookmarksManager.mapAllBookmarks { threadBookmarkView ->
      val threadDescriptor = threadBookmarkView.threadDescriptor

      return@mapAllBookmarks threadBookmarkView.threadBookmarkReplyViews.forEach { (_, threadBookmarkReplyView) ->
        if (threadBookmarkReplyView.alreadyRead) {
          // Skip already read replies
          return@forEach
        }

        unreadNotificationsGrouped.putIfNotContains(threadDescriptor, mutableSetOf())
        unreadNotificationsGrouped[threadDescriptor]!!.add(threadBookmarkReplyView)
      }
    }

    val shownNotifications = showNotificationForReplies(unreadNotificationsGrouped)
    if (shownNotifications.isEmpty()) {
      return
    }

    val threadDescriptors = shownNotifications.keys

    // Mark all shown notifications as notified so we won't show them again
    val updatedBookmarkDescriptors = bookmarksManager.updateBookmarksNoPersist(threadDescriptors) { threadBookmark ->
      shownNotifications[threadBookmark.threadDescriptor]?.forEach { threadBookmarkReplyView ->
        threadBookmark.threadBookmarkReplies[threadBookmarkReplyView.postDescriptor]?.alreadyNotified = true
      }
    }

    bookmarksManager.persistBookmarksManually(updatedBookmarkDescriptors)
  }

  private suspend fun showNotificationForReplies(
    unreadNotificationsGrouped: MutableMap<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>
  ): Map<ChanDescriptor.ThreadDescriptor, Set<ThreadBookmarkReplyView>> {
    Logger.d(TAG, "showNotificationForReplies(${unreadNotificationsGrouped.size})")

    if (unreadNotificationsGrouped.isEmpty()) {
      Logger.d(TAG, "showNotificationForReplies() unreadNotificationsGrouped are empty")

      closeAllNotifications()
      return emptyMap()
    }

    if (!AndroidUtils.isAndroidO()) {
      return showNotificationsForAndroidNougatAndBelow(
        unreadNotificationsGrouped
      )
    }

    setupChannels()
    restoreNotificationIdMap(unreadNotificationsGrouped)

    val sortedUnreadNotificationsGrouped = sortNotifications(unreadNotificationsGrouped)
    val notificationTime = sortedUnreadNotificationsGrouped.values.flatten()
      .maxByOrNull { threadBookmarkReply -> threadBookmarkReply.time }
      ?.time
      ?: DateTime.now()

    val hasUnseenReplies = showSummaryNotification(
      notificationTime,
      sortedUnreadNotificationsGrouped
    )

    if (!hasUnseenReplies) {
      Logger.d(TAG, "showNotificationForReplies() showSummaryNotification() hasUnseenReplies==false")
      return emptyMap()
    }

    val shownNotifications = showNotificationsForAndroidOreoAndAbove(
      notificationTime,
      sortedUnreadNotificationsGrouped
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && verboseLogsEnabled) {
      notificationManager.activeNotifications.forEach { notification ->
        Logger.d(
          TAG, "active notification, id: ${notification.id}, " +
          "isGroup=${notification.isGroup}, " +
          "group=${notification.notification.group}, " +
          "groupAlertBehavior=${notification.notification.groupAlertBehavior}")
      }
    }

    return shownNotifications
  }

  private fun sortNotifications(
    unreadNotificationsGrouped: Map<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>
  ): Map<ChanDescriptor.ThreadDescriptor, List<ThreadBookmarkReplyView>> {
    val sortedNotifications = mutableMapOf<ChanDescriptor.ThreadDescriptor, MutableList<ThreadBookmarkReplyView>>()

    unreadNotificationsGrouped.forEach { (threadDescriptor, replies) ->
      if (replies.isEmpty()) {
        return@forEach
      }

      sortedNotifications.putIfNotContains(threadDescriptor, ArrayList(replies.size))
      sortedNotifications[threadDescriptor]!!.addAll(replies.sortedWith(REPLIES_COMPARATOR))
    }

    return sortedNotifications
  }

  private suspend fun showNotificationsForAndroidNougatAndBelow(
    unreadNotificationsGrouped: MutableMap<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>
  ): Map<ChanDescriptor.ThreadDescriptor, Set<ThreadBookmarkReplyView>> {
    val threadsWithUnseenRepliesCount = unreadNotificationsGrouped.size
    val totalUnseenRepliesCount = unreadNotificationsGrouped.values.sumBy { replies -> replies.size }

    val titleText = appContext.resources.getString(
      R.string.reply_notifications_new_replies_total_stats,
      totalUnseenRepliesCount,
      threadsWithUnseenRepliesCount
    )

    val unseenRepliesCount = unreadNotificationsGrouped.values
      .flatten()
      .count { threadBookmarkReplyView -> !threadBookmarkReplyView.alreadySeen }
    val hasUnseenReplies = unseenRepliesCount > 0

    val newRepliesCount = unreadNotificationsGrouped.values
      .flatten()
      .count { threadBookmarkReplyView -> !threadBookmarkReplyView.alreadyNotified }
    val hasNewReplies = newRepliesCount > 0

    val useSoundForReplyNotifications = ChanSettings.useSoundForReplyNotifications.get()

    Logger.d(
      TAG, "showNotificationsForAndroidNougatAndBelow() " +
      "useSoundForReplyNotifications=$useSoundForReplyNotifications, " +
      "unreadNotificationsGrouped = ${unreadNotificationsGrouped.size}, " +
      "unseenRepliesCount=$unseenRepliesCount, newRepliesCount=$newRepliesCount")

    val iconId = if (hasUnseenReplies) {
      Logger.d(TAG, "showNotificationsForAndroidNougatAndBelow() Using R.drawable.ic_stat_notify_alert icon")
      R.drawable.ic_stat_notify_alert
    } else {
      Logger.d(TAG, "showNotificationsForAndroidNougatAndBelow() Using R.drawable.ic_stat_notify icon")
      R.drawable.ic_stat_notify
    }

    val notificationPriority = if (hasNewReplies && useSoundForReplyNotifications) {
      Logger.d(TAG, "showNotificationsForAndroidNougatAndBelow() Using NotificationCompat.PRIORITY_MAX")
      NotificationCompat.PRIORITY_MAX
    } else {
      Logger.d(TAG, "showNotificationsForAndroidNougatAndBelow() Using NotificationCompat.PRIORITY_LOW")
      NotificationCompat.PRIORITY_LOW
    }

    val unseenThreadBookmarkReplies = unreadNotificationsGrouped
      .flatMap { (_, replies) -> replies }
      .filter { threadBookmarkReplyView -> !threadBookmarkReplyView.alreadySeen }
      .sortedWith(REPLIES_COMPARATOR)
      .takeLast(MAX_LINES_IN_NOTIFICATION)

    if (unseenThreadBookmarkReplies.isEmpty()) {
      notificationManagerCompat.cancel(
        NotificationConstants.ReplyNotifications.REPLIES_PRE_OREO_NOTIFICATION_TAG,
        NotificationConstants.REPLIES_PRE_OREO_NOTIFICATION_ID
      )

      Logger.d(
        TAG, "showNotificationsForAndroidNougatAndBelow() " +
        "unseenThreadBookmarkReplies is empty, notification closed")
      return unreadNotificationsGrouped
    }

    val notificationTime = unseenThreadBookmarkReplies
      .maxByOrNull { threadBookmarkReply -> threadBookmarkReply.time }
      ?.time
      ?: DateTime.now()

    val preOreoNotificationBuilder = NotificationCompat.Builder(appContext)
      .setWhen(notificationTime.millis)
      .setShowWhen(true)
      .setContentTitle(getApplicationLabel())
      .setContentText(titleText)
      .setSmallIcon(iconId)
      .setupClickOnNotificationIntent(
        requestCode = RequestCodes.nextRequestCode(),
        threadDescriptors = unreadNotificationsGrouped.keys,
        postDescriptors = unreadNotificationsGrouped.values.flatMap { threadBookmarkViewSet ->
          threadBookmarkViewSet.map { threadBookmarkReplyView -> threadBookmarkReplyView.postDescriptor }
        }
      )
      .setupDeleteNotificationIntent(unreadNotificationsGrouped.keys)
      .setAutoCancel(true)
      .setAllowSystemGeneratedContextualActions(false)
      .setPriority(notificationPriority)
      .setupSoundAndVibration(hasNewReplies, useSoundForReplyNotifications)
      .setupReplyNotificationsStyle(titleText, unseenThreadBookmarkReplies)
      .setGroup(notificationsGroup)
      .setGroupSummary(true)

    notificationManagerCompat.notify(
      NotificationConstants.ReplyNotifications.REPLIES_PRE_OREO_NOTIFICATION_TAG,
      NotificationConstants.REPLIES_PRE_OREO_NOTIFICATION_ID,
      preOreoNotificationBuilder.build()
    )

    Logger.d(TAG, "showNotificationsForAndroidNougatAndBelow() notificationManagerCompat.notify() called")
    return unreadNotificationsGrouped
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun showSummaryNotification(
    notificationTime: DateTime,
    unreadNotificationsGrouped: Map<ChanDescriptor.ThreadDescriptor, List<ThreadBookmarkReplyView>>
  ): Boolean {
    val unseenRepliesCount = unreadNotificationsGrouped.values
      .flatten()
      .count { threadBookmarkReplyView -> !threadBookmarkReplyView.alreadySeen }
    val hasUnseenReplies = unseenRepliesCount > 0

    if (!hasUnseenReplies) {
      Logger.d(TAG, "showSummaryNotification() no unseen replies left after filtering")
      return false
    }

    val newRepliesCount = unreadNotificationsGrouped.values
      .flatten()
      .count { threadBookmarkReplyView -> !threadBookmarkReplyView.alreadyNotified }
    val hasNewReplies = newRepliesCount > 0
    val useSoundForReplyNotifications = ChanSettings.useSoundForReplyNotifications.get()

    Logger.d(
      TAG, "showSummaryNotification() " +
      "useSoundForReplyNotifications=$useSoundForReplyNotifications, " +
      "unreadNotificationsGrouped = ${unreadNotificationsGrouped.size}, " +
      "unseenRepliesCount=$unseenRepliesCount, newRepliesCount=$newRepliesCount")

    val iconId = if (hasUnseenReplies) {
      Logger.d(TAG, "showSummaryNotification() Using R.drawable.ic_stat_notify_alert icon")
      R.drawable.ic_stat_notify_alert
    } else {
      Logger.d(TAG, "showSummaryNotification() Using R.drawable.ic_stat_notify icon")
      R.drawable.ic_stat_notify
    }

    val summaryNotificationBuilder = if (hasNewReplies && useSoundForReplyNotifications) {
      Logger.d(TAG, "showSummaryNotification() Using REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID")
      NotificationCompat.Builder(
        appContext,
        NotificationConstants.ReplyNotifications.REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID
      )
    } else {
      Logger.d(TAG, "showSummaryNotification() Using REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID")
      NotificationCompat.Builder(
        appContext,
        NotificationConstants.ReplyNotifications.REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID
      )
    }

    val threadsWithUnseenRepliesCount = unreadNotificationsGrouped.size
    val totalUnseenRepliesCount = unreadNotificationsGrouped.values.sumBy { replies -> replies.size }

    val titleText = appContext.resources.getString(
      R.string.reply_notifications_new_replies_total_stats,
      totalUnseenRepliesCount,
      threadsWithUnseenRepliesCount
    )

    check(threadsWithUnseenRepliesCount > 0) { "Bad threadsWithUnseenRepliesCount" }
    check(totalUnseenRepliesCount > 0) { "Bad totalUnseenRepliesCount" }

    summaryNotificationBuilder
      .setWhen(notificationTime.millis)
      .setShowWhen(true)
      .setContentTitle(getApplicationLabel())
      .setContentText(titleText)
      .setSmallIcon(iconId)
      .setupSoundAndVibration(hasNewReplies, useSoundForReplyNotifications)
      .setupSummaryNotificationsStyle(titleText)
      .setupClickOnNotificationIntent(
        requestCode = RequestCodes.nextRequestCode(),
        threadDescriptors = unreadNotificationsGrouped.keys,
        postDescriptors = unreadNotificationsGrouped.values.flatMap { threadBookmarkViewList ->
          threadBookmarkViewList.map { threadBookmarkReplyView -> threadBookmarkReplyView.postDescriptor }
        }
      )
      .setupDeleteNotificationIntent(unreadNotificationsGrouped.keys)
      .setAllowSystemGeneratedContextualActions(false)
      .setAutoCancel(true)
      .setGroup(notificationsGroup)
      .setGroupSummary(true)

    notificationManagerCompat.notify(
      NotificationConstants.ReplyNotifications.SUMMARY_NOTIFICATION_TAG,
      NotificationConstants.REPLIES_SUMMARY_NOTIFICATION_ID,
      summaryNotificationBuilder.build()
    )

    Logger.d(TAG, "showSummaryNotification() notificationManagerCompat.notify() called")
    return true
  }

  @RequiresApi(Build.VERSION_CODES.O)
  suspend fun showNotificationsForAndroidOreoAndAbove(
    notificationTime: DateTime,
    unreadNotificationsGrouped: Map<ChanDescriptor.ThreadDescriptor, List<ThreadBookmarkReplyView>>
  ): Map<ChanDescriptor.ThreadDescriptor, Set<ThreadBookmarkReplyView>> {
    Logger.d(TAG, "showNotificationsForAndroidOreoAndAbove() called")

    val shownNotifications = mutableMapOf<ChanDescriptor.ThreadDescriptor, HashSet<ThreadBookmarkReplyView>>()
    var notificationCounter = 0

    val originalPosts = getOriginalPostsForNotifications(unreadNotificationsGrouped.keys)
    Logger.d(TAG, "Loaded ${originalPosts.size} original posts")

    val thumbnailBitmaps = getThreadThumbnails(originalPosts)
    Logger.d(TAG, "Loaded ${thumbnailBitmaps.size} thumbnail bitmaps")

    for ((threadDescriptor, threadBookmarkReplies) in unreadNotificationsGrouped) {
      if (threadBookmarkReplies.isEmpty()) {
        continue
      }

      val hasUnseenReplies = threadBookmarkReplies.any { threadBookmarkReplyView ->
        !threadBookmarkReplyView.alreadySeen
      }

      if (!hasUnseenReplies) {
        continue
      }

      val threadTitle = getThreadTitle(originalPosts, threadDescriptor)
      val titleText = appContext.resources.getString(
        R.string.reply_notifications_new_replies_in_thread,
        threadBookmarkReplies.size,
        threadDescriptor.threadNo
      )
      val notificationTag = getUniqueNotificationTag(threadDescriptor)
      val notificationId = NotificationConstants.ReplyNotifications.notificationId(threadDescriptor)

      val notificationBuilder = NotificationCompat.Builder(
        appContext,
        NotificationConstants.ReplyNotifications.REPLY_NOTIFICATION_CHANNEL_ID
      )
        .setContentTitle(titleText)
        .setWhen(notificationTime.millis)
        .setShowWhen(true)
        .setupLargeIcon(thumbnailBitmaps[threadDescriptor])
        .setSmallIcon(R.drawable.ic_stat_notify_alert)
        .setAutoCancel(true)
        .setupReplyNotificationsStyle(threadTitle, threadBookmarkReplies)
        .setupClickOnNotificationIntent(
          requestCode = RequestCodes.nextRequestCode(),
          threadDescriptors = listOf(threadDescriptor),
          postDescriptors = threadBookmarkReplies.map { threadBookmarkReplyView ->
            threadBookmarkReplyView.postDescriptor
          }
        )
        .setupDeleteNotificationIntent(listOf(threadDescriptor))
        .setAllowSystemGeneratedContextualActions(false)
        .setCategory(Notification.CATEGORY_MESSAGE)
        .setGroup(notificationsGroup)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)

      notificationManagerCompat.notify(
        notificationTag,
        notificationId,
        notificationBuilder.build()
      )

      Logger.d(
        TAG, "showNotificationsForAndroidOreoAndAbove() notificationManagerCompat.notify() " +
        "called, tag=${notificationTag}, counter=${notificationCounter}")

      ++notificationCounter

      shownNotifications.putIfNotContains(threadDescriptor, hashSetOf())
      shownNotifications[threadDescriptor]!!.addAll(threadBookmarkReplies)

      if (notificationCounter > MAX_VISIBLE_NOTIFICATIONS) {
        Logger.d(
          TAG, "showNotificationsForAndroidOreoAndAbove() " +
          "notificationCounter ($notificationCounter) exceeded MAX_VISIBLE_NOTIFICATIONS")
        break
      }
    }

    return shownNotifications
  }

  private suspend fun getThreadThumbnails(
    originalPosts: Map<ChanDescriptor.ThreadDescriptor, ChanPost>
  ): Map<ChanDescriptor.ThreadDescriptor, BitmapDrawable> {
    val resultMap = ConcurrentHashMap<ChanDescriptor.ThreadDescriptor, BitmapDrawable>()

    processDataCollectionConcurrently(originalPosts.entries, MAX_THUMBNAIL_REQUESTS_PER_BATCH) { entry ->
      val (threadDescriptor, originalPost) = entry

      val thumbnailUrl = originalPost.postImages.firstOrNull()?.actualThumbnailUrl
        ?: return@processDataCollectionConcurrently null

      val bitmapDrawable = downloadThumbnailForNotification(thumbnailUrl)
        ?: return@processDataCollectionConcurrently null

      resultMap[threadDescriptor] = bitmapDrawable
    }

    return resultMap
  }

  private suspend fun downloadThumbnailForNotification(
    thumbnailUrl: HttpUrl,
  ): BitmapDrawable? {
    return suspendCancellableCoroutine { cancellableContinuation ->
      val disposable = imageLoaderV2.loadFromNetwork(
        context = appContext,
        requestUrl = thumbnailUrl.toString(),
        cacheFileType = CacheFileType.PostMediaThumbnail,
        imageSize = ImageLoaderV2.ImageSize.FixedImageSize(
          NOTIFICATION_THUMBNAIL_SIZE,
          NOTIFICATION_THUMBNAIL_SIZE,
        ),
        transformations = CIRCLE_CROP,
        listener = object : ImageLoaderV2.FailureAwareImageListener {
          override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
            cancellableContinuation.resumeValueSafe(drawable)
          }

          override fun onNotFound() {
            cancellableContinuation.resumeValueSafe(null)
          }

          override fun onResponseError(error: Throwable) {
            Logger.e(
              TAG, "Error while trying to load thumbnail for notification image, " +
                "error: ${error.errorMessageOrClassName()}"
            )

            cancellableContinuation.resumeValueSafe(null)
          }
        }
      )

      cancellableContinuation.invokeOnCancellation { cause ->
        if (cause == null) {
          return@invokeOnCancellation
        }

        if (!disposable.isDisposed) {
          disposable.dispose()
        }
      }
    }
  }

  private fun getThreadTitle(
    originalPosts: Map<ChanDescriptor.ThreadDescriptor, ChanPost>,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): String {
    var title = originalPosts[threadDescriptor]?.subject?.toString()

    if (title.isNullOrBlank()) {
      title = originalPosts[threadDescriptor]?.postComment?.comment()?.toString()
    }

    if (title.isNullOrBlank()) {
      return threadDescriptor.threadNo.toString()
    }

    return title.ellipsizeEnd(MAX_THREAD_TITLE_LENGTH)
  }

  private suspend fun getOriginalPostsForNotifications(
    threadDescriptors: Set<ChanDescriptor.ThreadDescriptor>
  ): Map<ChanDescriptor.ThreadDescriptor, ChanPost> {
    return chanPostRepository.getCatalogOriginalPosts(threadDescriptors)
      .mapErrorToValue { error ->
        Logger.e(TAG, "chanPostRepository.getCatalogOriginalPosts() failed", error)
        return@mapErrorToValue emptyMap<ChanDescriptor.ThreadDescriptor, ChanPost>()
      }
  }

  private fun NotificationCompat.Builder.setupClickOnNotificationIntent(
    requestCode: Int,
    threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>,
    postDescriptors: Collection<PostDescriptor>
  ): NotificationCompat.Builder {
    val intent = Intent(appContext, StartActivity::class.java)
    val threadDescriptorsParcelable = threadDescriptors.map { threadDescriptor ->
      DescriptorParcelable.fromDescriptor(threadDescriptor)
    }
    val postDescriptorsParcelable = postDescriptors.map { postDescriptor ->
      PostDescriptorParcelable.fromPostDescriptor(postDescriptor)
    }

    intent
      .setAction(NotificationConstants.REPLY_NOTIFICATION_ACTION)
      .addCategory(Intent.CATEGORY_LAUNCHER)
      .setFlags(
        Intent.FLAG_ACTIVITY_CLEAR_TOP
          or Intent.FLAG_ACTIVITY_SINGLE_TOP
          or Intent.FLAG_ACTIVITY_NEW_TASK
          or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
      )
      .putParcelableArrayListExtra(
        NotificationConstants.ReplyNotifications.R_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY,
        ArrayList(threadDescriptorsParcelable)
      )
      .putParcelableArrayListExtra(
        NotificationConstants.ReplyNotifications.R_NOTIFICATION_CLICK_POST_DESCRIPTORS_KEY,
        ArrayList(postDescriptorsParcelable)
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

  private fun NotificationCompat.Builder.setupDeleteNotificationIntent(
    threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>
  ): NotificationCompat.Builder {
    val intent = Intent(appContext, ReplyNotificationDeleteIntentBroadcastReceiver::class.java)

    val threadDescriptorsParcelable = threadDescriptors.map { threadDescriptor ->
      DescriptorParcelable.fromDescriptor(threadDescriptor)
    }

    intent
      .putParcelableArrayListExtra(
        NotificationConstants.ReplyNotifications.R_NOTIFICATION_SWIPE_THREAD_DESCRIPTORS_KEY,
        ArrayList(threadDescriptorsParcelable)
      )

    val pendingIntent = PendingIntent.getBroadcast(
      appContext,
      RequestCodes.nextRequestCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    setDeleteIntent(pendingIntent)
    return this
  }

  private fun NotificationCompat.Builder.setupLargeIcon(
    bitmapDrawable: BitmapDrawable?
  ): NotificationCompat.Builder {
    if (bitmapDrawable != null) {
      setLargeIcon(bitmapDrawable.bitmap)
    }

    return this
  }

  private fun NotificationCompat.Builder.setupSoundAndVibration(
    hasNewReplies: Boolean,
    useSoundForReplyNotifications: Boolean
  ): NotificationCompat.Builder {
    if (hasNewReplies) {
      Logger.d(TAG, "Using sound and vibration: useSoundForReplyNotifications=${useSoundForReplyNotifications}")

      if (useSoundForReplyNotifications) {
        setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
      } else {
        setDefaults(Notification.DEFAULT_VIBRATE)
      }

      setLights(themeEngine.chanTheme.accentColor, 1000, 1000)
    }

    return this
  }

  private fun NotificationCompat.Builder.setupSummaryNotificationsStyle(
    summaryText: String
  ): NotificationCompat.Builder {
    setStyle(
      NotificationCompat.InboxStyle(this)
        .setSummaryText(summaryText)
    )

    return this
  }

  private suspend fun NotificationCompat.Builder.setupReplyNotificationsStyle(
    titleText: String,
    threadBookmarkReplyViewSet: Collection<ThreadBookmarkReplyView>
  ): NotificationCompat.Builder {
    val repliesSorted = threadBookmarkReplyViewSet
      .filter { threadBookmarkReplyView -> !threadBookmarkReplyView.alreadySeen }
      .sortedWith(REPLIES_COMPARATOR)
      .takeLast(MAX_LINES_IN_NOTIFICATION)

    val parsedReplyComments = withContext(Dispatchers.Default) {
      return@withContext repliesSorted.map { threadBookmarkReplyView ->
        val commentRaw = threadBookmarkReplyView.commentRaw
        if (commentRaw != null) {
          // Convert to string to get rid of spans
          val parsedComment = simpleCommentParser.parseComment(commentRaw)
            ?.toString()

          if (!parsedComment.isNullOrEmpty()) {
            return@map parsedComment
          }

          // fallthrough
        }

        // Default reply in case we failed to parse the reply comment
        return@map appContext.resources.getString(
          R.string.reply_notifications_reply_format,
          threadBookmarkReplyView.postDescriptor.postNo,
          threadBookmarkReplyView.repliesTo.postNo
        )
      }
    }

    if (parsedReplyComments.size > 1) {
      // If there are more than one notification to show - use InboxStyle
      val notificationStyle = NotificationCompat.InboxStyle(this)
        .setSummaryText(titleText)

      parsedReplyComments.forEach { replyComment ->
        notificationStyle.addLine(replyComment.take(MAX_NOTIFICATION_LINE_LENGTH))
      }

      setStyle(notificationStyle)
    } else {
      // If there is only one notification to show - use BigTextStyle
      check(parsedReplyComments.isNotEmpty()) { "parsedReplyComments is empty!" }
      val replyComment = parsedReplyComments.first()

      val notificationStyle = NotificationCompat.BigTextStyle(this)
        .setSummaryText(titleText)
        .bigText(replyComment)

      setStyle(notificationStyle)
    }

    return this
  }

  private fun setupChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    Logger.d(TAG, "setupChannels() called")

    if (notificationManagerCompat.getNotificationChannel(NotificationConstants.ReplyNotifications.REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID) == null) {
      Logger.d(
        TAG, "setupChannels() " +
        "creating ${NotificationConstants.ReplyNotifications.REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID} channel")

      // notification channel for replies summary
      val summaryChannel = NotificationChannel(
        NotificationConstants.ReplyNotifications.REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID,
        NotificationConstants.ReplyNotifications.REPLY_SUMMARY_NOTIFICATION_NAME,
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

    if (notificationManagerCompat.getNotificationChannel(NotificationConstants.ReplyNotifications.REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID) == null) {
      Logger.d(
        TAG, "setupChannels() creating " +
        "${NotificationConstants.ReplyNotifications.REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID} channel")

      // notification channel for replies summary
      val summaryChannel = NotificationChannel(
        NotificationConstants.ReplyNotifications.REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID,
        NotificationConstants.ReplyNotifications.REPLY_SUMMARY_SILENT_NOTIFICATION_NAME,
        NotificationManager.IMPORTANCE_LOW
      )

      notificationManagerCompat.createNotificationChannel(summaryChannel)
    }

    if (notificationManagerCompat.getNotificationChannel(NotificationConstants.ReplyNotifications.REPLY_NOTIFICATION_CHANNEL_ID) == null) {
      Logger.d(
        TAG, "setupChannels() creating " +
        "${NotificationConstants.ReplyNotifications.REPLY_NOTIFICATION_CHANNEL_ID} channel")

      // notification channel for replies
      val replyChannel = NotificationChannel(
        NotificationConstants.ReplyNotifications.REPLY_NOTIFICATION_CHANNEL_ID,
        NotificationConstants.ReplyNotifications.REPLY_NOTIFICATION_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_LOW
      )

      notificationManagerCompat.createNotificationChannel(replyChannel)
    }
  }

  private fun getUniqueNotificationTag(threadDescriptor: ChanDescriptor.ThreadDescriptor): String {
    return NotificationConstants.ReplyNotifications.NOTIFICATION_TAG_PREFIX + threadDescriptor.serializeToString()
  }

  @RequiresApi(Build.VERSION_CODES.M)
  private fun restoreNotificationIdMap(
    unreadNotificationsGrouped: MutableMap<ChanDescriptor.ThreadDescriptor, MutableSet<ThreadBookmarkReplyView>>
  ) {
    if (!AndroidUtils.isAndroidO()) {
      return
    }

    val visibleNotifications = notificationManager.activeNotifications
      .filter { statusBarNotification -> statusBarNotification.notification.group == notificationsGroup }

    if (visibleNotifications.isEmpty()) {
      return
    }

    val maxNotificationId = visibleNotifications.maxByOrNull { notification -> notification.id }?.id ?: 0
    if (maxNotificationId > NotificationConstants.ReplyNotifications.notificationIdCounter.get()) {
      NotificationConstants.ReplyNotifications.notificationIdCounter.set(maxNotificationId)
    }

    val visibleNotificationsMap = visibleNotifications
      .associateBy { notification -> notification.tag }

    unreadNotificationsGrouped.keys.forEach { threadDescriptor ->
      val tag = getUniqueNotificationTag(threadDescriptor)

      if (visibleNotificationsMap.containsKey(tag)) {
        NotificationConstants.ReplyNotifications.notificationIdMap[threadDescriptor] =
          visibleNotificationsMap[tag]!!.id
      }
    }
  }

  private fun closeAllNotifications() {
    if (!AndroidUtils.isAndroidO()) {
      notificationManagerCompat.cancel(
        NotificationConstants.ReplyNotifications.REPLIES_PRE_OREO_NOTIFICATION_TAG,
        NotificationConstants.REPLIES_PRE_OREO_NOTIFICATION_ID
      )

      Logger.d(TAG, "closeAllNotifications() closed REPLIES_PRE_OREO_NOTIFICATION_ID notification")
      return
    }

    val visibleNotifications = notificationManager.activeNotifications
      .filter { statusBarNotification -> statusBarNotification.notification.group == notificationsGroup }

    if (visibleNotifications.isEmpty()) {
      Logger.d(TAG, "closeAllNotifications() visibleNotifications are empty")
      return
    }

    visibleNotifications.forEach { notification ->
      notificationManagerCompat.cancel(notification.tag, notification.id)
    }

    Logger.d(TAG, "closeAllNotifications() closed ${visibleNotifications.size} notifications")
  }

  companion object {
    private const val TAG = "ReplyNotificationsHelper"

    private const val NOTIFICATIONS_UPDATE_DEBOUNCE_TIME = 1000L
    private const val MAX_THREAD_TITLE_LENGTH = 50
    private const val MAX_THUMBNAIL_REQUESTS_PER_BATCH = 8
    private const val MAX_NOTIFICATION_LINE_LENGTH = 128

    // For Android O and above
    private val notificationsGroup by lazy { "${TAG}_${BuildConfig.APPLICATION_ID}_${getFlavorType().name}" }

    private val REPLIES_COMPARATOR = Comparator<ThreadBookmarkReplyView> { o1, o2 ->
      o1.postDescriptor.postNo.compareTo(o2.postDescriptor.postNo)
    }

    private val CIRCLE_CROP = listOf<Transformation>(CircleCropTransformation())
  }
}
