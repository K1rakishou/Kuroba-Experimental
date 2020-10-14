package com.github.k1rakishou.chan.features.bookmarks.data

data class GroupOfThreadBookmarkItemViews(
  val groupId: String,
  val groupName: String,
  val threadBookmarkViews: List<ThreadBookmarkItemView>
)