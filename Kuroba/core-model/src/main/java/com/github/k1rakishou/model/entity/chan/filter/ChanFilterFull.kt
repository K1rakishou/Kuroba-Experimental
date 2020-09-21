package com.github.k1rakishou.model.entity.chan.filter

import androidx.room.Embedded
import androidx.room.Relation

data class ChanFilterFull(
  @Embedded
  val chanFilterEntity: ChanFilterEntity,
  @Relation(
    entity = ChanFilterBoardConstraintEntity::class,
    parentColumn = ChanFilterEntity.FILTER_ID_COLUMN_NAME,
    entityColumn = ChanFilterBoardConstraintEntity.OWNER_FILTER_ID_COLUMN_NAME
  )
  val chanFilterBoardConstraintEntityList: List<ChanFilterBoardConstraintEntity>
)