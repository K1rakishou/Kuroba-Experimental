package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.navigation.NavHistoryElement
import com.github.k1rakishou.model.source.local.NavHistoryLocalSource
import com.github.k1rakishou.model.util.ensureBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class HistoryNavigationRepository(
  database: KurobaDatabase,
  private val applicationScope: CoroutineScope,
  private val localSource: NavHistoryLocalSource
) : AbstractRepository(database) {
  private val TAG = "HistoryNavigationRepository"

  @OptIn(ExperimentalTime::class)
  suspend fun initialize(maxCount: Int): ModularResult<List<NavHistoryElement>> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        ensureBackgroundThread()

        val (navHistoryStack, duration) = measureTimedValue {
          return@measureTimedValue localSource.selectAll(maxCount)
        }

        Logger.d(TAG, "initialize() -> ${navHistoryStack.size} took $duration")
        return@tryWithTransaction navHistoryStack
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun persist(navHistoryStack: List<NavHistoryElement>): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        val (result, duration) = measureTimedValue {
          return@measureTimedValue localSource.persist(navHistoryStack)
        }

        Logger.d(TAG, "persist(${navHistoryStack.size}) took $duration")
        return@tryWithTransaction result
      }
    }
  }

  suspend fun getFirstNavElement(): ModularResult<NavHistoryElement?> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.getFirstNavElement()
      }
    }
  }

  suspend fun getFirstCatalogNavElement(): ModularResult<NavHistoryElement?> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.getFirstCatalogNavElement()
      }
    }
  }

  suspend fun getFirstThreadNavElement(): ModularResult<NavHistoryElement?> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.getFirstThreadNavElement()
      }
    }
  }

}