package com.github.k1rakishou.v2.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "kuroba_settings",
  indices = [
    Index("setting_key", unique = true)
  ]
)
class KurobaSettingEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = "setting_key")
  val key: String,
  @ColumnInfo(name = "setting_value")
  val value: ByteArray?,
  @ColumnInfo(name = "backupable")
  val backupable: Boolean,
  @ColumnInfo(name = "created_on")
  val createdOn: Long = System.currentTimeMillis(),
  @ColumnInfo(name = "last_accessed_on")
  val lastAccessedOn: Long = System.currentTimeMillis()
)