package com.github.k1rakishou.model.data.bookmark

import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import okhttp3.HttpUrl
import org.joda.time.DateTime
import java.util.*
import kotlin.math.max

class ThreadBookmarkView private constructor(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val groupId: String,
  val seenPostsCount: Int = 0,
  val totalPostsCount: Int = 0,
  val lastViewedPostNo: Long = 0,
  val threadLastPostNo: Long = 0,
  val threadBookmarkReplyViews: MutableMap<PostDescriptor, ThreadBookmarkReplyView> = mutableMapOf(),
  val title: String? = null,
  val thumbnailUrl: HttpUrl? = null,
  private val state: BitSet,
  private val stickyThread: StickyThread = StickyThread.NotSticky,
  val createdOn: DateTime
) {

  /**
   * Bookmark is being watched (not paused) and the thread it's watching is not archived/deleted
   * */
  fun isActive(): Boolean = isWatching()
  fun isWatching(): Boolean = state.get(ThreadBookmark.BOOKMARK_STATE_WATCHING)
  fun isThreadDeleted(): Boolean = state.get(ThreadBookmark.BOOKMARK_STATE_THREAD_DELETED)
  fun isThreadArchived(): Boolean = state.get(ThreadBookmark.BOOKMARK_STATE_THREAD_ARCHIVED)
  fun isBumpLimit(): Boolean = state.get(ThreadBookmark.BOOKMARK_STATE_THREAD_BUMP_LIMIT)
  fun isImageLimit(): Boolean = state.get(ThreadBookmark.BOOKMARK_STATE_THREAD_IMAGE_LIMIT)
  fun isRollingSticky(): Boolean = stickyThread is StickyThread.StickyWithCap
  fun isFirstFetch(): Boolean = state.get(ThreadBookmark.BOOKMARK_STATE_FIRST_FETCH)
  fun isError(): Boolean = state.get(ThreadBookmark.BOOKMARK_STATE_ERROR)
  fun isFilterWatchBookmark(): Boolean = state.get(ThreadBookmark.BOOKMARK_FILTER_WATCH)

  fun postsCount(): Int = totalPostsCount
  fun newPostsCount(): Int = max(0, totalPostsCount - seenPostsCount)
  fun newQuotesCount(): Int = threadBookmarkReplyViews.values.count { reply -> !reply.alreadyRead }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ThreadBookmarkView

    if (threadDescriptor != other.threadDescriptor) return false
    if (groupId != other.groupId) return false
    if (seenPostsCount != other.seenPostsCount) return false
    if (totalPostsCount != other.totalPostsCount) return false
    if (lastViewedPostNo != other.lastViewedPostNo) return false
    if (threadLastPostNo != other.threadLastPostNo) return false
    if (title != other.title) return false
    if (thumbnailUrl != other.thumbnailUrl) return false
    if (state != other.state) return false
    if (stickyThread != other.stickyThread) return false
    if (createdOn != other.createdOn) return false

    if (!compareThreadBookmarkReplyViews(threadBookmarkReplyViews, other.threadBookmarkReplyViews)) {
      return false
    }

    return true
  }

  override fun hashCode(): Int {
    var result = threadDescriptor.hashCode()
    result = 31 * result + groupId.hashCode()
    result = 31 * result + seenPostsCount
    result = 31 * result + totalPostsCount
    result = 31 * result + lastViewedPostNo.hashCode()
    result = 31 * result + threadLastPostNo.hashCode()
    result = 31 * result + threadBookmarkReplyViews.hashCode()
    result = 31 * result + (title?.hashCode() ?: 0)
    result = 31 * result + (thumbnailUrl?.hashCode() ?: 0)
    result = 31 * result + state.hashCode()
    result = 31 * result + stickyThread.hashCode()
    result = 31 * result + createdOn.hashCode()
    return result
  }

  override fun toString(): String {
    return "ThreadBookmarkView(threadDescriptor=$threadDescriptor, groupId=$groupId, seenPostsCount=$seenPostsCount, " +
      "totalPostsCount=$totalPostsCount, lastViewedPostNo=$lastViewedPostNo, threadLastPostNo=$threadLastPostNo" +
      "threadBookmarkReplyViews=$threadBookmarkReplyViews, title=${title?.take(20)}, " +
      "thumbnailUrl=$thumbnailUrl, stickyThread=$stickyThread, state=$state, createdOn=$createdOn)"
  }

  private fun compareThreadBookmarkReplyViews(
    tbrv1: MutableMap<PostDescriptor, ThreadBookmarkReplyView>,
    tbrv2: MutableMap<PostDescriptor, ThreadBookmarkReplyView>
  ): Boolean {
    if (tbrv1.size != tbrv2.size) {
      return false
    }

    for ((postDescriptor, threadBookmarkReplyView1) in tbrv1.entries) {
      val threadBookmarkReplyView2 = tbrv2[postDescriptor]
        ?: return false

      if (threadBookmarkReplyView1 != threadBookmarkReplyView2) {
        return false
      }
    }

    return true
  }

  companion object {
    fun fromThreadBookmark(threadBookmark: ThreadBookmark): ThreadBookmarkView {
      return ThreadBookmarkView(
        threadDescriptor = threadBookmark.threadDescriptor,
        groupId = threadBookmark.groupId,
        seenPostsCount = threadBookmark.seenPostsCount,
        totalPostsCount = threadBookmark.threadRepliesCount,
        lastViewedPostNo = threadBookmark.lastViewedPostNo,
        threadLastPostNo = threadBookmark.threadLastPostNo,
        threadBookmarkReplyViews = mapToViews(threadBookmark.threadBookmarkReplies),
        title = threadBookmark.title,
        thumbnailUrl = threadBookmark.thumbnailUrl,
        stickyThread = threadBookmark.stickyThread,
        state = threadBookmark.state,
        createdOn = threadBookmark.createdOn
      )
    }

    private fun mapToViews(
      threadBookmarkReplies: MutableMap<PostDescriptor, ThreadBookmarkReply>
    ): MutableMap<PostDescriptor, ThreadBookmarkReplyView> {
      if (threadBookmarkReplies.isEmpty()) {
        return mutableMapOf()
      }

      val resultMap = mutableMapWithCap<PostDescriptor, ThreadBookmarkReplyView>(threadBookmarkReplies.size)

      threadBookmarkReplies.forEach { (postDescriptor, threadBookmarkReply) ->
        resultMap[postDescriptor] = threadBookmarkReply.toThreadBookmarkReplyView()
      }

      return resultMap
    }

  }
}