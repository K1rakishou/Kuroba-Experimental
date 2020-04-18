package com.github.adamantcheese.model.entity

import androidx.room.*

@Entity(
        tableName = ChanPostEntity.TABLE_NAME,
        foreignKeys = [
            ForeignKey(
                    entity = ChanThreadEntity::class,
                    parentColumns = [ChanThreadEntity.THREAD_ID_COLUMN_NAME],
                    childColumns = [ChanPostEntity.OWNER_THREAD_ID_COLUMN_NAME],
                    onUpdate = ForeignKey.CASCADE,
                    onDelete = ForeignKey.CASCADE
            )
        ],
        indices = [
            Index(
                    name = ChanPostEntity.POST_NO_INDEX_NAME,
                    value = [ChanPostEntity.POST_NO_COLUMN_NAME]
            ),
            Index(
                    name = ChanPostEntity.THREAD_ID_INDEX_NAME,
                    value = [ChanPostEntity.OWNER_THREAD_ID_COLUMN_NAME]
            ),
            Index(
                    name = ChanPostEntity.POST_NO_OWNER_THREAD_ID_INDEX_NAME,
                    value = [
                        ChanPostEntity.POST_NO_COLUMN_NAME,
                        ChanPostEntity.OWNER_THREAD_ID_COLUMN_NAME
                    ],
                    unique = true
            )
        ]
)
data class ChanPostEntity(
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = POST_ID_COLUMN_NAME)
        var postId: Long = 0,
        @ColumnInfo(name = POST_NO_COLUMN_NAME)
        val postNo: Long,
        @ColumnInfo(name = OWNER_THREAD_ID_COLUMN_NAME)
        val ownerThreadId: Long,
        @ColumnInfo(name = TIMESTAMP_SECONDS_COLUMN_NAME)
        val timestamp: Long = -1L,
        @ColumnInfo(name = NAME_COLUMN_NAME)
        val name: String? = null,
        @ColumnInfo(name = POSTER_ID_COLUMN_NAME)
        val posterId: String? = null,
        @ColumnInfo(name = MODERATOR_CAPCODE_COLUMN_NAME)
        val moderatorCapcode: String? = null,
        @ColumnInfo(name = IS_OP_COLUMN_NAME)
        val isOp: Boolean = false,
        @ColumnInfo(name = IS_SAVED_REPLY_COLUMN_NAME)
        val isSavedReply: Boolean = false
) {
    companion object {
        const val TABLE_NAME = "chan_post"

        const val POST_ID_COLUMN_NAME = "post_id"
        const val POST_NO_COLUMN_NAME = "post_no"
        const val OWNER_THREAD_ID_COLUMN_NAME = "owner_thread_id"
        const val TIMESTAMP_SECONDS_COLUMN_NAME = "timestamp_seconds"
        const val NAME_COLUMN_NAME = "name"
        const val POSTER_ID_COLUMN_NAME = "poster_id"
        const val MODERATOR_CAPCODE_COLUMN_NAME = "moderator_capcode"
        const val IS_OP_COLUMN_NAME = "is_op"
        const val IS_SAVED_REPLY_COLUMN_NAME = "is_saved_reply"

        const val POST_NO_INDEX_NAME = "${TABLE_NAME}_post_no_idx"
        const val THREAD_ID_INDEX_NAME = "${TABLE_NAME}_thread_id_idx"
        const val POST_NO_OWNER_THREAD_ID_INDEX_NAME = "${TABLE_NAME}_post_no_owner_thread_id_idx"
    }
}