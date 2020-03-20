package com.github.adamantcheese.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import org.joda.time.DateTime

@Entity(
        tableName = SeenPostEntity.TABLE_NAME,
        primaryKeys = [
            SeenPostEntity.POST_UID_COLUMN_NAME,
            SeenPostEntity.PARENT_LOADABLE_UID_COLUMN_NAME,
            SeenPostEntity.POST_ID_COLUMN_NAME
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
        @ColumnInfo(name = POST_UID_COLUMN_NAME)
        val postUid: String,
        @ColumnInfo(name = PARENT_LOADABLE_UID_COLUMN_NAME)
        val parentLoadableUid: String,
        @ColumnInfo(name = POST_ID_COLUMN_NAME)
        val postId: Long,
        @ColumnInfo(name = INSERTED_AT_COLUMN_NAME)
        val insertedAt: DateTime
) {

    companion object {
        const val TABLE_NAME = "seen_post"

        const val POST_UID_COLUMN_NAME = "post_uid"
        const val PARENT_LOADABLE_UID_COLUMN_NAME = "parent_loadable_uid"
        const val POST_ID_COLUMN_NAME = "post_id"
        const val INSERTED_AT_COLUMN_NAME = "inserted_at"

        const val INSERTED_AT_INDEX_NAME = "${TABLE_NAME}_inserted_at_idx"
    }
}