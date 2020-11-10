package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.catalog.ChanCatalogSnapshot
import com.github.k1rakishou.model.data.catalog.ChanCatalogSnapshotEntry
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

  suspend fun storeChanCatalogSnapshot(
    chanCatalogSnapshot: ChanCatalogSnapshot
  ) {
    ensureInTransaction()

    if (chanCatalogSnapshot.isEmpty()) {
      return
    }

    val boardId = chanDescriptorCache.getBoardIdByBoardDescriptor(chanCatalogSnapshot.boardDescriptor)
      ?: return

    val chanCatalogSnapshotEntityList = chanCatalogSnapshot.catalogThreadDescriptors
      .mapIndexed { order, threadDescriptor -> ChanCatalogSnapshotEntity(boardId, threadDescriptor.threadNo, order) }

    chanCatalogSnapshotDao.deleteManyByBoardId(boardId)
    chanCatalogSnapshotDao.insertMany(chanCatalogSnapshotEntityList)

    chanCatalogSnapshotCache.store(chanCatalogSnapshot.boardDescriptor, chanCatalogSnapshot)
  }

  suspend fun preloadChanCatalogSnapshot(
    catalogDescriptor: ChanDescriptor.CatalogDescriptor
  ) {
    ensureInTransaction()
    val boardDescriptor = catalogDescriptor.boardDescriptor

    val fromCache = chanCatalogSnapshotCache.get(boardDescriptor)
    if (fromCache != null && !fromCache.isEmpty()) {
      // Already have something cached, no need to overwrite it with the database data
      return
    }

    val boardId = chanDescriptorCache.getBoardIdByBoardDescriptor(boardDescriptor)
      ?: return

    val chanCatalogSnapshotEntityList = chanCatalogSnapshotDao.selectManyByBoardIdOrdered(boardId)
    if (chanCatalogSnapshotEntityList.isEmpty()) {
      return
    }

    val chanCatalogSnapshotEntryList = chanCatalogSnapshotEntityList.mapIndexed { index, chanCatalogSnapshotEntity ->
      val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
        boardDescriptor,
        chanCatalogSnapshotEntity.threadNo
      )

      return@mapIndexed ChanCatalogSnapshotEntry(threadDescriptor, index)
    }

    if (chanCatalogSnapshotEntryList.isEmpty()) {
      return
    }

    val chanCatalogSnapshot = ChanCatalogSnapshot(boardDescriptor, chanCatalogSnapshotEntryList)
    chanCatalogSnapshotCache.store(chanCatalogSnapshot.boardDescriptor, chanCatalogSnapshot)
  }

}