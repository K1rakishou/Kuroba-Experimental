package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaMainDatabase
import com.github.k1rakishou.model.data.filter.ChanFilterWatchGroup
import com.github.k1rakishou.model.source.local.ChanFilterWatchLocalSource
import kotlinx.coroutines.CoroutineScope

class ChanFilterWatchRepository(
  database: KurobaMainDatabase,
  applicationScope: CoroutineScope,
  private val localSource: ChanFilterWatchLocalSource
) : AbstractRepository(database, applicationScope) {
  private val TAG = "ChanFilterWatchRepository"

  suspend fun createFilterWatchGroups(watchGroups: List<ChanFilterWatchGroup>): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        localSource.createFilterWatchGroups(watchGroups)
      }
    }
  }

  suspend fun getFilterWatchGroupsByFilterId(filterId: Long): ModularResult<List<ChanFilterWatchGroup>> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.getFilterWatchGroupsByFilterId(filterId)
      }
    }
  }

  suspend fun getFilterWatchGroups(): ModularResult<List<ChanFilterWatchGroup>> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.getFilterWatchGroups()
      }
    }
  }

  suspend fun clearFilterWatchGroups(): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        localSource.clearFilterWatchGroups()
      }
    }
  }

}