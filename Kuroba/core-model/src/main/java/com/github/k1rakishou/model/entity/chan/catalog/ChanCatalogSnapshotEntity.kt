package com.github.k1rakishou.model.entity.chan.catalog

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import com.github.k1rakishou.model.entity.chan.board.ChanBoardIdEntity

@Entity(
  tableName = ChanCatalogSnapshotEntity.TABLE_NAME,
  primaryKeys = [
    ChanCatalogSnapshotEntity.OWNER_BOARD_ID_COLUMN_NAME,
    ChanCatalogSnapshotEntity.THREAD_NO_COLUMN_NAME
  ],
  foreignKeys = [
    ForeignKey(
      entity = ChanBoardIdEntity::class,
      parentColumns = [ChanBoardIdEntity.BOARD_ID_COLUMN_NAME],
      childColumns = [ChanCatalogSnapshotEntity.OWNER_BOARD_ID_COLUMN_NAME],
      onUpdate = ForeignKey.CASCADE,
      onDelete = ForeignKey.CASCADE
    )
  ]
)
data class ChanCatalogSnapshotEntity(
  @ColumnInfo(name = OWNER_BOARD_ID_COLUMN_NAME)
  val ownerBoardId: Long,
  @ColumnInfo(name = THREAD_NO_COLUMN_NAME)
  val threadNo: Long,
  @ColumnInfo(name = THREAD_ORDER_COLUMN_NAME)
  val threadOrder: Int
) {

  companion object {
    const val TABLE_NAME = "chan_catalog_snapshot"

    const val OWNER_BOARD_ID_COLUMN_NAME = "owner_board_id"
    const val THREAD_NO_COLUMN_NAME = "thread_no"
    const val THREAD_ORDER_COLUMN_NAME = "thread_order"
  }
}