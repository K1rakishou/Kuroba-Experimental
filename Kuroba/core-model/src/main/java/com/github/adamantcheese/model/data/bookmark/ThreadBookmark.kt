package com.github.adamantcheese.model.data.bookmark

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import java.util.*

class ThreadBookmark private constructor(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  var watchLastCount: Int = -1,
  var watchNewCount: Int = -1,
  var quoteLastCount: Int = -1,
  var quoteNewCount: Int = -1,
  var title: String? = null,
  var thumbnailUrl: HttpUrl? = null,
  var state: BitSet
) {

  fun isActive(): Boolean = isWatching() && !isThreadArchived() && !isThreadDeleted()
  fun isWatching(): Boolean = state.get(BOOKMARK_STATE_WATCHING)
  fun isThreadDeleted(): Boolean = state.get(BOOKMARK_STATE_THREAD_DELETED)
  fun isThreadArchived(): Boolean = state.get(BOOKMARK_STATE_THREAD_ARCHIVED)


  fun fill(oldThreadBookmark: ThreadBookmark) {
    this.watchLastCount = oldThreadBookmark.watchLastCount
    this.watchNewCount = oldThreadBookmark.watchNewCount
    this.quoteLastCount = oldThreadBookmark.quoteLastCount
    this.quoteNewCount = oldThreadBookmark.quoteNewCount
    this.title = oldThreadBookmark.title
    this.thumbnailUrl = oldThreadBookmark.thumbnailUrl
    this.state = BitSet.valueOf(oldThreadBookmark.state.toLongArray())
  }

  fun copy(): ThreadBookmark {
    return ThreadBookmark(
      threadDescriptor = threadDescriptor,
      watchLastCount = watchLastCount,
      watchNewCount = watchNewCount,
      quoteLastCount = quoteLastCount,
      quoteNewCount = quoteNewCount,
      title = title,
      thumbnailUrl = thumbnailUrl,
      state = state
    )
  }

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
    return "ThreadBookmark(threadDescriptor=$threadDescriptor, watchLastCount=$watchLastCount, " +
      "watchNewCount=$watchNewCount, quoteLastCount=$quoteLastCount, quoteNewCount=$quoteNewCount, " +
      "title=${title?.take(20)}, thumbnailUrl=$thumbnailUrl, state=$state)"
  }

  companion object {
    /**
     * A flag for bookmarks that are being watched (not paused). Default flag when bookmarking any
     * thread.
     * */
    const val BOOKMARK_STATE_WATCHING = 1 shl 0

    /**
     * A flag for threads that are probably got deleted (404ed) from the server
     * */
    const val BOOKMARK_STATE_THREAD_DELETED = 1 shl 1

    /**
     * A flag for threads that got archived by first-party archives (like the archive that 4chan has)
     * */
    const val BOOKMARK_STATE_THREAD_ARCHIVED = 1 shl 2

    fun create(threadDescriptor: ChanDescriptor.ThreadDescriptor): ThreadBookmark {
      val bookmarkInitialState = BitSet()
      bookmarkInitialState.set(BOOKMARK_STATE_WATCHING)

      return ThreadBookmark(
        threadDescriptor = threadDescriptor,
        state = bookmarkInitialState
      )
    }
  }

}