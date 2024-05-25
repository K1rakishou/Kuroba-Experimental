package com.github.k1rakishou.model.repository

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.mutableSetWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.catalog.ChanCatalogSnapshot
import com.github.k1rakishou.model.data.catalog.IChanCatalogSnapshot
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.source.local.ChanCatalogSnapshotLocalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.measureTimedValue

class ChanCatalogSnapshotRepository(
  database: KurobaDatabase,
  private val verboseLogsEnabled: Boolean,
  private val applicationScope: CoroutineScope,
  private val localSource: ChanCatalogSnapshotLocalSource
) : AbstractRepository(database) {
  private val TAG = "ChanCatalogSnapshotRepository"

  private val mutex = Mutex()
  @GuardedBy("mutex")
  private val alreadyPreloadedSet = mutableSetWithCap<ChanDescriptor.CatalogDescriptor>(128)

  suspend fun preloadChanCatalogSnapshot(
    catalogDescriptor: ChanDescriptor.CatalogDescriptor,
    isUnlimitedCatalog: Boolean
  ): ModularResult<Unit> {
    val alreadyPreloaded = mutex.withLock { alreadyPreloadedSet.contains(catalogDescriptor) }
    if (alreadyPreloaded) {
      return ModularResult.value(Unit)
    }

    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {

        if (verboseLogsEnabled) {
          Logger.d(TAG, "preloadChanCatalogSnapshot($catalogDescriptor) begin")
        }

        val (preloaded, time) = measureTimedValue {
          localSource.preloadChanCatalogSnapshot(catalogDescriptor, isUnlimitedCatalog)
        }
        if (preloaded) {
          mutex.withLock { alreadyPreloadedSet.add(catalogDescriptor) }
        }

        if (verboseLogsEnabled) {
          Logger.d(TAG, "preloadChanCatalogSnapshot($catalogDescriptor) end, took $time")
        }

        return@tryWithTransaction
      }
    }
  }

  fun getCatalogSnapshot(catalogDescriptor: ChanDescriptor.CatalogDescriptor): ChanCatalogSnapshot? {
    return localSource.getCatalogSnapshot(catalogDescriptor)
  }

  suspend fun storeChanCatalogSnapshot(
    chanCatalogSnapshot: IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>
  ): ModularResult<Unit> {
    Logger.d(TAG, "storeChanCatalogSnapshot($chanCatalogSnapshot)")

    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.storeChanCatalogSnapshot(chanCatalogSnapshot)
      }
    }
  }

}