package com.github.k1rakishou.model.entity.chan.filter

import androidx.room.*

@Entity(
  tableName = ChanFilterBoardConstraintEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ChanFilterEntity::class,
      parentColumns = [ChanFilterEntity.FILTER_ID_COLUMN_NAME],
      childColumns = [ChanFilterBoardConstraintEntity.OWNER_FILTER_ID_COLUMN_NAME],
      onUpdate = ForeignKey.CASCADE,
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index(
      value = [
        ChanFilterBoardConstraintEntity.OWNER_FILTER_ID_COLUMN_NAME,
        ChanFilterBoardConstraintEntity.SITE_NAME_CONSTRAINT_COLUMN_NAME,
        ChanFilterBoardConstraintEntity.BOARD_CODE_CONSTRAINT_COLUMN_NAME,
      ],
      unique = true
    ),
    Index(value = [ChanFilterBoardConstraintEntity.OWNER_FILTER_ID_COLUMN_NAME])
  ]
)
data class ChanFilterBoardConstraintEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = BOARD_CONSTRAINT_ID_COLUMN_NAME)
  var boardConstraintId: Long = 0L,
  @ColumnInfo(name = OWNER_FILTER_ID_COLUMN_NAME)
  var ownerFilterId: Long,
  @ColumnInfo(name = SITE_NAME_CONSTRAINT_COLUMN_NAME)
  val siteNameConstraint: String,
  @ColumnInfo(name = BOARD_CODE_CONSTRAINT_COLUMN_NAME)
  val boardCodeConstraint: String
) {

  companion object {
    const val TABLE_NAME = "chan_filter_board_constraint"

    const val BOARD_CONSTRAINT_ID_COLUMN_NAME = "board_constraint_id"
    const val OWNER_FILTER_ID_COLUMN_NAME = "owner_filter_id"
    const val SITE_NAME_CONSTRAINT_COLUMN_NAME = "site_name_constraint"
    const val BOARD_CODE_CONSTRAINT_COLUMN_NAME = "board_code_constraint"
  }
}