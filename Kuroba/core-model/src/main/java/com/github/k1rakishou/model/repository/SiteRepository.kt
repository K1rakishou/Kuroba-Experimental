package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.myAsync
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.common.Logger
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.ChanSiteData
import com.github.k1rakishou.model.source.local.SiteLocalSource
import com.github.k1rakishou.model.util.ensureBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class SiteRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val applicationScope: CoroutineScope,
  private val localSource: SiteLocalSource
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag SiteRepository"
  private val allSitesLoadedInitializer = SuspendableInitializer<Unit>("allSitesLoadedInitializer")

  suspend fun awaitUntilSitesLoaded() = allSitesLoadedInitializer.awaitUntilInitialized()

  @OptIn(ExperimentalTime::class)
  suspend fun initializeSites(allSiteDescriptors: Collection<SiteDescriptor>): ModularResult<List<ChanSiteData>> {
    return applicationScope.myAsync {
      val result = tryWithTransaction {
        ensureBackgroundThread()

        val (sites, duration) = measureTimedValue {
          localSource.createDefaults(allSiteDescriptors)

          return@measureTimedValue localSource.selectAllOrderedDesc()
        }

        logger.log(TAG, "initializeSites() -> ${sites.size} took $duration")
        return@tryWithTransaction sites
      }

      allSitesLoadedInitializer.initWithModularResult(result.mapValue { Unit })
      logger.log(TAG, "allSitesLoadedInitializer initialized")
      return@myAsync result
    }
  }

  suspend fun loadAllSites(): ModularResult<List<ChanSiteData>> {
    check(allSitesLoadedInitializer.isInitialized()) { "SiteRepository is not initialized" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.selectAllOrderedDesc()
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun persist(chanSiteDataList: Collection<ChanSiteData>): ModularResult<Unit> {
    check(allSitesLoadedInitializer.isInitialized()) { "SiteRepository is not initialized" }
    logger.log(TAG, "persist(chanSiteDataListCount=${chanSiteDataList.size})")

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val time = measureTime { localSource.persist(chanSiteDataList) }
        logger.log(TAG, "persist(${chanSiteDataList.size}) took $time")

        return@tryWithTransaction
      }
    }
  }

}