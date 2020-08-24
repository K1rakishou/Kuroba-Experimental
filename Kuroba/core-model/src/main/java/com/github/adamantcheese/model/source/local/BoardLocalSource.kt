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

    val boardMapPerSite = mutableMapOf<SiteDescriptor, Map<BoardDescriptor, Long>>()

    boardsOrdered.forEach { (siteDescriptor, boards) ->
      val boardCodes = boards.map { board -> board.boardCode() }
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

        return@mapNotNull ChanBoardMapper.toChanBoardEntity(boardId, index++, board)
      }

      entities
        .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
        .forEach { chunk -> chanBoardDao.createOrUpdateBoards(chunk) }
    }
  }

}