package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.k1rakishou.model.entity.chan.post.ChanSavedReplyEntity

@Dao
abstract class ChanSavedReplyDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertOrIgnore(toEntity: ChanSavedReplyEntity)

  @Query("SELECT * FROM ${ChanSavedReplyEntity.TABLE_NAME}")
  abstract suspend fun loadAll(): List<ChanSavedReplyEntity>

  @Query("""
    SELECT *
    FROM ${ChanSavedReplyEntity.TABLE_NAME}
    WHERE 
        ${ChanSavedReplyEntity.SITE_NAME_COLUMN_NAME} = :siteName
    AND
        ${ChanSavedReplyEntity.BOARD_CODE_COLUMN_NAME} = :boardCode
    AND 
        ${ChanSavedReplyEntity.THREAD_NO_COLUMN_NAME} = :threadNo
  """)
  abstract suspend fun loadAllForThread(siteName: String, boardCode: String, threadNo: Long): List<ChanSavedReplyEntity>

  @Query("""
    DELETE
    FROM ${ChanSavedReplyEntity.TABLE_NAME}
    WHERE 
        ${ChanSavedReplyEntity.SITE_NAME_COLUMN_NAME} = :siteName
    AND
        ${ChanSavedReplyEntity.BOARD_CODE_COLUMN_NAME} = :boardCode
    AND 
        ${ChanSavedReplyEntity.THREAD_NO_COLUMN_NAME} = :threadNo
    AND
        ${ChanSavedReplyEntity.POST_NO_COLUMN_NAME} = :postNo
    AND 
        ${ChanSavedReplyEntity.POST_SUB_NO_COLUMN_NAME} = :postSubNo
  """)
  abstract suspend fun delete(siteName: String, boardCode: String, threadNo: Long, postNo: Long, postSubNo: Long)

}