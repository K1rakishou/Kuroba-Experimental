package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.bookmark.CreateBookmarkGroupEntriesTransaction
import com.github.k1rakishou.model.data.bookmark.DeleteBookmarkGroupEntriesTransaction
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroup
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupEntryToCreate
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupEntryEntity
import com.github.k1rakishou.model.mapper.ThreadBookmarkGroupMapper
import com.github.k1rakishou.model.source.cache.ChanDescriptorCache

class ThreadBookmarkGroupLocalSource(
  database: KurobaDatabase,
  private val isDevFlavor: Boolean,
  private val chanDescriptorCache: ChanDescriptorCache
) : AbstractLocalSource(database) {
  private val TAG = "ThreadBookmarkGroupLocalSource"
  private val threadBookmarkGroupDao = database.threadBookmarkGroupDao()

  suspend fun selectAll(): List<ThreadBookmarkGroup> {
    ensureInTransaction()

    val threadBookmarkGroupList = threadBookmarkGroupDao.selectAllGroups()
    val threadBookmarkGroupWithEntriesList = threadBookmarkGroupDao.selectGroupsWithEntries()

    val bookmarkIds = threadBookmarkGroupWithEntriesList.flatMap { threadBookmarkGroupWithEntries ->
      return@flatMap threadBookmarkGroupWithEntries.threadBookmarkGroupEntryEntities.map { threadBookmarkGroupEntryEntity ->
        return@map threadBookmarkGroupEntryEntity.ownerBookmarkId
      }
    }.toSet()

    val bookmarkThreadDescriptorsMap = bookmarkIds
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> threadBookmarkGroupDao.selectBookmarkThreadDescriptors(chunk) }
      .associateBy { bookmarkThreadDescriptor -> bookmarkThreadDescriptor.ownerBookmarkId }

    val threadBookmarkGroupWithEntriesMap = threadBookmarkGroupWithEntriesList
      .associateBy { threadBookmarkGroup -> threadBookmarkGroup.threadBookmarkGroupEntity.groupId }

    return threadBookmarkGroupList
      .map { threadBookmarkGroupEntity ->
        val threadBookmarkGroupWithEntries = threadBookmarkGroupWithEntriesMap[threadBookmarkGroupEntity.groupId]
          ?.threadBookmarkGroupEntryEntities
          ?: emptyList()

        return@map ThreadBookmarkGroupMapper.fromEntity(
          threadBookmarkGroupEntity = threadBookmarkGroupEntity,
          threadBookmarkGroupEntryEntities = threadBookmarkGroupWithEntries,
          bookmarkThreadDescriptorsMap = bookmarkThreadDescriptorsMap
        )
      }
  }

  suspend fun updateBookmarkGroupExpanded(groupId: String, isExpanded: Boolean) {
    ensureInTransaction()

    threadBookmarkGroupDao.updateBookmarkGroupExpanded(groupId, isExpanded)
  }

  suspend fun executeCreateTransaction(
    createTransaction: CreateBookmarkGroupEntriesTransaction
  ) {
    ensureInTransaction()

    val groupIds = createTransaction.toCreate.keys
    val existingGroupIds = threadBookmarkGroupDao.selectExistingGroupIds(groupIds).toHashSet()

    val groupsToCreate = createTransaction.toCreate.entries.mapNotNull { (groupId, group) ->
      if (groupId in existingGroupIds) {
        return@mapNotNull null
      }

      check(group.groupOrder >= 0) { "Bad order, group=${group}" }

      return@mapNotNull ThreadBookmarkGroupEntity(
        groupId = group.groupId,
        groupName = group.groupName,
        isExpanded = group.isExpanded,
        groupOrder = group.groupOrder
      )
    }

    groupsToCreate
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .forEach { chunk -> threadBookmarkGroupDao.createGroups(chunk) }

    val bookmarkEntriesToCreateMap = mutableMapOf<String, MutableList<ThreadBookmarkGroupEntryToCreate>>()
    val bookmarkThreadDescriptors = mutableListOf<ChanDescriptor.ThreadDescriptor>()

    createTransaction.toCreate.values.forEach { threadBookmarkGroupToCreate ->
      bookmarkEntriesToCreateMap.putIfNotContains(threadBookmarkGroupToCreate.groupId, mutableListOf())

      threadBookmarkGroupToCreate.entries.forEach { threadBookmarkGroupEntryToCreate ->
        bookmarkEntriesToCreateMap[threadBookmarkGroupEntryToCreate.ownerGroupId]!!.add(threadBookmarkGroupEntryToCreate)
        bookmarkThreadDescriptors += threadBookmarkGroupEntryToCreate.threadDescriptor
      }
    }

    val bookmarkIdMap = chanDescriptorCache.getManyThreadBookmarkIds(bookmarkThreadDescriptors)

    bookmarkEntriesToCreateMap.entries.forEach { (_, threadBookmarkGroupEntryToCreateList) ->
      if (threadBookmarkGroupEntryToCreateList.isEmpty()) {
        return@forEach
      }

      threadBookmarkGroupEntryToCreateList.forEach { threadBookmarkGroupEntryToCreate ->
        val threadDescriptor = threadBookmarkGroupEntryToCreate.threadDescriptor
        val ownerBookmarkId = bookmarkIdMap[threadDescriptor]?.id

        requireNotNull(ownerBookmarkId) {
          "Couldn't find BookmarkDatabaseId for bookmark with descriptor $threadDescriptor"
        }

        threadBookmarkGroupEntryToCreate.ownerBookmarkId = ownerBookmarkId
      }

      val threadBookmarkGroupEntryEntities =
        ThreadBookmarkGroupMapper.toEntityList2(threadBookmarkGroupEntryToCreateList)

      val databaseIds = threadBookmarkGroupEntryEntities
        .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
        .flatMap { chunk -> threadBookmarkGroupDao.insertMany(chunk) }

      check(threadBookmarkGroupEntryEntities.size == databaseIds.size) {
        "Sizes do not match: " +
          "threadBookmarkGroupEntryEntities.size=${threadBookmarkGroupEntryEntities.size}, " +
          "databaseIds.size=${databaseIds.size}"
      }

      for (index in databaseIds.indices) {
        val databaseId = databaseIds[index]
        threadBookmarkGroupEntryToCreateList[index].databaseId = databaseId
      }
    }
  }

  suspend fun executeDeleteTransaction(deleteTransaction: DeleteBookmarkGroupEntriesTransaction) {
    ensureInTransaction()

    val databaseIdsToDelete = deleteTransaction.toDelete
      .map { threadBookmarkGroupEntry -> threadBookmarkGroupEntry.databaseId }
      .toSet()

    databaseIdsToDelete
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .forEach { chunk -> threadBookmarkGroupDao.deleteBookmarkEntries(chunk) }

    deleteTransaction.toUpdate.forEach { (_, groupEntries) ->
      threadBookmarkGroupDao.updateMany(ThreadBookmarkGroupMapper.toEntityList(groupEntries))
    }
  }

  suspend fun updateGroup(group: ThreadBookmarkGroup) {
    ensureInTransaction()

    val entitiesList = mutableListOf<ThreadBookmarkGroupEntryEntity>()

    group.iterateEntriesOrderedWhile { order, threadBookmarkGroupEntry ->
      entitiesList += ThreadBookmarkGroupMapper.toEntity(order, threadBookmarkGroupEntry)
      return@iterateEntriesOrderedWhile true
    }

    threadBookmarkGroupDao.updateMany(entitiesList)
  }

  suspend fun updateGroupOrders(groups: List<ThreadBookmarkGroup>) {
    ensureInTransaction()

    val threadBookmarkGroupEntityList = groups
      .sortedBy { group -> group.groupOrder }
      .mapIndexed { newOrder, group ->
        return@mapIndexed ThreadBookmarkGroupEntity(
          groupId = group.groupId,
          groupName = group.groupName,
          isExpanded = group.isExpanded,
          groupOrder = newOrder
        )
      }

    if (threadBookmarkGroupEntityList.isEmpty()) {
      return
    }

    threadBookmarkGroupDao.updateGroups(threadBookmarkGroupEntityList)
  }

  suspend fun deleteGroup(groupId: String) {
    ensureInTransaction()

    threadBookmarkGroupDao.deleteGroup(groupId)
  }

}