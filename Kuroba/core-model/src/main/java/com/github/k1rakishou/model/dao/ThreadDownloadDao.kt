package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.k1rakishou.model.entity.download.ThreadDownloadEntity

@Dao
abstract class ThreadDownloadDao {

  @Query("SELECT * FROM ${ThreadDownloadEntity.TABLE_NAME}")
  abstract suspend fun selectAll(): List<ThreadDownloadEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insert(threadDownloadEntity: ThreadDownloadEntity)

  @Update(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun update(threadDownloadEntity: ThreadDownloadEntity)

  @Query("""
    DELETE FROM ${ThreadDownloadEntity.TABLE_NAME} 
    WHERE ${ThreadDownloadEntity.OWNER_THREAD_DATABASE_ID_COLUMN_NAME} = :threadDatabaseId 
  """)
  abstract suspend fun delete(threadDatabaseId: Long)

}