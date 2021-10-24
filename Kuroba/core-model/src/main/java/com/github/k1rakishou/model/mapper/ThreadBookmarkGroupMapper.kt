package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.bookmark.BookmarkGroupMatchFlag
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroup
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupEntry
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupEntryToCreate
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupMatchPattern
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.entity.bookmark.BookmarkThreadDescriptor
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupEntryEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

object ThreadBookmarkGroupMapper {

  fun fromEntity(
    moshi: Moshi,
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
      newOrders = orders,
      newMatchingPattern = matchingPatternFromEntity(
        moshi = moshi,
        matchingPatternRaw = threadBookmarkGroupEntity.groupMatcherPattern
      )
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

  fun matchingPatternToEntity(
    moshi: Moshi,
    matchingPattern: ThreadBookmarkGroupMatchPattern?
  ): String? {
    if (matchingPattern == null) {
      return null
    }

    val bookmarkGroupMatchFlagJsonList = matchingPattern.asList()
      .map { bookmarkGroupMatchFlag ->
        return@map BookmarkGroupMatchFlagJson(
          rawPattern = bookmarkGroupMatchFlag.rawPattern,
          matcherType = bookmarkGroupMatchFlag.type.rawType,
          operator = bookmarkGroupMatchFlag.operator?.operatorId
        )
      }

    if (bookmarkGroupMatchFlagJsonList.isEmpty()) {
      return null
    }

    return moshi
      .adapter(BookmarkGroupMatchFlagJsonList::class.java)
      .toJson(BookmarkGroupMatchFlagJsonList(bookmarkGroupMatchFlagJsonList))
  }

  fun matchingPatternFromEntity(
    moshi: Moshi,
    matchingPatternRaw: String?
  ): ThreadBookmarkGroupMatchPattern? {
    if (matchingPatternRaw == null) {
      return null
    }

    val bookmarkGroupMatchFlagList = moshi
      .adapter<BookmarkGroupMatchFlagJsonList>(BookmarkGroupMatchFlagJsonList::class.java)
      .fromJson(matchingPatternRaw)
      ?.list
      ?.mapNotNull { bookmarkGroupMatchFlagJson ->
        val matcherType = BookmarkGroupMatchFlag.Type.fromRawTypeOrNull(bookmarkGroupMatchFlagJson.matcherType)
          ?: return@mapNotNull null
        val operator = BookmarkGroupMatchFlag.Operator.fromOperatorIdOrNull(bookmarkGroupMatchFlagJson.operator)

        return@mapNotNull BookmarkGroupMatchFlag(
          rawPattern = bookmarkGroupMatchFlagJson.rawPattern,
          type = matcherType,
          nextGroupMatchFlag = null,
          operator = operator
        )
      }

    if (bookmarkGroupMatchFlagList == null || bookmarkGroupMatchFlagList.isEmpty()) {
      return null
    }

    return ThreadBookmarkGroupMatchPattern.fromList(bookmarkGroupMatchFlagList)
  }

  @JsonClass(generateAdapter = true)
  data class BookmarkGroupMatchFlagJsonList(
    val list: List<BookmarkGroupMatchFlagJson>
  )

  @JsonClass(generateAdapter = true)
  data class BookmarkGroupMatchFlagJson(
    val rawPattern: String,
    val matcherType: Int,
    var operator: Int?
  )

}