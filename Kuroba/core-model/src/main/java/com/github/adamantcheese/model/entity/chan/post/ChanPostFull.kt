package com.github.adamantcheese.model.entity.chan.post

import androidx.room.Embedded
import androidx.room.Relation

data class ChanPostFull(
  @Embedded
  val chanPostIdEntity: ChanPostIdEntity,
  @Relation(
    entity = ChanPostEntity::class,
    parentColumn = ChanPostIdEntity.POST_ID_COLUMN_NAME,
    entityColumn = ChanPostEntity.CHAN_POST_ID_COLUMN_NAME
  )
  val chanPostEntity: ChanPostEntity
)