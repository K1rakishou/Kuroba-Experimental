package com.github.k1rakishou.chan.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.utils.NotificationConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import javax.inject.Inject

class ReplyNotificationDeleteIntentBroadcastReceiver : BroadcastReceiver() {

  @Inject
  lateinit var bookmarksManager: BookmarksManager

  private val scope = KurobaCoroutineScope()
  private val serializedExecutor = SerializedCoroutineExecutor(scope)

  init {
    Chan.getComponent()
      .inject(this)
  }

  override fun onReceive(context: Context?, intent: Intent?) {
    if (context == null || intent == null) {
      return
    }

    val extras = intent.extras
      ?: return

    if (!intent.hasExtra(NotificationConstants.ReplyNotifications.R_NOTIFICATION_SWIPE_THREAD_DESCRIPTORS_KEY)) {
      return
    }

    val threadDescriptors = extras.getParcelableArrayList<DescriptorParcelable>(
      NotificationConstants.ReplyNotifications.R_NOTIFICATION_SWIPE_THREAD_DESCRIPTORS_KEY
    )?.map { it -> ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(it) }

    if (threadDescriptors.isNullOrEmpty()) {
      return
    }

    Logger.d(TAG, "Adding new notification swipe request, threadDescriptorsCount=${threadDescriptors.size}")
    val pendingResult = goAsync()

    serializedExecutor.post {
      try {
        bookmarksManager.awaitUntilInitialized()

        replyNotificationSwipedAway(threadDescriptors)
      } finally {
        pendingResult.finish()
      }
    }
  }

  private suspend fun replyNotificationSwipedAway(threadDescriptors: List<ChanDescriptor.ThreadDescriptor>) {
    Logger.d(TAG, "replyNotificationSwipedAway() reply notification swiped away, "
      + "marking as seen ${threadDescriptors.size} bookmarks")

    val updatedBookmarkDescriptors = bookmarksManager.updateBookmarksNoPersist(threadDescriptors) { threadBookmark ->
      threadBookmark.markAsSeenAllReplies()
    }

    bookmarksManager.persistBookmarksManually(updatedBookmarkDescriptors)
  }

  companion object {
    private const val TAG = "ReplyNotificationDeleteIntentBroadcastReceiver"
  }

}