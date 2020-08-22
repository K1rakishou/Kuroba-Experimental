package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.SuspendableInitializer
import com.github.adamantcheese.common.myAsync
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.github.adamantcheese.model.data.site.ChanSiteData
import com.github.adamantcheese.model.source.local.SiteLocalSource
import kotlinx.coroutines.CoroutineScope
import kotlin.time.ExperimentalTime
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
  suspend fun initializedSites(allSiteDescriptors: Collection<SiteDescriptor>): ModularResult<List<ChanSiteData>> {
    return applicationScope.myAsync {
      val result = tryWithTransaction {
        val (sites, duration) = measureTimedValue {
          localSource.createDefaults(allSiteDescriptors)

          return@measureTimedValue localSource.selectAllOrderedDesc()
        }

        logger.log(TAG, "initializedSites() -> ${sites.size} took $duration")
        return@tryWithTransaction sites
      }

      allSitesLoadedInitializer.initWithModularResult(result.mapValue { Unit })
      return@myAsync result
    }
  }

  suspend fun loadAllSites(): ModularResult<List<ChanSiteData>> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.selectAllOrderedDesc()
      }
    }
  }

  suspend fun persist(chanSiteDataList: Collection<ChanSiteData>): ModularResult<Unit> {
    logger.log(TAG, "persist(chanSiteDataListCount=${chanSiteDataList.size})")

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.persist(chanSiteDataList)
      }
    }
  }

}