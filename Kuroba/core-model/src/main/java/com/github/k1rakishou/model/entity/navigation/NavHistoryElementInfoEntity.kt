package com.github.k1rakishou.model.entity.navigation

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import okhttp3.HttpUrl

@Entity(
  tableName = NavHistoryElementInfoEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = NavHistoryElementIdEntity::class,
      parentColumns = [NavHistoryElementIdEntity.ID_COLUMN_NAME],
      childColumns = [NavHistoryElementInfoEntity.OWNER_NAV_HISTORY_ID_COLUMN_NAME],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    )
  ]
)
data class NavHistoryElementInfoEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = OWNER_NAV_HISTORY_ID_COLUMN_NAME)
  val ownerNavHistoryId: Long,
  @ColumnInfo(name = THUMBNAIL_URL_COLUMN_NAME)
  val thumbnailUrl: HttpUrl,
  @ColumnInfo(name = TITLE_COLUMN_NAME)
  val title: String,
  @ColumnInfo(name = PINNED_COLUMN_NAME)
  val pinned: Boolean,
  @ColumnInfo(name = ELEMENT_ORDER_COLUMN_NAME)
  val order: Int
) {

  companion object {
    const val TABLE_NAME = "nav_history_element_info"

    const val OWNER_NAV_HISTORY_ID_COLUMN_NAME = "owner_nav_history_id"
    const val THUMBNAIL_URL_COLUMN_NAME = "thumbnail_url"
    const val TITLE_COLUMN_NAME = "title"
    const val ELEMENT_ORDER_COLUMN_NAME = "element_order"
    const val PINNED_COLUMN_NAME = "pinned"
  }
}