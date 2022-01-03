package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.id.BoardDBId
import com.github.k1rakishou.model.mapper.ChanBoardMapper
import com.github.k1rakishou.model.source.cache.ChanDescriptorCache

class BoardLocalSource(
  database: KurobaDatabase,
  private val isDevFlavor: Boolean,
  private val chanDescriptorCache: ChanDescriptorCache
) : AbstractLocalSource(database) {
  private val TAG = "BoardLocalSource"
  private val chanBoardDao = database.chanBoardDao()

  suspend fun selectAllBoards(): Map<SiteDescriptor, List<ChanBoard>> {
    ensureInTransaction()
    val allActiveBoards = chanBoardDao.selectAllBoards()

    allActiveBoards.forEach { chanBoardFull ->
      chanDescriptorCache.putBoardDescriptor(
        BoardDBId(chanBoardFull.chanBoardIdEntity.boardId),
        chanBoardFull.chanBoardIdEntity.boardDescriptor()
      )
    }

    return allActiveBoards
      .mapNotNull { chanBoardFull -> ChanBoardMapper.fromChanBoardEntity(chanBoardFull) }
      .groupBy { it.boardDescriptor.siteDescriptor }
  }

  suspend fun activateDeactivateBoards(
    siteDescriptor: SiteDescriptor,
    boardDescriptors: Collection<BoardDescriptor>,
    activate: Boolean
  ): Boolean {
    ensureInTransaction()

    val boardIdEntities = chanBoardDao.selectManyBoardIdEntities(
      siteDescriptor.siteName,
      boardDescriptors.map { boardDescriptor -> boardDescriptor.boardCode }
    )

    if (boardIdEntities.isEmpty()) {
      return false
    }

    chanBoardDao.activateDeactivateBoards(
      boardIdEntities.map { chanBoardIdEntity -> chanBoardIdEntity.boardId },
      activate
    )

    return true
  }

  suspend fun persist(boardsOrdered: Map<SiteDescriptor, List<ChanBoard>>) {
    ensureInTransaction()

    val boardMapPerSite = mutableMapOf<SiteDescriptor, Map<BoardDescriptor, BoardDBId>>()

    boardsOrdered.forEach { (siteDescriptor, boards) ->
      val boardCodes = boards.mapNotNull { board ->
        if (board.synthetic) {
          return@mapNotNull null
        }

        return@mapNotNull board.boardCode()
      }

      if (boardCodes.isEmpty()) {
        return@forEach
      }

      val boardIdMap = chanBoardDao.createNewBoardIdEntities(
        siteDescriptor.siteName,
        boardCodes
      )

      boardMapPerSite[siteDescriptor] = boardIdMap
    }

    if (boardMapPerSite.isEmpty()) {
      return
    }

    boardMapPerSite.forEach { (_, boardIdMap) ->
      chanDescriptorCache.putManyBoardDescriptors(boardIdMap)
    }

    boardsOrdered.forEach { (siteDescriptor, boards) ->
      val boardIdMap = boardMapPerSite[siteDescriptor]
        ?: return@forEach

      var index = 0

      val entities = boards.mapNotNull { board ->
        val boardId = boardIdMap[board.boardDescriptor]
          ?: return@mapNotNull null

        val boardOrder = if (board.active) {
          index++
        } else {
          -1
        }

        return@mapNotNull ChanBoardMapper.toChanBoardEntity(boardId.id, boardOrder, board)
      }

      entities
        .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
        .forEach { chunk -> chanBoardDao.createOrUpdateBoards(chunk) }
    }
  }

}