package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroup
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupEntry
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.entity.bookmark.BookmarkThreadDescriptor
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupWithEntries

object ThreadBookmarkGroupMapper {

  fun fromEntity(
    threadBookmarkGroupWithEntries: ThreadBookmarkGroupWithEntries,
    bookmarkThreadDescriptorsMap: Map<Long, BookmarkThreadDescriptor>
  ): ThreadBookmarkGroup {
    val threadBookmarkGroupEntries = threadBookmarkGroupWithEntries.threadBookmarkGroupEntryEntities
      .mapNotNull { threadBookmarkGroupEntryEntity ->
        val ownerBookmarkId = requireNotNull(threadBookmarkGroupEntryEntity.ownerBookmarkId) {
          "ownerBookmarkId must not be null here!"
        }

        val bookmarkThreadDescriptor = bookmarkThreadDescriptorsMap[ownerBookmarkId]
          ?: return@mapNotNull null

        val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
          siteName = bookmarkThreadDescriptor.siteName,
          boardCode = bookmarkThreadDescriptor.boardCode,
          threadNo = bookmarkThreadDescriptor.threadNo
        )

        return@mapNotNull ThreadBookmarkGroupEntry(
          databaseId = threadBookmarkGroupEntryEntity.id,
          ownerGroupId = threadBookmarkGroupEntryEntity.ownerGroupId,
          ownerBookmarkId = ownerBookmarkId,
          threadDescriptor = threadDescriptor,
          orderInGroup = threadBookmarkGroupEntryEntity.orderInGroup
        )
      }.associateBy { threadBookmarkGroupEntry -> threadBookmarkGroupEntry.databaseId }

    return ThreadBookmarkGroup(
      groupId = threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.groupId,
      groupName = threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.groupName,
      isExpanded = threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.isExpanded,
      order = threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.groupOrder,
      entries = threadBookmarkGroupEntries
    )
  }

}