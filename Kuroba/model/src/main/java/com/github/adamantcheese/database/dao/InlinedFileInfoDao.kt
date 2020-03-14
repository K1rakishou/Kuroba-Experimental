package com.github.adamantcheese.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.database.entity.InlinedFileInfoEntity
import org.joda.time.DateTime

@Dao
abstract class InlinedFileInfoDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(inlinedFileInfoEntity: InlinedFileInfoEntity)

    @Query("""
        SELECT * 
        FROM ${InlinedFileInfoEntity.TABLE_NAME} 
        WHERE 
            ${InlinedFileInfoEntity.FILE_URL_COLUMN_NAME} = :fileUrl
    """)
    abstract suspend fun selectByFileUrl(fileUrl: String): InlinedFileInfoEntity?

    @Query("""
        DELETE 
        FROM ${InlinedFileInfoEntity.TABLE_NAME}
        WHERE ${InlinedFileInfoEntity.INSERTED_AT_COLUMN_NAME} < :dateTime
    """)
    abstract suspend fun deleteOlderThan(dateTime: DateTime): Int

}