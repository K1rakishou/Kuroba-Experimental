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
          bookmarkThreadDescriptor.siteName,
          bookmarkThreadDescriptor.boardCode,
          bookmarkThreadDescriptor.threadNo
        )

        return@mapNotNull ThreadBookmarkGroupEntry(
          threadBookmarkGroupEntryEntity.id,
          threadBookmarkGroupEntryEntity.ownerGroupId,
          ownerBookmarkId,
          threadDescriptor,
          threadBookmarkGroupEntryEntity.orderInGroup
        )
      }.associateBy { threadBookmarkGroupEntry -> threadBookmarkGroupEntry.databaseId }

    return ThreadBookmarkGroup(
      threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.groupId,
      threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.groupName,
      threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.groupOrder,
      threadBookmarkGroupEntries
    )
  }

}