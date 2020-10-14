package com.github.k1rakishou.model.entity.bookmark

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import com.github.k1rakishou.model.entity.chan.board.ChanBoardIdEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity

data class ThreadBookmarkGroupWithEntries(
  @Embedded
  val threadBookmarkGroupEntity: ThreadBookmarkGroupEntity,
  @Relation(
    entity = ThreadBookmarkGroupEntryEntity::class,
    parentColumn = ThreadBookmarkGroupEntity.GROUP_ID_COLUMN_NAME,
    entityColumn = ThreadBookmarkGroupEntryEntity.OWNER_GROUP_ID_COLUMN_NAME
  )
  val threadBookmarkGroupEntryEntities: List<ThreadBookmarkGroupEntryEntity>
)

data class BookmarkThreadDescriptor(
  @ColumnInfo(name = ThreadBookmarkEntity.THREAD_BOOKMARK_ID_COLUMN_NAME)
  val ownerBookmarkId: Long,
  @ColumnInfo(name = ChanBoardIdEntity.OWNER_SITE_NAME_COLUMN_NAME)
  val siteName: String,
  @ColumnInfo(name = ChanBoardIdEntity.BOARD_CODE_COLUMN_NAME)
  val boardCode: String,
  @ColumnInfo(name = ChanThreadEntity.THREAD_NO_COLUMN_NAME)
  val threadNo: Long
)