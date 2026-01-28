package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.source.local.BoardLocalSource
import kotlinx.coroutines.CoroutineScope
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class BoardRepository(
  database: KurobaDatabase,
  applicationScope: CoroutineScope,
  private val localSource: BoardLocalSource
) : AbstractRepository(database, applicationScope) {
  private val TAG = "BoardRepository"

  suspend fun loadAllBoards(): ModularResult<Map<SiteDescriptor, List<ChanBoard>>> {
    return database.call {
      return@call tryWithTransaction {
        val (boards, duration) = measureTimedValue {
          return@measureTimedValue localSource.selectAllBoards()
        }

        val totalLoadedBoards = boards.values.sumOf { siteBoards -> siteBoards.size }
        Logger.d(TAG, "loadAllBoards() -> ${totalLoadedBoards} took $duration")
        return@tryWithTransaction boards
      }
    }
  }

  suspend fun activateDeactivateBoards(
    siteDescriptor: SiteDescriptor,
    boardDescriptors: Collection<BoardDescriptor>,
    activate: Boolean
  ): ModularResult<Boolean> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.activateDeactivateBoards(
          siteDescriptor,
          boardDescriptors,
          activate
        )
      }
    }
  }

  suspend fun persist(boardsOrdered: Map<SiteDescriptor, List<ChanBoard>>): ModularResult<Unit> {
    if (boardsOrdered.isEmpty()) {
      return ModularResult.value(Unit)
    }

    return database.call {
      return@call tryWithTransaction {
        val time = measureTime { localSource.persist(boardsOrdered) }

        val boardsCountTotal = boardsOrdered.values.sumOf { boards -> boards.size }
        Logger.d(TAG, "persist($boardsCountTotal) took $time")

        return@tryWithTransaction
      }
    }
  }
}