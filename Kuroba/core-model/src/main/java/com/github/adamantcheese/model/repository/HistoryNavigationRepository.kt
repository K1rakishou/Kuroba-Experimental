package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.myAsync
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.navigation.NavHistoryElement
import com.github.adamantcheese.model.source.local.NavHistoryLocalSource
import kotlinx.coroutines.CoroutineScope

class HistoryNavigationRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val applicationScope: CoroutineScope,
  private val localSource: NavHistoryLocalSource
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag HistoryNavigationRepository"

  suspend fun initialize(maxCount: Int): ModularResult<List<NavHistoryElement>> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.selectAll(maxCount)
      }
    }
  }

  suspend fun persist(navHistoryStack: List<NavHistoryElement>): ModularResult<Unit> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.persist(navHistoryStack)
      }
    }
  }

}