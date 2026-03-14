package com.github.k1rakishou.v2.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "kuroba_seen_settings",
  indices = [
    Index("owner_setting_key", unique = true)
  ]
)
data class KurobaSeenSettingEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = "owner_setting_key")
  val ownerKey: String,
  @ColumnInfo(name = "seen_on")
  val seenOn: Long = System.currentTimeMillis()
)