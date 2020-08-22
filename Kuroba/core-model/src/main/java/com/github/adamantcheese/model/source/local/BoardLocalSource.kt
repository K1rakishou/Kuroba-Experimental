package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.board.ChanBoard
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.github.adamantcheese.model.mapper.ChanBoardMapper
import com.github.adamantcheese.model.source.cache.ChanDescriptorCache

class BoardLocalSource(
  database: KurobaDatabase,
  loggerTag: String,
  private val isDevFlavor: Boolean,
  private val logger: Logger,
  private val chanDescriptorCache: ChanDescriptorCache
) : AbstractLocalSource(database) {
  private val TAG = "$loggerTag BoardLocalSource"
  private val chanBoardDao = database.chanBoardDao()

  suspend fun selectAllActiveBoards(): Map<SiteDescriptor, List<ChanBoard>> {
    ensureInTransaction()

    val allActiveBoards = chanBoardDao.selectAllActiveBoards()

    allActiveBoards.forEach { chanBoardFull ->
      chanDescriptorCache.putBoardDescriptor(
        chanBoardFull.chanBoardIdEntity.boardId,
        chanBoardFull.chanBoardIdEntity.boardDescriptor()
      )
    }

    return allActiveBoards
      .mapNotNull { chanBoardFull -> ChanBoardMapper.fromChanBoardEntity(chanBoardFull) }
      .groupBy { it.boardDescriptor.siteDescriptor }
  }

  suspend fun activateDeactivateBoard(boardDescriptor: BoardDescriptor, activate: Boolean): Boolean {
    ensureInTransaction()

    val boardIdEntity = chanBoardDao.selectBoardId(
      boardDescriptor.siteName(),
      boardDescriptor.boardCode
    )

    if (boardIdEntity == null) {
      return false
    }

    return chanBoardDao.activateDeactivateBoard(boardIdEntity.boardId, activate) > 0
  }

  suspend fun persist(boardsOrdered: Map<SiteDescriptor, List<ChanBoard>>) {
    ensureInTransaction()

    boardsOrdered.forEach { (_, boards) ->
      val boardIdMap = chanDescriptorCache.getBoardIdByBoardDescriptors(
        boards.map { board -> board.boardDescriptor }
      )

      var index = 0

      val entities = boards.mapNotNull { board ->
        val boardId = boardIdMap[board.boardDescriptor]
          ?: return@mapNotNull null

        return@mapNotNull ChanBoardMapper.toChanBoardEntity(boardId, index++, board)
      }

      entities
        .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
        .forEach { chunk -> chanBoardDao.updateBoards(chunk) }
    }
  }

}