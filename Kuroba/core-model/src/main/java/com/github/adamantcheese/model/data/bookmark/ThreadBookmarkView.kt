package com.github.adamantcheese.model.data.bookmark

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import okhttp3.HttpUrl
import java.util.*
import kotlin.collections.HashMap

class ThreadBookmarkView private constructor(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val seenPostsCount: Int = 0,
  val totalPostsCount: Int = 0,
  val lastViewedPostNo: Long = 0,
  val threadBookmarkReplyViews: MutableMap<PostDescriptor, ThreadBookmarkReplyView> = mutableMapOf(),
  val title: String? = null,
  val thumbnailUrl: HttpUrl? = null,
  private val state: BitSet,
  private val stickyThread: StickyThread = StickyThread.NotSticky
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

  fun postsCount(): Int = totalPostsCount

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ThreadBookmarkView

    if (threadDescriptor != other.threadDescriptor) return false
    if (seenPostsCount != other.seenPostsCount) return false
    if (totalPostsCount != other.totalPostsCount) return false
    if (lastViewedPostNo != other.lastViewedPostNo) return false
    if (threadBookmarkReplyViews != other.threadBookmarkReplyViews) return false
    if (title != other.title) return false
    if (thumbnailUrl != other.thumbnailUrl) return false
    if (stickyThread != other.stickyThread) return false
    if (state != other.state) return false

    return true
  }

  override fun hashCode(): Int {
    var result = threadDescriptor.hashCode()
    result = 31 * result + seenPostsCount
    result = 31 * result + totalPostsCount
    result = 31 * result + lastViewedPostNo.hashCode()
    result = 31 * result + threadBookmarkReplyViews.hashCode()
    result = 31 * result + (title?.hashCode() ?: 0)
    result = 31 * result + (thumbnailUrl?.hashCode() ?: 0)
    result = 31 * result + stickyThread.hashCode()
    result = 31 * result + state.hashCode()
    return result
  }

  override fun toString(): String {
    return "ThreadBookmarkView(threadDescriptor=$threadDescriptor, seenPostsCount=$seenPostsCount, " +
      "totalPostsCount=$totalPostsCount, lastViewedPostNo=$lastViewedPostNo, " +
      "threadBookmarkReplyViews=$threadBookmarkReplyViews, title=${title?.take(20)}, " +
      "thumbnailUrl=$thumbnailUrl, stickyThread=$stickyThread, state=$state)"
  }

  companion object {
    fun fromThreadBookmark(threadBookmark: ThreadBookmark): ThreadBookmarkView {
      return ThreadBookmarkView(
        threadDescriptor = threadBookmark.threadDescriptor,
        seenPostsCount = threadBookmark.seenPostsCount,
        totalPostsCount = threadBookmark.totalPostsCount,
        lastViewedPostNo = threadBookmark.lastViewedPostNo,
        threadBookmarkReplyViews = mapToViews(threadBookmark.threadBookmarkReplies),
        title = threadBookmark.title,
        thumbnailUrl = threadBookmark.thumbnailUrl,
        stickyThread = threadBookmark.stickyThread,
        state = threadBookmark.state
      )
    }

    private fun mapToViews(
      threadBookmarkReplies: MutableMap<PostDescriptor, ThreadBookmarkReply>
    ): MutableMap<PostDescriptor, ThreadBookmarkReplyView> {
      if (threadBookmarkReplies.isEmpty()) {
        return mutableMapOf()
      }

      val resultMap = HashMap<PostDescriptor, ThreadBookmarkReplyView>(threadBookmarkReplies.size)

      threadBookmarkReplies.forEach { (postDescriptor, threadBookmarkReply) ->
        resultMap[postDescriptor] = threadBookmarkReply.toThreadBookmarkReplyView()
      }

      return resultMap
    }

  }
}