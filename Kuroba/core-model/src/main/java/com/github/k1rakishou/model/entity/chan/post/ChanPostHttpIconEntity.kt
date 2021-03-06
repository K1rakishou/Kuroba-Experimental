package com.github.k1rakishou.model.entity.chan.post

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import okhttp3.HttpUrl

@Entity(
  tableName = ChanPostHttpIconEntity.TABLE_NAME,
  primaryKeys = [
    ChanPostHttpIconEntity.ICON_URL_COLUMN_NAME,
    ChanPostHttpIconEntity.OWNER_POST_ID_COLUMN_NAME
  ],
  foreignKeys = [
    ForeignKey(
      entity = ChanPostEntity::class,
      parentColumns = [ChanPostEntity.CHAN_POST_ID_COLUMN_NAME],
      childColumns = [ChanPostHttpIconEntity.OWNER_POST_ID_COLUMN_NAME],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index(value = [ChanPostHttpIconEntity.OWNER_POST_ID_COLUMN_NAME])
  ]
)
data class ChanPostHttpIconEntity(
  @ColumnInfo(name = ICON_URL_COLUMN_NAME)
  val iconUrl: HttpUrl,
  @ColumnInfo(name = OWNER_POST_ID_COLUMN_NAME)
  val ownerPostId: Long,
  @ColumnInfo(name = ICON_NAME_COLUMN_NAME)
  val iconName: String
) {

  companion object {
    const val TABLE_NAME = "chan_post_http_icon"

    const val ICON_URL_COLUMN_NAME = "icon_url"
    const val OWNER_POST_ID_COLUMN_NAME = "owner_post_id"
    const val ICON_NAME_COLUMN_NAME = "icon_name"
  }
}