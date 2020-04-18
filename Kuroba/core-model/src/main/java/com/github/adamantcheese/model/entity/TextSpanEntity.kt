package com.github.adamantcheese.model.entity

import androidx.room.*

@Entity(
        tableName = TextSpanEntity.TABLE_NAME,
        foreignKeys = [
            ForeignKey(
                    entity = ChanPostEntity::class,
                    parentColumns = [ChanPostEntity.POST_ID_COLUMN_NAME],
                    childColumns = [TextSpanEntity.OWNER_POST_ID_COLUMN_NAME],
                    onDelete = ForeignKey.CASCADE,
                    onUpdate = ForeignKey.CASCADE
            )
        ],
        indices = [
            Index(
                    value = [TextSpanEntity.OWNER_POST_ID_INDEX_NAME],
                    name = TextSpanEntity.OWNER_POST_ID_COLUMN_NAME
            )
        ]
)
data class TextSpanEntity(
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = TEXT_SPAN_ID_COLUMN_NAME)
        val textSpanId: Long = 0L,
        @ColumnInfo(name = OWNER_POST_ID_COLUMN_NAME)
        val ownerPostId: Long,
        @ColumnInfo(name = ORIGINAL_TEXT_COLUMN_NAME)
        val originalText: String,
        @ColumnInfo(name = SPAN_INFO_JSON_COLUMN_NAME)
        val spanInfoJson: String,
        @ColumnInfo(name = TEXT_TYPE_COLUMN_NAME)
        val textType: TextType
) {

    enum class TextType(val value: Int) {
        PostComment(0),
        Subject(1),
        Tripcode(2);

        companion object {
            fun fromValue(value: Int): TextType {
                return values()
                        .firstOrNull { it.value == value }
                        ?: throw IllegalArgumentException("Value ($value) not found")
            }
        }
    }

    companion object {
        const val TABLE_NAME = "text_span"

        const val TEXT_SPAN_ID_COLUMN_NAME = "text_span_id"
        const val OWNER_POST_ID_COLUMN_NAME = "owner_post_id"
        const val ORIGINAL_TEXT_COLUMN_NAME = "original_text"
        const val SPAN_INFO_JSON_COLUMN_NAME = "span_info_json"
        const val TEXT_TYPE_COLUMN_NAME = "text_type"

        const val OWNER_POST_ID_INDEX_NAME = "${TABLE_NAME}_owner_post_id_idx"
    }
}