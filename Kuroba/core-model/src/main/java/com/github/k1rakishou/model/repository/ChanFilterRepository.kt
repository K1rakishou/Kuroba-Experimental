package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaMainDatabase
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.source.local.ChanFilterLocalSource
import kotlinx.coroutines.CoroutineScope

class ChanFilterRepository(
  database: KurobaMainDatabase,
  applicationScope: CoroutineScope,
  private val localSource: ChanFilterLocalSource
) : AbstractRepository(database, applicationScope) {
  private val TAG = "ChanFilterRepository"

  suspend fun loadAllFilters(): ModularResult<List<ChanFilter>> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.selectAll()
      }
    }
  }

  suspend fun createFilter(chanFilter: ChanFilter, order: Int): ModularResult<Long> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.createFilter(chanFilter, order)
      }
    }
  }

  suspend fun updateAllFilters(filters: List<ChanFilter>): ModularResult<Boolean> {
    return database.call {
      return@call tryWithTransaction {
        localSource.updateAllFilters(filters)
        return@tryWithTransaction true
      }
    }
  }

  suspend fun deleteFilter(filter: ChanFilter): ModularResult<Boolean> {
    return database.call {
      return@call tryWithTransaction {
        localSource.deleteFilter(filter)
        return@tryWithTransaction true
      }
    }
  }

  suspend fun deleteAll(): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        localSource.deleteAll()
      }
    }
  }

}