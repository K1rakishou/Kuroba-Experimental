package com.github.k1rakishou.model.data.bookmark

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

class ThreadBookmarkInfoObject(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val simplePostObjects: List<ThreadBookmarkInfoPostObject>
) {

  fun getPostsCountWithoutOP(): Int {
    check(simplePostObjects.first() is ThreadBookmarkInfoPostObject.OriginalPost) {
      "First post of ThreadBookmarkInfoObject is not OP"
    }

    return simplePostObjects.size - 1
  }

  fun countAmountOfSeenPosts(lastViewedPostNo: Long): Int {
    check(simplePostObjects.first() is ThreadBookmarkInfoPostObject.OriginalPost) {
      "First post of ThreadBookmarkInfoObject is not OP"
    }

    return simplePostObjects.count { threadBookmarkInfoPostObject ->
      threadBookmarkInfoPostObject.postNo() <= lastViewedPostNo
    }
  }

  fun lastThreadPostNo(): Long {
    return simplePostObjects.maxOfOrNull { simplePostObject -> simplePostObject.postNo() } ?: 0L
  }

}

sealed class ThreadBookmarkInfoPostObject {

  fun comment(): String {
    return when (this) {
      is OriginalPost -> comment
      is RegularPost -> comment
    }
  }

  fun postNo(): Long {
    return when (this) {
      is OriginalPost -> postNo
      is RegularPost -> postNo
    }
  }

  data class OriginalPost(
    val postNo: Long,
    val closed: Boolean,
    val archived: Boolean,
    val isBumpLimit: Boolean,
    val isImageLimit: Boolean,
    val stickyThread: StickyThread,
    val comment: String
  ) : ThreadBookmarkInfoPostObject() {

    override fun toString(): String {
      return "OriginalPost(postNo=$postNo, closed=$closed, archived=$archived, " +
        "isBumpLimit=$isBumpLimit, isImageLimit=$isImageLimit, stickyThread=$stickyThread)"
    }
  }

  data class RegularPost(
    val postNo: Long,
    val comment: String
  ) : ThreadBookmarkInfoPostObject() {

    override fun toString(): String {
      return "RegularPost(postNo=$postNo)"
    }
  }
}

sealed class StickyThread {
  object NotSticky : StickyThread()

  // Sticky thread without post cap.
  object StickyUnlimited : StickyThread()

  // Rolling sticky thread
  data class StickyWithCap(val cap: Int) : StickyThread()

  override fun toString(): String {
    return when (this) {
      NotSticky -> "NotSticky"
      StickyUnlimited -> "StickyUnlimited"
      is StickyWithCap -> "StickyWithCap(cap=${cap})"
    }
  }

  companion object {
    fun create(isSticky: Boolean, stickyCap: Int): StickyThread {
      if (!isSticky) {
        return NotSticky
      }

      if (isSticky && stickyCap <= 0) {
        return StickyUnlimited
      }

      if (isSticky && stickyCap > 0) {
        return StickyWithCap(stickyCap)
      }

      throw IllegalStateException("Bad StickyThread, isSticky: $isSticky, stickyCap: $stickyCap")
    }
  }
}