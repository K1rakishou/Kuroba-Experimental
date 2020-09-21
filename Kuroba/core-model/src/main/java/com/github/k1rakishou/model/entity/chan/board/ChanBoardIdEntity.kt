package com.github.k1rakishou.model.entity.chan.board

import androidx.room.*
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.entity.chan.site.ChanSiteIdEntity

@Entity(
  tableName = ChanBoardIdEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ChanSiteIdEntity::class,
      parentColumns = [ChanSiteIdEntity.SITE_NAME_COLUMN_NAME],
      childColumns = [ChanBoardIdEntity.OWNER_SITE_NAME_COLUMN_NAME],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index(
      name = ChanBoardIdEntity.SITE_NAME_INDEX_NAME,
      value = [ChanBoardIdEntity.OWNER_SITE_NAME_COLUMN_NAME]
    ),
    Index(
      name = ChanBoardIdEntity.BOARD_CODE_INDEX_NAME,
      value = [ChanBoardIdEntity.BOARD_CODE_COLUMN_NAME]
    ),
    Index(
      name = ChanBoardIdEntity.BOARD_DESCRIPTOR_INDEX_NAME,
      value = [
        ChanBoardIdEntity.OWNER_SITE_NAME_COLUMN_NAME,
        ChanBoardIdEntity.BOARD_CODE_COLUMN_NAME
      ],
      unique = true
    )
  ]
)
data class ChanBoardIdEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = BOARD_ID_COLUMN_NAME)
  var boardId: Long = 0L,
  @ColumnInfo(name = OWNER_SITE_NAME_COLUMN_NAME)
  val ownerSiteName: String,
  @ColumnInfo(name = BOARD_CODE_COLUMN_NAME)
  val boardCode: String
) {

  fun boardDescriptor(): BoardDescriptor = BoardDescriptor(SiteDescriptor(ownerSiteName), boardCode)

  companion object {
    const val TABLE_NAME = "chan_board_id"

    const val BOARD_ID_COLUMN_NAME = "board_id"
    const val OWNER_SITE_NAME_COLUMN_NAME = "owner_site_name"
    const val BOARD_CODE_COLUMN_NAME = "board_code"

    const val BOARD_DESCRIPTOR_INDEX_NAME = "${TABLE_NAME}_board_descriptor_idx"
    const val SITE_NAME_INDEX_NAME = "${TABLE_NAME}_site_name_idx"
    const val BOARD_CODE_INDEX_NAME = "${TABLE_NAME}_board_code_idx"
  }
}