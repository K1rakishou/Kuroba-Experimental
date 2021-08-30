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

    val catalogSnapshot = chanCatalogSnapshotCache.getOrPut(
      key = chanCatalogSnapshot.catalogDescriptor,
      valueFunc = {
        return@getOrPut ChanCatalogSnapshot(
          catalogDescriptor = chanCatalogSnapshot.catalogDescriptor,
          isUnlimitedCatalog = chanCatalogSnapshot.isUnlimitedCatalog
        )
      }
    )

    catalogSnapshot.mergeWith(chanCatalogSnapshot)

    val boardDescriptors = when (val catalogDescriptor = catalogSnapshot.catalogDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> {
        listOf(catalogDescriptor.boardDescriptor)
      }
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        catalogDescriptor.catalogDescriptors.map { descriptor -> descriptor.boardDescriptor }
      }
      else -> {
        error("Unknown catalogDescriptor type: ${catalogDescriptor.javaClass.simpleName}")
      }
    }.toSet()

    boardDescriptors.forEach { boardDescriptor ->
      val boardId = chanDescriptorCache.getBoardIdByBoardDescriptor(boardDescriptor)
        ?: return@forEach

      val chanCatalogSnapshotEntityList = catalogSnapshot.catalogThreadDescriptorList
        .filter { threadDescriptor -> threadDescriptor.boardDescriptor == boardDescriptor }
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
  }

  suspend fun preloadChanCatalogSnapshot(
    catalogDescriptor: ChanDescriptor.ICatalogDescriptor,
    isUnlimitedCatalog: Boolean
  ): Boolean {
    ensureInTransaction()
    val fromCache = chanCatalogSnapshotCache.get(catalogDescriptor)
    if (fromCache != null && !fromCache.isEmpty()) {
      // Already have something cached, no need to overwrite it with the database data
      return false
    }

    val boardDescriptors = when (catalogDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> {
        listOf(catalogDescriptor.boardDescriptor)
      }
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        catalogDescriptor.catalogDescriptors.map { descriptor -> descriptor.boardDescriptor }
      }
    }.toSet()

    var success = false

    boardDescriptors.forEach { boardDescriptor ->
      val boardId = chanDescriptorCache.getBoardIdByBoardDescriptor(boardDescriptor)
        ?: return@forEach

      val chanCatalogSnapshotEntityList = chanCatalogSnapshotDao.selectManyByBoardIdOrdered(boardId.id)
      if (chanCatalogSnapshotEntityList.isEmpty()) {
        return@forEach
      }

      val chanCatalogSnapshotEntryList = chanCatalogSnapshotEntityList.map { chanCatalogSnapshotEntity ->
        return@map ChanDescriptor.ThreadDescriptor.create(
          boardDescriptor,
          chanCatalogSnapshotEntity.threadNo
        )
      }

      if (chanCatalogSnapshotEntryList.isEmpty()) {
        return@forEach
      }

      val prevCatalogSnapshot = chanCatalogSnapshotCache.getOrPut(
        key = catalogDescriptor,
        valueFunc = { ChanCatalogSnapshot(catalogDescriptor, isUnlimitedCatalog) }
      )

      success = true
      prevCatalogSnapshot.add(chanCatalogSnapshotEntryList)
    }

    return success
  }

  fun getCatalogSnapshot(catalogDescriptor: ChanDescriptor.CatalogDescriptor): ChanCatalogSnapshot? {
    return chanCatalogSnapshotCache.get(catalogDescriptor)
  }

}