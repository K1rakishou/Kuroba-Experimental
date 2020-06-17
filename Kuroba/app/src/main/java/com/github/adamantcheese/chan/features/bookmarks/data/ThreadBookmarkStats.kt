package com.github.adamantcheese.chan.features.bookmarks.data

data class ThreadBookmarkStats(
  val showBookmarkStats: Boolean,
  val watching: Boolean,
  val newPosts: Int = 0,
  val newQuotes: Int = 0,
  val totalPosts: Int = 0,
  val isBumpLimit: Boolean = false,
  val isImageLimit: Boolean = false,
  val isLastPage: Boolean = false
)