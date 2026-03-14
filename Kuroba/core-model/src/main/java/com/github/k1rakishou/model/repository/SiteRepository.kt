package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.KurobaMainDatabase
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.ChanSiteData
import com.github.k1rakishou.model.source.local.SiteLocalSource
import com.github.k1rakishou.model.util.ensureBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class SiteRepository(
  database: KurobaMainDatabase,
  applicationScope: CoroutineScope,
  private val localSource: SiteLocalSource
) : AbstractRepository(database, applicationScope) {
  private val TAG = "SiteRepository"
  private val allSitesLoadedInitializer = SuspendableInitializer<Unit>("allSitesLoadedInitializer")

  suspend fun initialize(allSiteDescriptors: Collection<SiteDescriptor>): ModularResult<List<ChanSiteData>> {
    return database.call {
      val result = tryWithTransaction {
        ensureBackgroundThread()

        val (sites, duration) = measureTimedValue {
          localSource.createDefaults(allSiteDescriptors)

          return@measureTimedValue localSource.selectAllOrderedDesc()
        }

        Logger.d(TAG, "initializeSites() -> ${sites.size} took $duration")
        return@tryWithTransaction sites
      }

      allSitesLoadedInitializer.initWithModularResult(result.mapValue { Unit })
      Logger.d(TAG, "allSitesLoadedInitializer initialized")
      return@call result
    }
  }

  suspend fun persist(chanSiteDataList: Collection<ChanSiteData>): ModularResult<Unit> {
    check(allSitesLoadedInitializer.isInitialized()) { "SiteRepository is not initialized" }
    Logger.d(TAG, "persist(chanSiteDataListCount=${chanSiteDataList.size})")

    return database.call {
      return@call tryWithTransaction {
        val time = measureTime { localSource.persist(chanSiteDataList) }
        Logger.d(TAG, "persist(${chanSiteDataList.size}) took $time")

        return@tryWithTransaction
      }
    }
  }

}