package com.github.k1rakishou.common.options

/**
 * Can be used to load a thread partially (like only the first n-posts or only last n-posts). For now
 * can only be used with threads (catalogs are not supported). Really handy when you want to show
 * to the user a preview of a thread (original post + n first/last posts of a thread)
 * */
data class ChanReadOptions(
  // We always read original post. It's always true for now but we may need to change it in some
  // cases in the future.
  val readOriginalPost: Boolean = true,
  // This value does not include OP
  val readFirstPostsCount: Int = Int.MAX_VALUE,
  // This value does not include OP
  val readLastPostsCount: Int = Int.MAX_VALUE,
  // This value represents then last N posts that will be retained
  val threadMaxPostsCapacity: Int = 0,
) {

  private fun ignoreReadFirstPostsCount(): Boolean = readFirstPostsCount == Int.MAX_VALUE
  private fun ignoreReadLastPostsCount(): Boolean = readLastPostsCount == Int.MAX_VALUE
  private fun ignoreThreadMaxPostsCapacity(): Boolean = threadMaxPostsCapacity <= 0

  /**
   * Returns a list of IntRange
   * */
  fun getRetainPostRanges(postsCount: Int): List<IntRange> {
    if (postsCount <= 0) {
      return emptyList()
    }

    if (!readOriginalPost
      && ignoreReadFirstPostsCount()
      && ignoreReadLastPostsCount()
      && ignoreThreadMaxPostsCapacity()) {
      return emptyList()
    }

    val resultList = mutableListOf<IntRange>()
    if (readOriginalPost) {
      resultList += IntRange(0, 0)
    }

    if (!ignoreReadFirstPostsCount()) {
      resultList += 1 .. readFirstPostsCount.coerceAtMost(postsCount)
    }

    if (!ignoreReadLastPostsCount()) {
      resultList += (postsCount - readLastPostsCount).coerceAtLeast(1) .. postsCount
    }

    if (!ignoreThreadMaxPostsCapacity()) {
      resultList += (postsCount - threadMaxPostsCapacity).coerceAtLeast(1) .. postsCount
    }

    return resultList
  }

  fun isDefault(): Boolean {
    return readOriginalPost
      && ignoreReadFirstPostsCount()
      && ignoreReadLastPostsCount()
      && ignoreThreadMaxPostsCapacity()
  }

  companion object {
    fun default(threadMaxPostsCapacity: Int): ChanReadOptions {
      val maxPostsCapacity = if (threadMaxPostsCapacity < 0) {
        0
      } else {
        threadMaxPostsCapacity
      }

      return ChanReadOptions(true, Int.MAX_VALUE, Int.MAX_VALUE, maxPostsCapacity)
    }
  }

}