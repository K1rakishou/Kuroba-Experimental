package com.github.adamantcheese.model.entity.archive

import androidx.room.*
import com.github.adamantcheese.model.entity.ChanThreadEntity
import org.joda.time.DateTime

@Entity(
        tableName = ThirdPartyArchiveFetchHistoryEntity.TABLE_NAME,
        foreignKeys = [
            ForeignKey(
                    entity = ThirdPartyArchiveInfoEntity::class,
                    parentColumns = [ThirdPartyArchiveInfoEntity.ARCHIVE_ID_COLUMN_NAME],
                    childColumns = [ThirdPartyArchiveFetchHistoryEntity.OWNER_THIRD_PARTY_ARCHIVE_ID_COLUMN_NAME],
                    onDelete = ForeignKey.CASCADE,
                    onUpdate = ForeignKey.CASCADE
            ),
            ForeignKey(
                    entity = ChanThreadEntity::class,
                    parentColumns = [ChanThreadEntity.THREAD_ID_COLUMN_NAME],
                    childColumns = [ThirdPartyArchiveFetchHistoryEntity.OWNER_THREAD_ID_COLUMN_NAME],
                    onDelete = ForeignKey.CASCADE,
                    onUpdate = ForeignKey.CASCADE
            )
        ],
        indices = [
            Index(
                    name = ThirdPartyArchiveFetchHistoryEntity.OWNER_THIRD_PARTY_ARCHIVE_ID_INDEX_NAME,
                    value = [ThirdPartyArchiveFetchHistoryEntity.OWNER_THIRD_PARTY_ARCHIVE_ID_COLUMN_NAME]
            ),
            Index(
                    name = ThirdPartyArchiveFetchHistoryEntity.OWNER_THREAD_ID_INDEX_NAME,
                    value = [ThirdPartyArchiveFetchHistoryEntity.OWNER_THREAD_ID_COLUMN_NAME]
            ),
            Index(
                    name = ThirdPartyArchiveFetchHistoryEntity.OWNER_THIRD_PARTY_ARCHIVE_THREAD_ID_INDEX_NAME,
                    value = [
                        ThirdPartyArchiveFetchHistoryEntity.OWNER_THIRD_PARTY_ARCHIVE_ID_COLUMN_NAME,
                        ThirdPartyArchiveFetchHistoryEntity.OWNER_THREAD_ID_COLUMN_NAME
                    ]
            ),
            Index(
                    name = ThirdPartyArchiveFetchHistoryEntity.INSERTED_ON_INDEX_NAME,
                    value = [ThirdPartyArchiveFetchHistoryEntity.INSERTED_ON_COLUMN_NAME]
            )
        ]
)
data class ThirdPartyArchiveFetchHistoryEntity(
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = ID_COLUMN_NAME)
        val id: Long,
        @ColumnInfo(name = OWNER_THIRD_PARTY_ARCHIVE_ID_COLUMN_NAME)
        val ownerThirdPartyArchiveId: Long,
        @ColumnInfo(name = OWNER_THREAD_ID_COLUMN_NAME)
        val ownerThreadId: Long,
        @ColumnInfo(name = SUCCESS_COLUMN_NAME)
        val success: Boolean,
        @ColumnInfo(name = ERROR_TEXT_COLUMN_NAME)
        val errorText: String?,
        @ColumnInfo(name = INSERTED_ON_COLUMN_NAME)
        val insertedOn: DateTime
) {

    companion object {
        const val TABLE_NAME = "third_party_archive_fetch_history"

        const val ID_COLUMN_NAME = "id"
        const val OWNER_THIRD_PARTY_ARCHIVE_ID_COLUMN_NAME = "owner_third_party_archive_id"
        const val OWNER_THREAD_ID_COLUMN_NAME = "owner_thread_id"
        const val SUCCESS_COLUMN_NAME = "success"
        const val ERROR_TEXT_COLUMN_NAME = "error_text"
        const val INSERTED_ON_COLUMN_NAME = "inserted_on"

        const val OWNER_THIRD_PARTY_ARCHIVE_ID_INDEX_NAME = "${TABLE_NAME}_owner_third_party_archive_idx"
        const val OWNER_THREAD_ID_INDEX_NAME = "${TABLE_NAME}_owner_thread_id_idx"
        const val OWNER_THIRD_PARTY_ARCHIVE_THREAD_ID_INDEX_NAME = "${TABLE_NAME}_owner_third_party_archive_thread_id_idx"
        const val INSERTED_ON_INDEX_NAME = "${TABLE_NAME}_inserted_on_idx"
    }
}