package com.github.k1rakishou.model.repository

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.KurobaMainDatabase
import com.github.k1rakishou.model.data.catalog.ChanCatalogSnapshot
import com.github.k1rakishou.model.data.catalog.IChanCatalogSnapshot
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.source.local.ChanCatalogSnapshotLocalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.measureTimedValue

class ChanCatalogSnapshotRepository(
  database: KurobaMainDatabase,
  applicationScope: CoroutineScope,
  private val verboseLogsEnabled: Boolean,
  private val localSource: ChanCatalogSnapshotLocalSource
) : AbstractRepository(database, applicationScope) {
  private val TAG = "ChanCatalogSnapshotRepository"

  private val mutex = Mutex()
  @GuardedBy("mutex")
  private val alreadyPreloadedSet = hashSetWithCap<ChanDescriptor.CatalogDescriptor>(128)

  suspend fun preloadChanCatalogSnapshot(
    catalogDescriptor: ChanDescriptor.CatalogDescriptor,
    isUnlimitedCatalog: Boolean
  ): ModularResult<Unit> {
    val alreadyPreloaded = mutex.withLock { alreadyPreloadedSet.contains(catalogDescriptor) }
    if (alreadyPreloaded) {
      return ModularResult.value(Unit)
    }

    return database.call {
      return@call tryWithTransaction {

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

    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.storeChanCatalogSnapshot(chanCatalogSnapshot)
      }
    }
  }

}