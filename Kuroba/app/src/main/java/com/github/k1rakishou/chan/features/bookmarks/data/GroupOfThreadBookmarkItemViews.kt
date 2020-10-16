package com.github.k1rakishou.chan.features.bookmarks.data

data class GroupOfThreadBookmarkItemViews(
  val groupId: String,
  val groupInfoText: String,
  val isExpanded: Boolean,
  val threadBookmarkItemViews: List<ThreadBookmarkItemView>
)