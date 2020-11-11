package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.myAsync
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.catalog.ChanCatalogSnapshot
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.source.local.ChanCatalogSnapshotLocalSource
import kotlinx.coroutines.CoroutineScope

class ChanCatalogSnapshotRepository(
  database: KurobaDatabase,
  private val applicationScope: CoroutineScope,
  private val localSource: ChanCatalogSnapshotLocalSource
) : AbstractRepository(database) {
  private val TAG = "ChanCatalogSnapshotRepository"

  suspend fun preloadChanCatalogSnapshot(
    catalogDescriptor: ChanDescriptor.CatalogDescriptor
  ): ModularResult<Unit> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.preloadChanCatalogSnapshot(catalogDescriptor)
      }
    }
  }

  fun getCatalogSnapshot(catalogDescriptor: ChanDescriptor.CatalogDescriptor): ChanCatalogSnapshot? {
    return localSource.getCatalogSnapshot(catalogDescriptor)
  }

  suspend fun storeChanCatalogSnapshot(
    chanCatalogSnapshot: ChanCatalogSnapshot
  ): ModularResult<Unit> {
    Logger.d(TAG, "storeChanCatalogSnapshot($chanCatalogSnapshot)")

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.storeChanCatalogSnapshot(chanCatalogSnapshot)
      }
    }
  }

}