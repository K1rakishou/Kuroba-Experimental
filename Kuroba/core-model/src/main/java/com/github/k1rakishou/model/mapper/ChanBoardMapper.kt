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
      // We don't persist synthetic boards so we assume all boards coming from the DB are not synthetic
      synthetic = false,
      order = boardOrder,
      name = chanBoardFull.chanBoardEntity.name,
      perPage = chanBoardFull.chanBoardEntity.perPage,
      pages = chanBoardFull.chanBoardEntity.pages,
      maxFileSize = chanBoardFull.chanBoardEntity.maxFileSize,
      maxWebmSize = chanBoardFull.chanBoardEntity.maxWebmSize,
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
      codeTags = chanBoardFull.chanBoardEntity.codeTags,
      preuploadCaptcha = chanBoardFull.chanBoardEntity.preuploadCaptcha,
      countryFlags = chanBoardFull.chanBoardEntity.countryFlags,
      mathTags = chanBoardFull.chanBoardEntity.mathTags,
      archive = chanBoardFull.chanBoardEntity.archive,
      isUnlimitedCatalog = chanBoardFull.chanBoardEntity.isUnlimitedCatalog,
    )
  }

  fun toChanBoardEntity(boardDatabaseId: Long, order: Int?, board: ChanBoard): ChanBoardEntity {
    require(!board.synthetic) { "Cannot persist synthetic boards! board: ${board}" }

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
      codeTags = board.codeTags,
      preuploadCaptcha = board.preuploadCaptcha,
      countryFlags = board.countryFlags,
      mathTags = board.mathTags,
      archive = board.archive,
      isUnlimitedCatalog = board.isUnlimitedCatalog,
    )
  }

  fun merge(prevBoard: ChanBoardEntity, board: ChanBoard): ChanBoardEntity {
    return ChanBoardEntity(
      ownerChanBoardId = prevBoard.ownerChanBoardId,
      // Be careful here, we don't want to overwrite the "active" flag here because it will most
      // likely always be false here. We want to use one from the DB when updating a board.
      active = prevBoard.active,
      // Same with orders, prefer the database order.
      boardOrder = prevBoard.boardOrder,
      name = board.name,
      perPage = board.perPage,
      pages = board.pages,
      maxFileSize = board.maxFileSize,
      maxWebmSize = board.maxWebmSize,
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
      codeTags = board.codeTags,
      preuploadCaptcha = board.preuploadCaptcha,
      countryFlags = board.countryFlags,
      mathTags = board.mathTags,
      archive = board.archive,
      isUnlimitedCatalog = board.isUnlimitedCatalog,
    )
  }

}