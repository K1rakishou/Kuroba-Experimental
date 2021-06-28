package com.github.k1rakishou.model.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomWarnings
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.entity.chan.board.ChanBoardIdEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostFull
import com.github.k1rakishou.model.entity.chan.post.ChanPostIdEntity
import com.github.k1rakishou.model.entity.chan.site.ChanSiteIdEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity

@Dao
abstract class ChanPostDao {

  @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
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
  abstract suspend fun selectAllByThreadIdExceptOp(ownerThreadId: Long): List<ChanPostFull>

  @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
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
            cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME} IN (:postDatabaseIds)
        ORDER BY cp_id.${ChanPostIdEntity.POST_NO_COLUMN_NAME} DESC
    """)
  abstract suspend fun selectManyByThreadIdExceptOp(
    ownerThreadId: Long,
    postDatabaseIds: Collection<Long>
  ): List<ChanPostFull>

  suspend fun selectOriginalPost(
    ownerThreadId: Long
  ): ChanPostFull? {
    val originalPosts = selectOriginalPostsGrouped(listOf(ownerThreadId))
    check(originalPosts.size <= 1) { "Bad originalPosts count: ${originalPosts.size}" }

    return originalPosts.firstOrNull()
  }

  suspend fun selectOriginalPosts(
    ownerThreadIds: Collection<Long>
  ): List<ChanPostFull> {
    return selectOriginalPostsGrouped(ownerThreadIds)
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
    SELECT 
	      post_id,
        post_no,
        post_sub_no,
	      thread_no, 
	      board_code, 
	      site_name
    FROM ${ChanPostIdEntity.TABLE_NAME} post_ids
    INNER JOIN ${ChanThreadEntity.TABLE_NAME} threads 
        ON threads.${ChanThreadEntity.THREAD_ID_COLUMN_NAME} = post_ids.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME}
    INNER JOIN ${ChanBoardIdEntity.TABLE_NAME} boards 
        ON boards.${ChanBoardIdEntity.BOARD_ID_COLUMN_NAME} = threads.${ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME}
    INNER JOIN ${ChanSiteIdEntity.TABLE_NAME} sites 
        ON sites.${ChanSiteIdEntity.SITE_NAME_COLUMN_NAME} = boards.${ChanBoardIdEntity.OWNER_SITE_NAME_COLUMN_NAME}
    WHERE post_ids.${ChanPostIdEntity.POST_ID_COLUMN_NAME} IN (:chanPostIds)
  """)
  abstract suspend fun selectPostDescriptorDatabaseObjectsByPostIds(
    chanPostIds: Set<Long>
  ): List<PostDescriptorDatabaseObject>

  @Query("""
    SELECT 
	      post_id,
        post_no,
        post_sub_no,
	      thread_no, 
	      board_code, 
	      site_name
    FROM ${ChanPostIdEntity.TABLE_NAME} post_ids
    INNER JOIN ${ChanThreadEntity.TABLE_NAME} threads 
        ON threads.${ChanThreadEntity.THREAD_ID_COLUMN_NAME} = post_ids.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME}
    INNER JOIN ${ChanBoardIdEntity.TABLE_NAME} boards 
        ON boards.${ChanBoardIdEntity.BOARD_ID_COLUMN_NAME} = threads.${ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME}
    INNER JOIN ${ChanSiteIdEntity.TABLE_NAME} sites 
        ON sites.${ChanSiteIdEntity.SITE_NAME_COLUMN_NAME} = boards.${ChanBoardIdEntity.OWNER_SITE_NAME_COLUMN_NAME}
    WHERE post_ids.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = :threadId
  """)
  abstract suspend fun selectPostDescriptorDatabaseObjectsByThreadIds(
    threadId: Long
  ): List<PostDescriptorDatabaseObject>

  @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
  @Query("""
        SELECT *
        FROM ${ChanPostIdEntity.TABLE_NAME} cp_id
        WHERE 
            cp_id.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
        AND 
            cp_id.${ChanPostIdEntity.POST_SUB_NO_COLUMN_NAME} = 0
        AND 
            cp_id.${ChanPostIdEntity.POST_NO_COLUMN_NAME} IN (:postNos)
        ORDER BY cp_id.${ChanPostIdEntity.POST_NO_COLUMN_NAME} DESC
    """)
  abstract suspend fun selectManyByThreadIdAndPostNos(ownerThreadId: Long, postNos: Collection<Long>): List<ChanPostIdEntity>

  @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
  @Query("""
        SELECT *
        FROM ${ChanPostIdEntity.TABLE_NAME} cp_id
        INNER JOIN ${ChanPostEntity.TABLE_NAME} cpe
            ON cpe.${ChanPostEntity.CHAN_POST_ID_COLUMN_NAME} = cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME}
        WHERE 
            cp_id.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} IN (:ownerThreadIds)
        AND 
            cpe.${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_TRUE}
    """)
  protected abstract suspend fun selectOriginalPostsGrouped(ownerThreadIds: Collection<Long>): List<ChanPostFull>

  @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
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
                ${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} IN (:ownerThreadIds)
            AND 
                ${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_FALSE}
        )
    """)
  abstract suspend fun deletePostsByThreadIds(ownerThreadIds: Set<Long>): Int

  @Query("SELECT *FROM ${ChanPostIdEntity.TABLE_NAME}")
  abstract suspend fun testGetAll(): List<ChanPostFull>

  @Query("SELECT * FROM ${ChanPostIdEntity.TABLE_NAME}")
  abstract suspend fun testGetAllChanPostIds(): List<ChanPostIdEntity>

  @Query("SELECT * FROM ${ChanPostEntity.TABLE_NAME}")
  abstract suspend fun testGetAllChanPosts(): List<ChanPostEntity>

  data class PostDescriptorDatabaseObject(
    @ColumnInfo(name = ChanPostIdEntity.POST_ID_COLUMN_NAME)
    val postDatabaseId: Long,
    @ColumnInfo(name = ChanPostIdEntity.POST_NO_COLUMN_NAME)
    val postNo: Long,
    @ColumnInfo(name = ChanPostIdEntity.POST_SUB_NO_COLUMN_NAME)
    val postSubNo: Long,
    @ColumnInfo(name = ChanThreadEntity.THREAD_NO_COLUMN_NAME)
    val threadNo: Long,
    @ColumnInfo(name = ChanBoardIdEntity.BOARD_CODE_COLUMN_NAME)
    val boardCode: String,
    @ColumnInfo(name = ChanSiteIdEntity.SITE_NAME_COLUMN_NAME)
    val siteName: String
  )

}