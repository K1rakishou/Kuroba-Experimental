package com.github.adamantcheese.model.entity.chan.site

import androidx.room.Embedded
import androidx.room.Relation

data class ChanSiteWithSettings(
  @Embedded
  val chanSiteIdEntity: ChanSiteIdEntity,
  @Relation(
    entity = ChanSiteEntity::class,
    parentColumn = ChanSiteIdEntity.SITE_NAME_COLUMN_NAME,
    entityColumn = ChanSiteEntity.OWNER_CHAN_SITE_NAME_COLUMN_NAME
  )
  val chanSiteEntity: ChanSiteEntity,
  @Relation(
    entity = ChanSiteSettingsEntity::class,
    parentColumn = ChanSiteIdEntity.SITE_NAME_COLUMN_NAME,
    entityColumn = ChanSiteSettingsEntity.OWNER_CHAN_SITE_NAME_COLUMN_NAME
  )
  val chanSiteSettingsEntity: ChanSiteSettingsEntity?
)