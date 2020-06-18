package com.github.adamantcheese.model.entity.bookmark

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.github.adamantcheese.model.entity.chan.ChanBoardEntity
import com.github.adamantcheese.model.entity.chan.ChanThreadEntity

data class ThreadBookmarkFull(
  @ColumnInfo(name = ChanBoardEntity.SITE_NAME_COLUMN_NAME)
  val siteName: String,
  @ColumnInfo(name = ChanBoardEntity.BOARD_CODE_COLUMN_NAME)
  val boardCode: String,
  @ColumnInfo(name = ChanThreadEntity.THREAD_NO_COLUMN_NAME)
  val threadNo: Long,
  @Embedded
  val threadBookmarkEntity: ThreadBookmarkEntity
)