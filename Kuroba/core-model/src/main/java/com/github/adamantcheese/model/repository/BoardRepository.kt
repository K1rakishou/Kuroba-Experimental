package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.board.ChanBoard
import com.github.adamantcheese.model.source.local.BoardLocalSource
import kotlinx.coroutines.CoroutineScope

class BoardRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val applicationScope: CoroutineScope,
  private val localSource: BoardLocalSource
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag BoardRepository"

  suspend fun loadBoards(): ModularResult<List<ChanBoard>> {
    // TODO(KurobaEx):
    return ModularResult.Try { emptyList() }
  }

  companion object {
    private const val TAG = "BoardRepository"
  }
}