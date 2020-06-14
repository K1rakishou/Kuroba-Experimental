package com.github.adamantcheese.model.entity.archive

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = ThirdPartyArchiveInfoEntity.TABLE_NAME,
  indices = [
    Index(
      name = ThirdPartyArchiveInfoEntity.ARCHIVE_DOMAIN_INDEX_NAME,
      value = [ThirdPartyArchiveInfoEntity.ARCHIVE_DOMAIN_COLUMN_NAME],
      unique = true
    )
  ]
)
data class ThirdPartyArchiveInfoEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = ARCHIVE_ID_COLUMN_NAME)
  var archiveId: Long,
  @ColumnInfo(name = ARCHIVE_DOMAIN_COLUMN_NAME)
  val archiveDomain: String,
  @ColumnInfo(name = ARCHIVE_ENABLED_COLUMN_NAME)
  val enabled: Boolean
) {

  companion object {
    const val TABLE_NAME = "third_party_archive_info"

    const val ARCHIVE_ID_COLUMN_NAME = "archive_id"
    const val ARCHIVE_DOMAIN_COLUMN_NAME = "archive_domain"
    const val ARCHIVE_ENABLED_COLUMN_NAME = "archive_enabled"

    const val ARCHIVE_DOMAIN_INDEX_NAME = "${TABLE_NAME}_archive_domain_idx"
  }
}