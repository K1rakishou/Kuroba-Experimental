package com.github.adamantcheese.model.data.bookmark

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import java.util.*
import kotlin.math.max

class ThreadBookmarkView private constructor(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val watchLastCount: Int = -1,
  val watchNewCount: Int = -1,
  val quoteLastCount: Int = -1,
  val quoteNewCount: Int = -1,
  val title: String? = null,
  val thumbnailUrl: HttpUrl? = null,
  private val state: BitSet
) {

  /**
   * Bookmark is being watched (not paused) and the thread it's watching is not archived/deleted
   * */
  fun isActive(): Boolean = isWatching() && !isThreadArchived() && !isThreadDeleted()
  fun isWatching(): Boolean = state.get(ThreadBookmark.BOOKMARK_STATE_WATCHING)
  fun isThreadDeleted(): Boolean = state.get(ThreadBookmark.BOOKMARK_STATE_THREAD_DELETED)
  fun isThreadArchived(): Boolean = state.get(ThreadBookmark.BOOKMARK_STATE_THREAD_ARCHIVED)

  fun postsCount(): Int = max(watchLastCount, watchNewCount)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ThreadBookmark

    if (threadDescriptor != other.threadDescriptor) return false
    if (watchLastCount != other.watchLastCount) return false
    if (watchNewCount != other.watchNewCount) return false
    if (quoteLastCount != other.quoteLastCount) return false
    if (quoteNewCount != other.quoteNewCount) return false
    if (title != other.title) return false
    if (thumbnailUrl != other.thumbnailUrl) return false
    if (state != other.state) return false

    return true
  }

  override fun hashCode(): Int {
    var result = threadDescriptor.hashCode()
    result = 31 * result + watchLastCount
    result = 31 * result + watchNewCount
    result = 31 * result + quoteLastCount
    result = 31 * result + quoteNewCount
    result = 31 * result + (title?.hashCode() ?: 0)
    result = 31 * result + (thumbnailUrl?.hashCode() ?: 0)
    result = 31 * result + state.hashCode()
    return result
  }

  override fun toString(): String {
    return "ThreadBookmarkView(threadDescriptor=$threadDescriptor, watchLastCount=$watchLastCount, " +
      "watchNewCount=$watchNewCount, quoteLastCount=$quoteLastCount, quoteNewCount=$quoteNewCount, " +
      "title=${title?.take(20)}, thumbnailUrl=$thumbnailUrl, state=$state)"
  }

  companion object {

    fun fromThreadBookmark(threadBookmark: ThreadBookmark): ThreadBookmarkView {
      return ThreadBookmarkView(
        threadDescriptor = threadBookmark.threadDescriptor,
        watchLastCount = threadBookmark.watchLastCount,
        watchNewCount = threadBookmark.watchNewCount,
        quoteLastCount = threadBookmark.quoteLastCount,
        quoteNewCount = threadBookmark.quoteNewCount,
        title = threadBookmark.title,
        thumbnailUrl = threadBookmark.thumbnailUrl,
        state = threadBookmark.state
      )
    }

  }
}