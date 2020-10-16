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
          threadDescriptor = threadDescriptor
        )
      }.associateBy { threadBookmarkGroupEntry -> threadBookmarkGroupEntry.databaseId }

    if (threadBookmarkGroupEntries.isEmpty()) {
      return null
    }

    val orders = threadBookmarkGroupWithEntries.threadBookmarkGroupEntryEntities
      .sortedBy { threadBookmarkGroupEntryEntity -> threadBookmarkGroupEntryEntity.orderInGroup }
      .map { threadBookmarkGroupEntryEntity -> threadBookmarkGroupEntryEntity.id }

    return ThreadBookmarkGroup(
      groupId = threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.groupId,
      groupName = threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.groupName,
      isExpanded = threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.isExpanded,
      groupOrder = threadBookmarkGroupWithEntries.threadBookmarkGroupEntity.groupOrder,
      entries = ConcurrentHashMap(threadBookmarkGroupEntries),
      orders = orders.toMutableList()
    )
  }

  fun toEntityList(
    threadBookmarkGroupEntryList: List<ThreadBookmarkGroupEntry>
  ): List<ThreadBookmarkGroupEntryEntity> {
    return threadBookmarkGroupEntryList.mapIndexed { index, threadBookmarkGroupEntry ->
      return@mapIndexed toEntity(index, threadBookmarkGroupEntry)
    }
  }

  fun toEntity(
    order: Int,
    threadBookmarkGroupEntry: ThreadBookmarkGroupEntry
  ): ThreadBookmarkGroupEntryEntity {
    return ThreadBookmarkGroupEntryEntity(
      id = threadBookmarkGroupEntry.databaseId,
      ownerBookmarkId = threadBookmarkGroupEntry.ownerBookmarkId,
      ownerGroupId = threadBookmarkGroupEntry.ownerGroupId,
      orderInGroup = order
    )
  }

  fun toEntityList2(
    threadBookmarkGroupEntryToCreateList: List<ThreadBookmarkGroupEntryToCreate>
  ): List<ThreadBookmarkGroupEntryEntity> {
    return threadBookmarkGroupEntryToCreateList.mapIndexed { index, threadBookmarkGroupEntryToCreate ->
      toEntity2(index, threadBookmarkGroupEntryToCreate)
    }
  }

  fun toEntity2(
    order: Int,
    threadBookmarkGroupEntryToCreate: ThreadBookmarkGroupEntryToCreate
  ): ThreadBookmarkGroupEntryEntity {
    val ownerBookmarkId = threadBookmarkGroupEntryToCreate.ownerBookmarkId

    require(threadBookmarkGroupEntryToCreate.ownerBookmarkId > 0L) {
      "Bad ownerBookmarkId: ${threadBookmarkGroupEntryToCreate.ownerBookmarkId}"
    }

    return ThreadBookmarkGroupEntryEntity(
      id = 0L,
      ownerBookmarkId = ownerBookmarkId,
      ownerGroupId = threadBookmarkGroupEntryToCreate.ownerGroupId,
      orderInGroup = order
    )
  }

}