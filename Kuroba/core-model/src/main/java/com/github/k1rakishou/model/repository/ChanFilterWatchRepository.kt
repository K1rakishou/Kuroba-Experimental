package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.myAsync
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
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        localSource.createFilterWatchGroups(watchGroups)
      }
    }
  }

  suspend fun getFilterWatchGroups(): ModularResult<List<ChanFilterWatchGroup>> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.getFilterWatchGroups()
      }
    }
  }

  suspend fun clearFilterWatchGroups(): ModularResult<Unit> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        localSource.clearFilterWatchGroups()
      }
    }
  }

}