package com.github.adamantcheese.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.model.entity.archive.ThirdPartyArchiveFetchHistoryEntity
import org.joda.time.DateTime

@Dao
abstract class ThirdPartyArchiveFetchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(thirdPartyArchiveFetchHistoryEntity: ThirdPartyArchiveFetchHistoryEntity): Long

    @Query("""
        SELECT *
        FROM ${ThirdPartyArchiveFetchHistoryEntity.TABLE_NAME}
        WHERE
            ${ThirdPartyArchiveFetchHistoryEntity.OWNER_THIRD_PARTY_ARCHIVE_ID_COLUMN_NAME} = :ownerArchiveId
        AND 
            ${ThirdPartyArchiveFetchHistoryEntity.INSERTED_ON_COLUMN_NAME} > :newerThan
        ORDER BY ${ThirdPartyArchiveFetchHistoryEntity.INSERTED_ON_COLUMN_NAME} DESC
        LIMIT :maxCount
    """)
    abstract suspend fun selectLatest(
            ownerArchiveId: Long,
            newerThan: DateTime,
            maxCount: Int
    ): List<ThirdPartyArchiveFetchHistoryEntity>

    @Query("""
        DELETE FROM ${ThirdPartyArchiveFetchHistoryEntity.TABLE_NAME}
        WHERE ${ThirdPartyArchiveFetchHistoryEntity.ID_COLUMN_NAME} = :databaseId
    """)
    abstract suspend fun delete(databaseId: Long)

    @Query("""
        DELETE FROM ${ThirdPartyArchiveFetchHistoryEntity.TABLE_NAME}
        WHERE ${ThirdPartyArchiveFetchHistoryEntity.INSERTED_ON_COLUMN_NAME} < :olderThan
    """)
    abstract suspend fun deleteOlderThan(olderThan: DateTime)

}