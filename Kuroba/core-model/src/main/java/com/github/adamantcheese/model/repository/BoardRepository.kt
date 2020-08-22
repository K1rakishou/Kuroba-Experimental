package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.myAsync
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.board.ChanBoard
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.github.adamantcheese.model.source.local.BoardLocalSource
import com.github.adamantcheese.model.util.ensureBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class BoardRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val applicationScope: CoroutineScope,
  private val localSource: BoardLocalSource
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag BoardRepository"

  @OptIn(ExperimentalTime::class)
  suspend fun loadAllBoards(): ModularResult<Map<SiteDescriptor, List<ChanBoard>>> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        ensureBackgroundThread()

        val (boards, duration) = measureTimedValue {
          return@measureTimedValue localSource.selectAllActiveBoards()
        }

        logger.log(TAG, "loadAllBoards() -> ${boards.size} took $duration")
        return@tryWithTransaction boards
      }
    }
  }

  suspend fun activateDeactivateBoard(boardDescriptor: BoardDescriptor, activate: Boolean): ModularResult<Boolean> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.activateDeactivateBoard(boardDescriptor, activate)
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun persist(boardsOrdered: Map<SiteDescriptor, List<ChanBoard>>): ModularResult<Unit> {
    if (boardsOrdered.isEmpty()) {
      return ModularResult.value(Unit)
    }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val time = measureTime { localSource.persist(boardsOrdered) }

        val boardsCountTotal = boardsOrdered.values.sumBy { boards -> boards.size }
        logger.log(TAG, "persist($boardsCountTotal) took $time")

        return@tryWithTransaction
      }
    }
  }
}