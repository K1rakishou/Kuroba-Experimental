package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroup
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupEntry
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupEntryToCreate
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.entity.bookmark.BookmarkThreadDescriptor
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupEntryEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupWithEntries
import java.util.concurrent.ConcurrentHashMap

object ThreadBookmarkGroupMapper {

  fun fromEntity(
    threadBookmarkGroupWithEntries: ThreadBookmarkGroupWithEntries,
    bookmarkThreadDescriptorsMap: Map<Long, BookmarkThreadDescriptor>
  ): ThreadBookmarkGroup? {
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

    if (threadBookmarkGroupEntries.isEmpty()) {
      return null
    }

    return ThreadBookmarkGroup(
      groupId = threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.groupId,
      groupName = threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.groupName,
      isExpanded = threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.isExpanded,
      order = threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.groupOrder,
      entries = ConcurrentHashMap(threadBookmarkGroupEntries)
    )
  }

  fun toEntityList(threadBookmarkGroupEntryList: List<ThreadBookmarkGroupEntry>): List<ThreadBookmarkGroupEntryEntity> {
    return threadBookmarkGroupEntryList.map { toEntity(it) }
  }

  fun toEntity(threadBookmarkGroupEntry: ThreadBookmarkGroupEntry): ThreadBookmarkGroupEntryEntity {
    return ThreadBookmarkGroupEntryEntity(
      id = threadBookmarkGroupEntry.databaseId,
      ownerBookmarkId = threadBookmarkGroupEntry.ownerBookmarkId,
      ownerGroupId = threadBookmarkGroupEntry.ownerGroupId,
      orderInGroup = threadBookmarkGroupEntry.orderInGroup
    )
  }

  fun toEntityList2(threadBookmarkGroupEntryToCreateList: List<ThreadBookmarkGroupEntryToCreate>): List<ThreadBookmarkGroupEntryEntity> {
    return threadBookmarkGroupEntryToCreateList.map { toEntity2(it) }
  }

  fun toEntity2(threadBookmarkGroupEntryToCreate: ThreadBookmarkGroupEntryToCreate): ThreadBookmarkGroupEntryEntity {
    val ownerBookmarkId =threadBookmarkGroupEntryToCreate.ownerBookmarkId

    require(threadBookmarkGroupEntryToCreate.ownerBookmarkId > 0L) {
      "Bad ownerBookmarkId: ${threadBookmarkGroupEntryToCreate.ownerBookmarkId}"
    }

    return ThreadBookmarkGroupEntryEntity(
      id = 0L,
      ownerBookmarkId = ownerBookmarkId,
      ownerGroupId = threadBookmarkGroupEntryToCreate.ownerGroupId,
      orderInGroup = threadBookmarkGroupEntryToCreate.orderInGroup
    )
  }

}