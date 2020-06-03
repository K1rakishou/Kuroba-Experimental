package com.github.adamantcheese.model.entity.navigation

import androidx.room.Embedded
import androidx.room.Relation

data class NavHistoryFullDto(
  @Embedded
  val navHistoryElementIdEntity: NavHistoryElementIdEntity,
  @Relation(
    entity = NavHistoryElementInfoEntity::class,
    parentColumn = NavHistoryElementIdEntity.ID_COLUMN_NAME,
    entityColumn = NavHistoryElementInfoEntity.OWNER_NAV_HISTORY_ID_COLUMN_NAME
  )
  val navHistoryElementInfoEntity: NavHistoryElementInfoEntity
)