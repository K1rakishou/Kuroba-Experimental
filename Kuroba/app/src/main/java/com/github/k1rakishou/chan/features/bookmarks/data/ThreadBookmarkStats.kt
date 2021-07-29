package com.github.k1rakishou.chan.features.bookmarks.data

data class ThreadBookmarkStats(
  val watching: Boolean,
  val isArchive: Boolean = false,
  val isWatcherEnabled: Boolean = true,
  val newPosts: Int = 0,
  val newQuotes: Int = 0,
  val totalPosts: Int = 0,
  val currentPage: Int = 0,
  val totalPages: Int = 0,
  val isBumpLimit: Boolean = false,
  val isImageLimit: Boolean = false,
  val isFirstFetch: Boolean = false,
  val isFilterWatchBookmark: Boolean = false,
  val isDownloading: Boolean = false,
  val isDeleted: Boolean = false,
  val isError: Boolean = false
) {

  @Suppress("ConvertTwoComparisonsToRangeCheck")
  fun isLastPage() = totalPages > 0 && currentPage >= totalPages

  fun isDead(): Boolean = isArchive || isDeleted

  fun isDeadOrNotWatching(): Boolean = isDead() || !watching

}