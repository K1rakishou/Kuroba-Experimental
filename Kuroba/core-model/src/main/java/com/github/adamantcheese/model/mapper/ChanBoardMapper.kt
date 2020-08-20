package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.board.ChanBoard
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.github.adamantcheese.model.entity.chan.ChanBoardFull

object ChanBoardMapper {

  fun fromChanBoardEntity(chanBoardFull: ChanBoardFull?): ChanBoard? {
    if (chanBoardFull == null) {
      return null
    }

    val boardDescriptor = BoardDescriptor(
      SiteDescriptor(chanBoardFull.chanBoardIdEntity.ownerSiteName),
      chanBoardFull.chanBoardIdEntity.boardCode
    )

    return ChanBoard(
      boardDescriptor = boardDescriptor,
      order = chanBoardFull.chanBoardEntity.order,
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
      saved = chanBoardFull.chanBoardEntity.saved,
      workSafe = chanBoardFull.chanBoardEntity.workSafe,
      spoilers = chanBoardFull.chanBoardEntity.spoilers,
      userIds = chanBoardFull.chanBoardEntity.userIds,
      codeTags = chanBoardFull.chanBoardEntity.codeTags,
      preuploadCaptcha = chanBoardFull.chanBoardEntity.preuploadCaptcha,
      countryFlags = chanBoardFull.chanBoardEntity.countryFlags,
      mathTags = chanBoardFull.chanBoardEntity.mathTags,
      archive = chanBoardFull.chanBoardEntity.archive,
    )
  }

}