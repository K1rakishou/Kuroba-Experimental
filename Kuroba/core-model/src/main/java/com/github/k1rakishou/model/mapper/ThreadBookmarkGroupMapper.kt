package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroup
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupEntry
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupEntryToCreate
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.entity.bookmark.BookmarkThreadDescriptor
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupEntryEntity

object ThreadBookmarkGroupMapper {

  fun fromEntity(
    threadBookmarkGroupEntity: ThreadBookmarkGroupEntity,
    threadBookmarkGroupEntryEntities: List<ThreadBookmarkGroupEntryEntity>,
    bookmarkThreadDescriptorsMap: Map<Long, BookmarkThreadDescriptor>
  ): ThreadBookmarkGroup {
    val threadBookmarkGroupEntries = threadBookmarkGroupEntryEntities
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
      }
      .associateBy { threadBookmarkGroupEntry -> threadBookmarkGroupEntry.databaseId }

    val orders = threadBookmarkGroupEntryEntities
      .sortedBy { threadBookmarkGroupEntryEntity -> threadBookmarkGroupEntryEntity.orderInGroup }
      .map { threadBookmarkGroupEntryEntity -> threadBookmarkGroupEntryEntity.id }

    return ThreadBookmarkGroup(
      groupId = threadBookmarkGroupEntity.groupId,
      groupName = threadBookmarkGroupEntity.groupName,
      isExpanded = threadBookmarkGroupEntity.isExpanded,
      groupOrder = threadBookmarkGroupEntity.groupOrder,
      newEntries = threadBookmarkGroupEntries,
      newOrders = orders
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
    return threadBookmarkGroupEntryToCreateList.map { threadBookmarkGroupEntryToCreate ->
      toEntity2(threadBookmarkGroupEntryToCreate)
    }
  }

  fun toEntity2(
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
      orderInGroup = threadBookmarkGroupEntryToCreate.orderInGroup
    )
  }

}