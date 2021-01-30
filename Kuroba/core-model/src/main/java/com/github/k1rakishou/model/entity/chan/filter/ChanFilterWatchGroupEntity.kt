package com.github.k1rakishou.model.entity.chan.filter

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkEntity

@Entity(
  tableName = ChanFilterWatchGroupEntity.TABLE_NAME,
  primaryKeys = [
    ChanFilterWatchGroupEntity.OWNER_CHAN_FILTER_DATABASE_ID_COLUMN_NAME,
    ChanFilterWatchGroupEntity.OWNER_THREAD_BOOKMARK_DATABASE_ID_COLUMN_NAME
  ],
  foreignKeys = [
    ForeignKey(
      entity = ChanFilterEntity::class,
      parentColumns = [ChanFilterEntity.FILTER_ID_COLUMN_NAME],
      childColumns = [ChanFilterWatchGroupEntity.OWNER_CHAN_FILTER_DATABASE_ID_COLUMN_NAME],
      onUpdate = ForeignKey.CASCADE,
      onDelete = ForeignKey.CASCADE
    ),
    ForeignKey(
      entity = ThreadBookmarkEntity::class,
      parentColumns = [ThreadBookmarkEntity.THREAD_BOOKMARK_ID_COLUMN_NAME],
      childColumns = [ChanFilterWatchGroupEntity.OWNER_THREAD_BOOKMARK_DATABASE_ID_COLUMN_NAME],
      onUpdate = ForeignKey.CASCADE,
      onDelete = ForeignKey.CASCADE
    ),
  ]
)
data class ChanFilterWatchGroupEntity(
  @ColumnInfo(name = OWNER_CHAN_FILTER_DATABASE_ID_COLUMN_NAME)
  val ownerChanFilterDatabaseId: Long,
  @ColumnInfo(name = OWNER_THREAD_BOOKMARK_DATABASE_ID_COLUMN_NAME)
  val ownerThreadBookmarkDatabaseId: Long
) {

  companion object {
    const val TABLE_NAME = "chan_filter_watch_group_entity"

    const val OWNER_CHAN_FILTER_DATABASE_ID_COLUMN_NAME = "owner_chan_filter_database_id"
    const val OWNER_THREAD_BOOKMARK_DATABASE_ID_COLUMN_NAME = "owner_thread_bookmark_database_id"

  }
}