package com.github.adamantcheese.model.entity.chan.board

import androidx.room.Embedded
import androidx.room.Relation

data class ChanBoardFull(
  @Embedded
  val chanBoardIdEntity: ChanBoardIdEntity,
  @Relation(
    entity = ChanBoardEntity::class,
    parentColumn = ChanBoardIdEntity.BOARD_ID_COLUMN_NAME,
    entityColumn = ChanBoardEntity.OWNER_CHAN_BOARD_ID_COLUMN_NAME
  )
  val chanBoardEntity: ChanBoardEntity
)