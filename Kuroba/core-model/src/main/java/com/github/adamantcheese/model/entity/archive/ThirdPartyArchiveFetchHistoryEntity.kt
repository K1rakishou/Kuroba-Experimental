package com.github.adamantcheese.model.entity.archive

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
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
            )
        ]
)
class ThirdPartyArchiveFetchHistoryEntity(
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = ID_COLUMN_NAME)
        val id: Long,
        @ColumnInfo(name = OWNER_THIRD_PARTY_ARCHIVE_ID_COLUMN_NAME)
        val ownerThirdPartyArchiveId: Long,
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
        const val SUCCESS_COLUMN_NAME = "success"
        const val ERROR_TEXT_COLUMN_NAME = "error_text"
        const val INSERTED_ON_COLUMN_NAME = "inserted_on"
    }
}