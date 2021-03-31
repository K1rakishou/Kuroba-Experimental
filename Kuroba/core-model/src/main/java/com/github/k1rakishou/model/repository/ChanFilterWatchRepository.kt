package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.filter.ChanFilterWatchGroup
import com.github.k1rakishou.model.source.local.ChanFilterWatchLocalSource
import kotlinx.coroutines.CoroutineScope

class ChanFilterWatchRepository(
  database: KurobaDatabase,
  private val applicationScope: CoroutineScope,
  private val localSource: ChanFilterWatchLocalSource
) : AbstractRepository(database) {
  private val TAG = "ChanFilterWatchRepository"

  suspend fun createFilterWatchGroups(watchGroups: List<ChanFilterWatchGroup>): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        localSource.createFilterWatchGroups(watchGroups)
      }
    }
  }

  suspend fun getFilterWatchGroupsByFilterId(filterId: Long): ModularResult<List<ChanFilterWatchGroup>> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.getFilterWatchGroupsByFilterId(filterId)
      }
    }
  }

  suspend fun getFilterWatchGroups(): ModularResult<List<ChanFilterWatchGroup>> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.getFilterWatchGroups()
      }
    }
  }

  suspend fun clearFilterWatchGroups(): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        localSource.clearFilterWatchGroups()
      }
    }
  }

}