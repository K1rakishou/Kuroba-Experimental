package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.entity.chan.post.ChanPostEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostFull
import com.github.k1rakishou.model.entity.chan.post.ChanPostIdEntity

@Dao
abstract class ChanPostDao {

  suspend fun selectAllByThreadId(
    ownerThreadId: Long
  ): List<ChanPostFull> {
    return selectAllByThreadIdGrouped(ownerThreadId)
  }

  suspend fun selectOriginalPost(
    ownerThreadId: Long
  ): ChanPostFull? {
    val originalPosts = selectOriginalPostGrouped(ownerThreadId)
    check(originalPosts.size <= 1) { "Bad originalPosts count: ${originalPosts.size}" }

    return originalPosts.firstOrNull()
  }

  suspend fun selectManyOriginalPostsByThreadIdList(
    ownerThreadIdList: List<Long>
  ): List<ChanPostFull> {
    return ownerThreadIdList
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { ownerThreadIdChunk -> selectManyOriginalPostsByThreadIdListGrouped(ownerThreadIdChunk) }
  }

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertOrReplaceManyIds(chanPostIdEntityList: List<ChanPostIdEntity>): List<Long>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertOrReplaceManyPosts(chanPostEntityList: List<ChanPostEntity>)

  @Query("SELECT COUNT(*) FROM ${ChanPostIdEntity.TABLE_NAME}")
  abstract suspend fun totalPostsCount(): Int

  @Query("""
        SELECT *
        FROM ${ChanPostIdEntity.TABLE_NAME} cp_id
        INNER JOIN ${ChanPostEntity.TABLE_NAME} cpe
            ON cpe.${ChanPostEntity.CHAN_POST_ID_COLUMN_NAME} = cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME}
        WHERE 
            cp_id.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
        AND 
            cp_id.${ChanPostIdEntity.POST_SUB_NO_COLUMN_NAME} = 0
        AND 
            cpe.${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_FALSE}
        ORDER BY cp_id.${ChanPostIdEntity.POST_NO_COLUMN_NAME} DESC
    """)
  protected abstract suspend fun selectAllByThreadIdGrouped(ownerThreadId: Long): List<ChanPostFull>

  @Query("""
        SELECT *
        FROM ${ChanPostIdEntity.TABLE_NAME} cp_id
        INNER JOIN ${ChanPostEntity.TABLE_NAME} cpe
            ON cpe.${ChanPostEntity.CHAN_POST_ID_COLUMN_NAME} = cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME}
        WHERE 
            cp_id.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
        AND 
            cpe.${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_TRUE}
    """)
  protected abstract suspend fun selectOriginalPostGrouped(ownerThreadId: Long): List<ChanPostFull>

  @Query("""
        SELECT *
        FROM ${ChanPostIdEntity.TABLE_NAME} cp_id
        INNER JOIN ${ChanPostEntity.TABLE_NAME} cpe
            ON cpe.${ChanPostEntity.CHAN_POST_ID_COLUMN_NAME} = cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME}
        WHERE 
            cp_id.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} IN (:ownerThreadIdList)
        AND
            cpe.${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_TRUE}
    """)
  protected abstract suspend fun selectManyOriginalPostsByThreadIdListGrouped(ownerThreadIdList: List<Long>): List<ChanPostFull>

  @Query("DELETE FROM ${ChanPostEntity.TABLE_NAME}")
  abstract suspend fun deleteAll(): Int

  @Query("""
    DELETE
    FROM ${ChanPostIdEntity.TABLE_NAME}
    WHERE
        ${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = :threadId
    AND
        ${ChanPostIdEntity.POST_NO_COLUMN_NAME} = :postNo
    AND
        ${ChanPostIdEntity.POST_SUB_NO_COLUMN_NAME} = :postSubNo
  """)
  abstract suspend fun deletePost(threadId: Long, postNo: Long, postSubNo: Long)

  @Query("""
        DELETE FROM ${ChanPostIdEntity.TABLE_NAME} 
        WHERE ${ChanPostIdEntity.POST_ID_COLUMN_NAME} IN (
            SELECT ${ChanPostIdEntity.POST_ID_COLUMN_NAME}
            FROM ${ChanPostIdEntity.TABLE_NAME}
            INNER JOIN ${ChanPostEntity.TABLE_NAME} 
                ON ${ChanPostIdEntity.POST_ID_COLUMN_NAME} = ${ChanPostEntity.CHAN_POST_ID_COLUMN_NAME}
            WHERE 
                ${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
            AND 
                ${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_FALSE}
        )
    """)
  abstract suspend fun deletePostsByThreadId(ownerThreadId: Long): Int

  @Query("SELECT *FROM ${ChanPostIdEntity.TABLE_NAME}")
  abstract suspend fun testGetAll(): List<ChanPostFull>

  @Query("SELECT * FROM ${ChanPostIdEntity.TABLE_NAME}")
  abstract suspend fun testGetAllChanPostIds(): List<ChanPostIdEntity>

  @Query("SELECT * FROM ${ChanPostEntity.TABLE_NAME}")
  abstract suspend fun testGetAllChanPosts(): List<ChanPostEntity>

}