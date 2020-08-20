package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.myAsync
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.board.ChanBoard
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.github.adamantcheese.model.source.local.BoardLocalSource
import kotlinx.coroutines.CoroutineScope
import kotlin.time.ExperimentalTime
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
  suspend fun initialize(): ModularResult<Map<SiteDescriptor, List<ChanBoard>>> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val (boards, duration) = measureTimedValue {
          return@measureTimedValue localSource.selectAllActiveBoards()
        }

        logger.log(TAG, "initialize() -> ${boards.size} took $duration")
        return@tryWithTransaction boards
      }
    }
  }

  suspend fun activateBoard(boardDescriptor: BoardDescriptor) {
    TODO()
  }

  suspend fun deactivateBoard(boardDescriptor: BoardDescriptor) {
    TODO()
  }

  suspend fun updateBoards(boards: List<ChanBoard>) {
    TODO("Not yet implemented")
  }

  companion object {
    private const val TAG = "BoardRepository"
  }
}