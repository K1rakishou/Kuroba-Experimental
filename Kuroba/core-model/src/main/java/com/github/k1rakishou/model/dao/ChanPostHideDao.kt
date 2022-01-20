package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.k1rakishou.model.entity.chan.post.ChanPostHideEntity

@Dao
abstract class ChanPostHideDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertManyOrUpdate(entities: List<ChanPostHideEntity>)

  @Query("""
    SELECT *
    FROM ${ChanPostHideEntity.TABLE_NAME}
    WHERE
        ${ChanPostHideEntity.SITE_NAME_COLUMN_NAME} = :siteName
    AND
        ${ChanPostHideEntity.BOARD_CODE_COLUMN_NAME} = :boardCode
    AND
        ${ChanPostHideEntity.THREAD_NO_COLUMN_NAME} = :threadNo
  """)
  abstract suspend fun selectAllInThread(siteName: String, boardCode: String, threadNo: Long): List<ChanPostHideEntity>

  @Query("""
    SELECT *
    FROM ${ChanPostHideEntity.TABLE_NAME}
    WHERE
        ${ChanPostHideEntity.SITE_NAME_COLUMN_NAME} = :siteName
    AND
        ${ChanPostHideEntity.BOARD_CODE_COLUMN_NAME} = :boardCode
    AND 
        ${ChanPostHideEntity.THREAD_NO_COLUMN_NAME} = ${ChanPostHideEntity.POST_NO_COLUMN_NAME}
    ORDER BY ${ChanPostHideEntity.THREAD_NO_COLUMN_NAME} DESC
    LIMIT :count
  """)
  abstract suspend fun selectLatestForCatalog(
    siteName: String,
    boardCode: String,
    count: Int
  ): List<ChanPostHideEntity>

  @Query("""
    SELECT COUNT(*)
    FROM ${ChanPostHideEntity.TABLE_NAME}
  """)
  abstract suspend fun totalCount(): Int

  @Query("""
    DELETE
    FROM ${ChanPostHideEntity.TABLE_NAME}
    WHERE
        ${ChanPostHideEntity.SITE_NAME_COLUMN_NAME} = :siteName
    AND
        ${ChanPostHideEntity.BOARD_CODE_COLUMN_NAME} = :boardCode
    AND
        ${ChanPostHideEntity.THREAD_NO_COLUMN_NAME} = :threadNo
    AND
        ${ChanPostHideEntity.POST_NO_COLUMN_NAME} = :postNo
    AND
        ${ChanPostHideEntity.POST_SUB_NO_COLUMN_NAME} = :postSubNo
  """)
  abstract suspend fun delete(siteName: String, boardCode: String, threadNo: Long, postNo: Long, postSubNo: Long)

  @Query("DELETE FROM ${ChanPostHideEntity.TABLE_NAME}")
  abstract suspend fun deleteAll()
}