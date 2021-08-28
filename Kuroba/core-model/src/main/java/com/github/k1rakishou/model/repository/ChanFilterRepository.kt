package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.source.local.ChanFilterLocalSource
import kotlinx.coroutines.CoroutineScope

class ChanFilterRepository(
  database: KurobaDatabase,
  private val applicationScope: CoroutineScope,
  private val localSource: ChanFilterLocalSource
) : AbstractRepository(database) {
  private val TAG = "ChanFilterRepository"

  suspend fun loadAllFilters(): ModularResult<List<ChanFilter>> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.selectAll()
      }
    }
  }

  suspend fun createFilter(chanFilter: ChanFilter, order: Int): ModularResult<Long> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.createFilter(chanFilter, order)
      }
    }
  }

  suspend fun updateAllFilters(filters: List<ChanFilter>): ModularResult<Boolean> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        localSource.updateAllFilters(filters)
        return@tryWithTransaction true
      }
    }
  }

  suspend fun deleteFilter(filter: ChanFilter): ModularResult<Boolean> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        localSource.deleteFilter(filter)
        return@tryWithTransaction true
      }
    }
  }

  suspend fun deleteAll(): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        localSource.deleteAll()
      }
    }
  }

}