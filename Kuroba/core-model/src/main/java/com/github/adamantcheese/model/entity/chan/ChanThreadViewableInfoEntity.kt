package com.github.adamantcheese.model.entity.chan

import androidx.room.*

@Entity(
  tableName = ChanThreadViewableInfoEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ChanThreadEntity::class,
      parentColumns = [ChanThreadEntity.THREAD_ID_COLUMN_NAME],
      childColumns = [ChanThreadViewableInfoEntity.OWNER_THREAD_ID_COLUMN_NAME],
      onUpdate = ForeignKey.CASCADE,
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index(value = [ChanThreadViewableInfoEntity.OWNER_THREAD_ID_COLUMN_NAME])
  ]
)
data class ChanThreadViewableInfoEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = CHAN_THREAD_VIEWABLE_INFO_ID_COLUMN_NAME)
  var chanThreadViewableInfoId: Long,
  @ColumnInfo(name = OWNER_THREAD_ID_COLUMN_NAME)
  val ownerThreadId: Long,
  @ColumnInfo(name = LIST_VIEW_INDEX_COLUMN_NAME)
  val listViewIndex: Int = 0,
  @ColumnInfo(name = LIST_VIEW_TOP_COLUMN_NAME)
  val listViewTop: Int = 0,
  @ColumnInfo(name = LAST_VIEWED_POST_NO_COLUMN_NAME)
  val lastViewedPostNo: Long = -1L,
  @ColumnInfo(name = LAST_LOADED_POST_NO_COLUMN_NAME)
  val lastLoadedPostNo: Long = -1L,
  @ColumnInfo(name = MARKED_POST_NO_COLUMN_NAME)
  val markedPostNo: Long = -1L
) {

  companion object {
    const val TABLE_NAME = "chan_thread_viewable_info"

    const val CHAN_THREAD_VIEWABLE_INFO_ID_COLUMN_NAME = "chan_thread_viewable_info_id"
    const val OWNER_THREAD_ID_COLUMN_NAME = "owner_thread_id"
    const val LIST_VIEW_INDEX_COLUMN_NAME = "list_view_index"
    const val LIST_VIEW_TOP_COLUMN_NAME = "list_view_top"
    const val LAST_VIEWED_POST_NO_COLUMN_NAME = "last_viewed_post_no"
    const val LAST_LOADED_POST_NO_COLUMN_NAME = "last_loaded_post_no"
    const val MARKED_POST_NO_COLUMN_NAME = "marked_post_no"

  }
}