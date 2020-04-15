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
        @ColumnInfo(name = UNIX_TIMESTAMP_SECONDS_COLUMN_NAME)
        val unixTimestampSeconds: Long = -1L,
        @ColumnInfo(name = ID_COLOR_COLUMN_NAME)
        val idColor: Int = 0,
        @ColumnInfo(name = FILTER_HIGHLIGHTED_COLOR_COLUMN_NAME)
        val filterHighlightedColor: Int = 0,
        @ColumnInfo(name = POST_COMMENT_COLUMN_NAME)
        val postComment: String = "",
        @ColumnInfo(name = SUBJECT_COLUMN_NAME)
        val subject: String? = null,
        @ColumnInfo(name = NAME_COLUMN_NAME)
        val name: String? = null,
        @ColumnInfo(name = TRIPCODE_COLUMN_NAME)
        val tripcode: String? = null,
        @ColumnInfo(name = POSTER_ID_COLUMN_NAME)
        val posterId: String? = null,
        @ColumnInfo(name = MODERATOR_CAPCODE_COLUMN_NAME)
        val moderatorCapcode: String? = null,
        @ColumnInfo(name = SUBJECT_SPAN_COLUMN_NAME)
        val subjectSpan: String? = null,
        @ColumnInfo(name = NAME_TRIPCODE_ID_CAPCODE_SPAN_COLUMN_NAME)
        val nameTripcodeIdCapcodeSpan: String? = null,
        @ColumnInfo(name = IS_OP_COLUMN_NAME)
        val isOp: Boolean = false,
        @ColumnInfo(name = IS_LIGHT_COLOR_COLUMN_NAME)
        val isLightColor: Boolean = false,
        @ColumnInfo(name = IS_SAVED_REPLY_COLUMN_NAME)
        val isSavedReply: Boolean = false
) {
    companion object {
        const val TABLE_NAME = "chan_post"

        const val POST_ID_COLUMN_NAME = "post_id"
        const val POST_NO_COLUMN_NAME = "post_no"
        const val OWNER_THREAD_ID_COLUMN_NAME = "owner_thread_id"
        const val UNIX_TIMESTAMP_SECONDS_COLUMN_NAME = "unix_timestamp_seconds"
        const val ID_COLOR_COLUMN_NAME = "id_color"
        const val FILTER_HIGHLIGHTED_COLOR_COLUMN_NAME = "filter_highlighted_color"
        const val SUBJECT_COLUMN_NAME = "subject"
        const val NAME_COLUMN_NAME = "name"
        const val POST_COMMENT_COLUMN_NAME = "post_comment"
        const val TRIPCODE_COLUMN_NAME = "tripcode"
        const val POSTER_ID_COLUMN_NAME = "poster_id"
        const val MODERATOR_CAPCODE_COLUMN_NAME = "moderator_capcode"
        const val SUBJECT_SPAN_COLUMN_NAME = "subject_span"
        const val NAME_TRIPCODE_ID_CAPCODE_SPAN_COLUMN_NAME = "name_tripcode_id_capcode_span"
        const val IS_OP_COLUMN_NAME = "is_op"
        const val IS_LIGHT_COLOR_COLUMN_NAME = "is_light_color"
        const val IS_SAVED_REPLY_COLUMN_NAME = "is_saved_reply"

        const val POST_NO_INDEX_NAME = "${TABLE_NAME}_post_no_idx"
        const val THREAD_ID_INDEX_NAME = "${TABLE_NAME}_thread_id_idx"
        const val POST_NO_OWNER_THREAD_ID_INDEX_NAME = "${TABLE_NAME}_post_no_owner_thread_id_idx"
    }
}