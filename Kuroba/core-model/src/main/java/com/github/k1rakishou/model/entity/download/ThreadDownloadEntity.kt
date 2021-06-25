package com.github.k1rakishou.model.entity.download

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.joda.time.DateTime

@Entity(
  tableName = ThreadDownloadEntity.TABLE_NAME,
  indices = [
    Index(value = [ThreadDownloadEntity.CREATED_ON_COLUMN_NAME])
  ]
)
data class ThreadDownloadEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = OWNER_THREAD_DATABASE_ID_COLUMN_NAME)
  val ownerThreadDatabaseId: Long,
  @ColumnInfo(name = SITE_NAME_COLUMN_NAME)
  val siteName: String,
  @ColumnInfo(name = BOARD_CODE_COLUMN_NAME)
  val boardCode: String,
  @ColumnInfo(name = THREAD_NO_COLUMN_NAME)
  val threadNo: Long,
  @ColumnInfo(name = DOWNLOAD_MEDIA_COLUMN_NAME)
  val downloadMedia: Boolean,
  @ColumnInfo(name = STATUS_COLUMN_NAME)
  val status: Int,
  @ColumnInfo(name = CREATED_ON_COLUMN_NAME)
  val createdOn: DateTime,
  @ColumnInfo(name = THREAD_THUMBNAIL_URL_COLUMN_NAME)
  val threadThumbnailUrl: String?,
  @ColumnInfo(name = LAST_UPDATE_TIME_COLUMN_NAME)
  val lastUpdateTime: DateTime?,
  @ColumnInfo(name = DOWNLOAD_RESULT_MSG_COLUMN_NAME)
  val downloadResultMsg: String?
) {

  companion object {
    const val TABLE_NAME = "thread_download_entity"

    const val OWNER_THREAD_DATABASE_ID_COLUMN_NAME = "owner_thread_database_id"
    const val STATUS_COLUMN_NAME = "status"
    const val DOWNLOAD_MEDIA_COLUMN_NAME = "download_media"
    const val SITE_NAME_COLUMN_NAME = "site_name"
    const val BOARD_CODE_COLUMN_NAME = "board_code"
    const val THREAD_NO_COLUMN_NAME = "thread_no"
    const val CREATED_ON_COLUMN_NAME = "created_on"
    const val THREAD_THUMBNAIL_URL_COLUMN_NAME = "thread_thumbnail_url"
    const val LAST_UPDATE_TIME_COLUMN_NAME = "last_update_time"
    const val DOWNLOAD_RESULT_MSG_COLUMN_NAME = "download_result_msg"

  }
}