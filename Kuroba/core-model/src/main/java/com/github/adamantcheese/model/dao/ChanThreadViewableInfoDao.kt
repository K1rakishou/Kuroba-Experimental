package com.github.adamantcheese.model.dao

import androidx.room.*
import com.github.adamantcheese.model.entity.chan.thread.ChanThreadViewableInfoEntity

@Dao
abstract class ChanThreadViewableInfoDao {

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insert(chanThreadViewableInfoEntity: ChanThreadViewableInfoEntity)

  @Update(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun update(chanThreadViewableInfoEntity: ChanThreadViewableInfoEntity)

  @Query("""
    SELECT * 
    FROM ${ChanThreadViewableInfoEntity.TABLE_NAME}
    WHERE ${ChanThreadViewableInfoEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
  """)
  abstract suspend fun selectByOwnerThreadId(ownerThreadId: Long): ChanThreadViewableInfoEntity?

  @Query("""
    SELECT ${ChanThreadViewableInfoEntity.CHAN_THREAD_VIEWABLE_INFO_ID_COLUMN_NAME} 
    FROM ${ChanThreadViewableInfoEntity.TABLE_NAME}
    WHERE ${ChanThreadViewableInfoEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
  """)
  abstract suspend fun selectIdByOwnerThreadId(ownerThreadId: Long): Long?

  @Query("SELECT * FROM ${ChanThreadViewableInfoEntity.TABLE_NAME}")
  abstract suspend fun testGetAll(): List<ChanThreadViewableInfoEntity>

}