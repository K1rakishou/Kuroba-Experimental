package com.github.k1rakishou.model.entity.download

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.k1rakishou.model.data.download.ImageDownloadRequest
import okhttp3.HttpUrl
import org.joda.time.DateTime

@Entity(
  tableName = ImageDownloadRequestEntity.TABLE_NAME,
  indices = [
    Index(
      value = [ImageDownloadRequestEntity.UNIQUE_ID_COLUMN_NAME],
      unique = true
    ),
    Index(
      value = [ImageDownloadRequestEntity.IMAGE_SERVER_FILE_NAME_COLUMN_NAME],
      unique = true
    ),
    Index(
      value = [ImageDownloadRequestEntity.IMAGE_FULL_URL_COLUMN_NAME],
      unique = true
    ),
    Index(value = [ImageDownloadRequestEntity.CREATED_ON_COLUMN_NAME])
  ]
)
data class ImageDownloadRequestEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = UNIQUE_ID_COLUMN_NAME)
  val uniqueId: String,
  @ColumnInfo(name = IMAGE_SERVER_FILE_NAME_COLUMN_NAME)
  val imageServerFileName: String,
  @ColumnInfo(name = IMAGE_FULL_URL_COLUMN_NAME)
  val imageFullUrl: HttpUrl,
  @ColumnInfo(name = NEW_FILE_NAME_COLUMN_NAME)
  val newFileName: String?,
  @ColumnInfo(name = STATUS_COLUMN_NAME)
  val status: Int,
  @ColumnInfo(name = DUPLICATE_FILE_URI_COLUMN_NAME)
  val duplicateFileUri: Uri?,
  @ColumnInfo(name = DUPLICATES_RESOLUTION_COLUMN_NAME)
  val duplicatesResolution: Int,
  @ColumnInfo(name = CREATED_ON_COLUMN_NAME)
  val createdOn: DateTime
) {

  fun isQueued(): Boolean {
    return status == ImageDownloadRequest.Status.Queued.rawValue
  }

  companion object {
    const val TABLE_NAME = "image_download_request_entity"

    const val UNIQUE_ID_COLUMN_NAME = "unique_id"
    const val IMAGE_SERVER_FILE_NAME_COLUMN_NAME = "image_server_file_name"
    const val IMAGE_FULL_URL_COLUMN_NAME = "image_full_url"
    const val NEW_FILE_NAME_COLUMN_NAME = "new_file_name"
    const val STATUS_COLUMN_NAME = "status"
    const val DUPLICATE_FILE_URI_COLUMN_NAME = "duplicate_file_uri"
    const val DUPLICATES_RESOLUTION_COLUMN_NAME = "duplicates_resolution"
    const val CREATED_ON_COLUMN_NAME = "created_on"
  }
}