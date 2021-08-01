package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.catalog.ChanCatalogSnapshot
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.entity.chan.catalog.ChanCatalogSnapshotEntity
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import com.github.k1rakishou.model.source.cache.ChanDescriptorCache

class ChanCatalogSnapshotLocalSource(
  database: KurobaDatabase,
  private val chanDescriptorCache: ChanDescriptorCache,
  private val chanCatalogSnapshotCache: ChanCatalogSnapshotCache
) : AbstractLocalSource(database) {
  private val TAG = "ChanCatalogSnapshotLocalSource"
  private val chanCatalogSnapshotDao = database.chanCatalogSnapshotDao()

  suspend fun storeChanCatalogSnapshot(chanCatalogSnapshot: ChanCatalogSnapshot) {
    ensureInTransaction()

    if (chanCatalogSnapshot.isEmpty()) {
      return
    }

    val isUnlimitedCatalog = chanCatalogSnapshot.isUnlimitedCatalog

    val catalogSnapshot = chanCatalogSnapshotCache.getOrPut(
      key = chanCatalogSnapshot.boardDescriptor,
      valueFunc = { ChanCatalogSnapshot(chanCatalogSnapshot.boardDescriptor, isUnlimitedCatalog) }
    )

    catalogSnapshot.mergeWith(chanCatalogSnapshot)

    val boardId = chanDescriptorCache.getBoardIdByBoardDescriptor(catalogSnapshot.boardDescriptor)
      ?: return

    val chanCatalogSnapshotEntityList = catalogSnapshot.catalogThreadDescriptorList
      .mapIndexed { order, threadDescriptor ->
        return@mapIndexed ChanCatalogSnapshotEntity(
          boardId.id,
          threadDescriptor.threadNo,
          order
        )
      }

    chanCatalogSnapshotDao.deleteManyByBoardId(boardId.id)
    chanCatalogSnapshotDao.insertMany(chanCatalogSnapshotEntityList)
  }

  suspend fun preloadChanCatalogSnapshot(
    catalogDescriptor: ChanDescriptor.CatalogDescriptor,
    isUnlimitedCatalog: Boolean
  ): Boolean {
    ensureInTransaction()
    val boardDescriptor = catalogDescriptor.boardDescriptor

    val fromCache = chanCatalogSnapshotCache.get(boardDescriptor)
    if (fromCache != null && !fromCache.isEmpty()) {
      // Already have something cached, no need to overwrite it with the database data
      return false
    }

    val boardId = chanDescriptorCache.getBoardIdByBoardDescriptor(boardDescriptor)
      ?: return false

    val chanCatalogSnapshotEntityList = chanCatalogSnapshotDao.selectManyByBoardIdOrdered(boardId.id)
    if (chanCatalogSnapshotEntityList.isEmpty()) {
      return false
    }

    val chanCatalogSnapshotEntryList = chanCatalogSnapshotEntityList.map { chanCatalogSnapshotEntity ->
        return@map ChanDescriptor.ThreadDescriptor.create(
          boardDescriptor,
          chanCatalogSnapshotEntity.threadNo
        )
      }

    if (chanCatalogSnapshotEntryList.isEmpty()) {
      return false
    }

    val prevCatalogSnapshot = chanCatalogSnapshotCache.getOrPut(
      key = catalogDescriptor.boardDescriptor,
      valueFunc = { ChanCatalogSnapshot(boardDescriptor, isUnlimitedCatalog) }
    )

    prevCatalogSnapshot.add(chanCatalogSnapshotEntryList)

    return true
  }

  fun getCatalogSnapshot(catalogDescriptor: ChanDescriptor.CatalogDescriptor): ChanCatalogSnapshot? {
    return chanCatalogSnapshotCache.get(catalogDescriptor.boardDescriptor)
  }

}