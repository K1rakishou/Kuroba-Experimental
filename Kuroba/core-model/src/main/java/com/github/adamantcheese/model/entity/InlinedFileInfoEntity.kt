package com.github.adamantcheese.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import org.joda.time.DateTime

@Entity(
        tableName = InlinedFileInfoEntity.TABLE_NAME,
        primaryKeys = [
            InlinedFileInfoEntity.FILE_URL_COLUMN_NAME
        ],
        indices = [
            Index(
                    name = InlinedFileInfoEntity.INSERTED_AT_INDEX_NAME,
                    value = [
                        InlinedFileInfoEntity.INSERTED_AT_COLUMN_NAME
                    ]
            )
        ]
)
data class InlinedFileInfoEntity(
        @ColumnInfo(name = FILE_URL_COLUMN_NAME)
        val fileUrl: String,
        @ColumnInfo(name = FILE_SIZE_COLUMN_NAME)
        val fileSize: Long?,
        @ColumnInfo(name = INSERTED_AT_COLUMN_NAME)
        val insertedAt: DateTime
) {

    companion object {
        const val TABLE_NAME = "inlined_file_info_entity"

        const val FILE_URL_COLUMN_NAME = "file_url"
        const val FILE_SIZE_COLUMN_NAME = "file_size"
        const val INSERTED_AT_COLUMN_NAME = "inserted_at"

        const val INSERTED_AT_INDEX_NAME = "${TABLE_NAME}_inserted_at_idx"
    }
}