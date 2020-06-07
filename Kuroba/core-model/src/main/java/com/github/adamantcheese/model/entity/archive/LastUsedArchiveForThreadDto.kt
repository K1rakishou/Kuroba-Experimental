package com.github.adamantcheese.model.entity.archive

import androidx.room.Embedded
import androidx.room.Relation

data class LastUsedArchiveForThreadDto(
  @Embedded
  val lastUsedArchiveForThreadRelationEntity: LastUsedArchiveForThreadRelationEntity,
  @Relation(
    entity = ThirdPartyArchiveInfoEntity::class,
    parentColumn = LastUsedArchiveForThreadRelationEntity.ARCHIVE_ID_COLUMN_NAME,
    entityColumn = ThirdPartyArchiveInfoEntity.ARCHIVE_ID_COLUMN_NAME
  )
  val thirdPartyArchiveInfoEntity: ThirdPartyArchiveInfoEntity
)