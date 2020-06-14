package com.github.adamantcheese.model.entity.chan

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = ChanBoardEntity.TABLE_NAME,
  indices = [
    Index(
      name = ChanBoardEntity.SITE_NAME_INDEX_NAME,
      value = [ChanBoardEntity.SITE_NAME_COLUMN_NAME]
    ),
    Index(
      name = ChanBoardEntity.BOARD_CODE_INDEX_NAME,
      value = [ChanBoardEntity.BOARD_CODE_COLUMN_NAME]
    ),
    Index(
      name = ChanBoardEntity.BOARD_DESCRIPTOR_INDEX_NAME,
      value = [
        ChanBoardEntity.SITE_NAME_COLUMN_NAME,
        ChanBoardEntity.BOARD_CODE_COLUMN_NAME
      ],
      unique = true
    )
  ]
)
data class ChanBoardEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = BOARD_ID_COLUMN_NAME)
  var boardId: Long = 0L,
  @ColumnInfo(name = SITE_NAME_COLUMN_NAME)
  val siteName: String,
  @ColumnInfo(name = BOARD_CODE_COLUMN_NAME)
  val boardCode: String
) {

  companion object {
    const val TABLE_NAME = "chan_board"

    const val BOARD_ID_COLUMN_NAME = "board_id"
    const val SITE_NAME_COLUMN_NAME = "site_name"
    const val BOARD_CODE_COLUMN_NAME = "board_code"

    const val BOARD_DESCRIPTOR_INDEX_NAME = "${TABLE_NAME}_board_descriptor_idx"
    const val SITE_NAME_INDEX_NAME = "${TABLE_NAME}_site_name_idx"
    const val BOARD_CODE_INDEX_NAME = "${TABLE_NAME}_board_code_idx"
  }
}