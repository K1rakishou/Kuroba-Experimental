package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.entity.chan.board.ChanBoardEntity
import com.github.k1rakishou.model.entity.chan.board.ChanBoardFull

object ChanBoardMapper {

  fun fromChanBoardEntity(chanBoardFull: ChanBoardFull?): ChanBoard? {
    if (chanBoardFull == null) {
      return null
    }

    val boardDescriptor = BoardDescriptor.create(
      chanBoardFull.chanBoardIdEntity.ownerSiteName,
      chanBoardFull.chanBoardIdEntity.boardCode
    )

    val boardOrder = if (chanBoardFull.chanBoardEntity.boardOrder >= 0) {
      chanBoardFull.chanBoardEntity.boardOrder
    } else {
      null
    }

    return ChanBoard(
      boardDescriptor = boardDescriptor,
      active = chanBoardFull.chanBoardEntity.active,
      order = boardOrder,
      name = chanBoardFull.chanBoardEntity.name,
      perPage = chanBoardFull.chanBoardEntity.perPage,
      pages = chanBoardFull.chanBoardEntity.pages,
      maxFileSize = chanBoardFull.chanBoardEntity.maxFileSize,
      maxWebmSize = chanBoardFull.chanBoardEntity.maxWebmSize,
      maxMediaWidth = chanBoardFull.chanBoardEntity.maxMediaWidth,
      maxMediaHeight = chanBoardFull.chanBoardEntity.maxMediaHeight,
      maxCommentChars = chanBoardFull.chanBoardEntity.maxCommentChars,
      bumpLimit = chanBoardFull.chanBoardEntity.bumpLimit,
      imageLimit = chanBoardFull.chanBoardEntity.imageLimit,
      cooldownThreads = chanBoardFull.chanBoardEntity.cooldownThreads,
      cooldownReplies = chanBoardFull.chanBoardEntity.cooldownReplies,
      cooldownImages = chanBoardFull.chanBoardEntity.cooldownImages,
      customSpoilers = chanBoardFull.chanBoardEntity.customSpoilers,
      description = chanBoardFull.chanBoardEntity.description,
      workSafe = chanBoardFull.chanBoardEntity.workSafe,
      spoilers = chanBoardFull.chanBoardEntity.spoilers,
      userIds = chanBoardFull.chanBoardEntity.userIds,
      countryFlags = chanBoardFull.chanBoardEntity.countryFlags,
      isUnlimitedCatalog = chanBoardFull.chanBoardEntity.isUnlimitedCatalog,
    )
  }

  fun toChanBoardEntity(boardDatabaseId: Long, order: Int?, board: ChanBoard): ChanBoardEntity {
    val boardOrder = (order ?: board.order) ?: -1

    return ChanBoardEntity(
      ownerChanBoardId = boardDatabaseId,
      active = board.active,
      boardOrder = boardOrder,
      name = board.name,
      perPage = board.perPage,
      pages = board.pages,
      maxFileSize = board.maxFileSize,
      maxWebmSize = board.maxWebmSize,
      maxMediaWidth = board.maxMediaWidth,
      maxMediaHeight = board.maxMediaHeight,
      maxCommentChars = board.maxCommentChars,
      bumpLimit = board.bumpLimit,
      imageLimit = board.imageLimit,
      cooldownThreads = board.cooldownThreads,
      cooldownReplies = board.cooldownReplies,
      cooldownImages = board.cooldownImages,
      customSpoilers = board.customSpoilers,
      description = board.description,
      workSafe = board.workSafe,
      spoilers = board.spoilers,
      userIds = board.userIds,
      countryFlags = board.countryFlags,
      isUnlimitedCatalog = board.isUnlimitedCatalog,
    )
  }

}