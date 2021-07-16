package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.k1rakishou.model.entity.SeenPostEntity
import org.joda.time.DateTime

@Dao
abstract class SeenPostDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertMany(seenPostEntities: Collection<SeenPostEntity>)

  @Query("""
        SELECT *
        FROM ${SeenPostEntity.TABLE_NAME}
        WHERE ${SeenPostEntity.OWNER_THREAD_ID_COLUMN_NAME} = :threadId
    """)
  abstract suspend fun selectAllByThreadId(threadId: Long): List<SeenPostEntity>

  @Query("SELECT COUNT(*) FROM ${SeenPostEntity.TABLE_NAME}")
  abstract suspend fun count(): Int

  @Query("""
        DELETE 
        FROM ${SeenPostEntity.TABLE_NAME}
        WHERE ${SeenPostEntity.INSERTED_AT_COLUMN_NAME} < :dateTime
    """)
  abstract suspend fun deleteOlderThan(dateTime: DateTime): Int

  @Query("DELETE FROM ${SeenPostEntity.TABLE_NAME}")
  abstract suspend fun deleteAll(): Int
}