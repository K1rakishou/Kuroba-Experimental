package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.common.myAsync
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.navigation.NavHistoryElement
import kotlinx.coroutines.CoroutineScope

class HistoryNavigationRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val applicationScope: CoroutineScope
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag HistoryNavigationRepository"

  // TODO(KurobaEx): returned elements must be sorted by id in descending order
  suspend fun initialize(): ModularResult<List<NavHistoryElement>> {
    return applicationScope.myAsync {
      Try { emptyList<NavHistoryElement>() }
    }
  }

  suspend fun create(navHistoryElement: NavHistoryElement) {
    // TODO(KurobaEx):
  }

  suspend fun remove(navHistoryElement: NavHistoryElement) {
    // TODO(KurobaEx):
  }

  suspend fun persist(navHistoryStack: List<NavHistoryElement>) {
    // TODO(KurobaEx):
  }

}