package com.github.adamantcheese.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.database.entity.SeenPostEntity
import org.joda.time.DateTime

@Dao
abstract class SeenPostDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(seenPostEntity: SeenPostEntity)

    @Query("""
        SELECT *
        FROM ${SeenPostEntity.TABLE_NAME}
        WHERE ${SeenPostEntity.PARENT_LOADABLE_UID_COLUMN_NAME} = :loadableUid
    """)
    abstract suspend fun selectAllByLoadableUid(loadableUid: String): List<SeenPostEntity>

    @Query("""
        DELETE 
        FROM ${SeenPostEntity.TABLE_NAME}
        WHERE ${SeenPostEntity.INSERTED_AT_COLUMN_NAME} < :dateTime
    """)
    abstract suspend fun deleteOlderThan(dateTime: DateTime): Int
}