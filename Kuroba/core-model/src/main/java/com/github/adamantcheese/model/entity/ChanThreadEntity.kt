package com.github.adamantcheese.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
        tableName = ChanThreadEntity.TABLE_NAME,
        foreignKeys = [
            ForeignKey(
                    entity = ChanBoardEntity::class,
                    parentColumns = [ChanBoardEntity.BOARD_ID_COLUMN_NAME],
                    childColumns = [ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME],
                    onUpdate = ForeignKey.CASCADE,
                    onDelete = ForeignKey.CASCADE
            )
        ]
)
data class ChanThreadEntity(
        @PrimaryKey(autoGenerate = false)
        @ColumnInfo(name = THREAD_ID_COLUMN_NAME)
        val threadId: Long,
        @ColumnInfo(name = OWNER_BOARD_ID_COLUMN_NAME)
        val ownerBoardId: Long
) {

    companion object {
        const val TABLE_NAME = "chan_thread"

        const val THREAD_ID_COLUMN_NAME = "thread_id"
        const val OWNER_BOARD_ID_COLUMN_NAME = "owner_board_id"
    }
}