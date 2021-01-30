package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.filter.ChanFilterWatchGroup
import com.github.k1rakishou.model.data.id.ThreadBookmarkDBId
import com.github.k1rakishou.model.entity.chan.filter.ChanFilterWatchGroupEntity
import com.github.k1rakishou.model.source.cache.ChanDescriptorCache

class ChanFilterWatchLocalSource(
  database: KurobaDatabase,
  private val chanDescriptorCache: ChanDescriptorCache
) : AbstractLocalSource(database) {
  private val TAG = "ChanFilterWatchLocalSource"
  private val chanFilterWatchGroupDao = database.chanFilterWatchGroupDao()

  suspend fun createFilterWatchGroups(watchGroups: List<ChanFilterWatchGroup>) {
    ensureInTransaction()

    val threadDescriptors = watchGroups
      .map { chanFilterWatchGroup -> chanFilterWatchGroup.threadDescriptor }

    val bookmarkIdMap = chanDescriptorCache.getManyThreadBookmarkIds(threadDescriptors)

    val watchGroupEntities = watchGroups.mapNotNull { chanFilterWatchGroup ->
      val threadBookmarkDatabaseId = bookmarkIdMap.get(chanFilterWatchGroup.threadDescriptor)
        ?: return@mapNotNull null

      return@mapNotNull ChanFilterWatchGroupEntity(
        chanFilterWatchGroup.ownerChanFilterDatabaseId,
        threadBookmarkDatabaseId.id
      )
    }

    chanFilterWatchGroupDao.insertMany(watchGroupEntities)
  }

  suspend fun getFilterWatchGroups(): List<ChanFilterWatchGroup> {
    ensureInTransaction()

    val chanFilterWatchGroupEntities = chanFilterWatchGroupDao.selectAll()
    val threadBookmarkDatabaseIds = chanFilterWatchGroupEntities
      .map { chanFilterWatchGroupEntity ->
        return@map ThreadBookmarkDBId(chanFilterWatchGroupEntity.ownerThreadBookmarkDatabaseId)
      }

    val bookmarkIdMap = chanDescriptorCache.getManyBookmarkThreadDescriptors(threadBookmarkDatabaseIds)

    return chanFilterWatchGroupEntities.mapNotNull { chanFilterWatchGroupEntity ->
      val threadBookmarkId = ThreadBookmarkDBId(chanFilterWatchGroupEntity.ownerThreadBookmarkDatabaseId)

      val bookmarkThreadDescriptor = bookmarkIdMap[threadBookmarkId]
        ?: return@mapNotNull null

      return@mapNotNull ChanFilterWatchGroup(
        chanFilterWatchGroupEntity.ownerChanFilterDatabaseId,
        bookmarkThreadDescriptor.threadDescriptor
      )
    }
  }

  suspend fun clearFilterWatchGroups() {
    ensureInTransaction()

    chanFilterWatchGroupDao.deleteAll()
  }

}