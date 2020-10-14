package com.github.k1rakishou.model.data.bookmark

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

data class ThreadBookmarkGroup(
  val groupId: String,
  val groupName: String,
  val order: Int,
  // Map<BookmarkDatabaseId, ThreadBookmarkGroupEntry>
  val entries: Map<Long, ThreadBookmarkGroupEntry>
)

data class ThreadBookmarkGroupEntry(
  val databaseId: Long,
  val ownerGroupId: String,
  val ownerBookmarkId: Long,
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val orderInGroup: Int,
)