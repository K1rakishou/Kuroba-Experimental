package com.github.adamantcheese.model.entity.chan

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = ChanSiteIdEntity.TABLE_NAME)
data class ChanSiteIdEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = SITE_NAME_COLUMN_NAME)
  val siteName: String
) {

  companion object {
    const val TABLE_NAME = "chan_site_id"

    const val SITE_NAME_COLUMN_NAME = "site_name"
  }

}