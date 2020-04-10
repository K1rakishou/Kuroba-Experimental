package com.github.adamantcheese.model.entity

import androidx.room.*
import org.joda.time.DateTime

@Entity(
        tableName = SeenPostEntity.TABLE_NAME,
        foreignKeys = [
            ForeignKey(
                    entity = ChanCatalogEntity::class,
                    parentColumns = [ChanCatalogEntity.BOARD_ID_COLUMN_NAME],
                    childColumns = [SeenPostEntity.OWNER_BOARD_ID_COLUMN_NAME],
                    onDelete = ForeignKey.CASCADE,
                    onUpdate = ForeignKey.CASCADE
            )
        ],
        indices = [
            Index(
                    name = SeenPostEntity.INSERTED_AT_INDEX_NAME,
                    value = [
                        SeenPostEntity.INSERTED_AT_COLUMN_NAME
                    ]
            )
        ]
)
class SeenPostEntity(
        @PrimaryKey(autoGenerate = false)
        @ColumnInfo(name = POST_ID_COLUMN_NAME)
        val postId: Long,
        @ColumnInfo(name = OWNER_BOARD_ID_COLUMN_NAME)
        val ownerBoardId: Long,
        @ColumnInfo(name = INSERTED_AT_COLUMN_NAME)
        val insertedAt: DateTime
) {

    companion object {
        const val TABLE_NAME = "seen_post"

        const val POST_ID_COLUMN_NAME = "post_id"
        const val OWNER_BOARD_ID_COLUMN_NAME = "owner_board_id"
        const val INSERTED_AT_COLUMN_NAME = "inserted_at"

        const val INSERTED_AT_INDEX_NAME = "${TABLE_NAME}_inserted_at_idx"
    }
}