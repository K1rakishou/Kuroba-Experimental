package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.myAsync
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.filter.ChanFilter
import com.github.adamantcheese.model.source.local.ChanFilterLocalSource
import kotlinx.coroutines.CoroutineScope

class ChanFilterRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val applicationScope: CoroutineScope,
  private val localSource: ChanFilterLocalSource
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag ChanFilterRepository"

  suspend fun loadAllFilters(): ModularResult<List<ChanFilter>> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.selectAll()
      }
    }
  }

  suspend fun createFilter(chanFilter: ChanFilter, order: Int): ModularResult<Long> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.createFilter(chanFilter, order)
      }
    }
  }

  suspend fun updateAllFilters(filters: List<ChanFilter>): ModularResult<Boolean> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        localSource.updateAllFilters(filters)
        return@tryWithTransaction true
      }
    }
  }

  suspend fun deleteFilter(filter: ChanFilter): ModularResult<Boolean> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        localSource.deleteFilter(filter)
        return@tryWithTransaction true
      }
    }
  }

}