package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.entity.chan.post.ChanPostEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostIdEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity
import com.github.k1rakishou.model.entity.view.ChanThreadsWithPosts
import com.github.k1rakishou.model.entity.view.OldChanPostThread

@Dao
abstract class ChanThreadDao {

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insert(chanThreadEntity: ChanThreadEntity): Long

  @Query("""
        UPDATE ${ChanThreadEntity.TABLE_NAME}
        SET ${ChanThreadEntity.STICKY_COLUMN_NAME} = :sticky,
            ${ChanThreadEntity.CLOSED_COLUMN_NAME} = :closed,
            ${ChanThreadEntity.ARCHIVED_COLUMN_NAME} = :archived,
            
            ${ChanThreadEntity.CATALOG_IMAGES_COUNT_COLUMN_NAME} = CASE 
            WHEN ${ChanThreadEntity.CATALOG_IMAGES_COUNT_COLUMN_NAME} IS NULL THEN :threadImagesCount
            WHEN ${ChanThreadEntity.CATALOG_IMAGES_COUNT_COLUMN_NAME} > :threadImagesCount THEN ${ChanThreadEntity.CATALOG_IMAGES_COUNT_COLUMN_NAME}
            ELSE :threadImagesCount
            END,
            
            ${ChanThreadEntity.CATALOG_REPLIES_COUNT_COLUMN_NAME} = CASE 
            WHEN ${ChanThreadEntity.CATALOG_REPLIES_COUNT_COLUMN_NAME} IS NULL THEN :replies
            WHEN ${ChanThreadEntity.CATALOG_REPLIES_COUNT_COLUMN_NAME} > :replies THEN ${ChanThreadEntity.CATALOG_REPLIES_COUNT_COLUMN_NAME}
            ELSE :replies
            END,
            
            ${ChanThreadEntity.UNIQUE_IPS_COLUMN_NAME} = CASE 
            WHEN ${ChanThreadEntity.UNIQUE_IPS_COLUMN_NAME} IS NULL THEN :uniqueIps
            WHEN ${ChanThreadEntity.UNIQUE_IPS_COLUMN_NAME} > :uniqueIps THEN ${ChanThreadEntity.UNIQUE_IPS_COLUMN_NAME}
            ELSE :uniqueIps
            END,
            
            ${ChanThreadEntity.LAST_MODIFIED_COLUMN_NAME} = CASE 
            WHEN ${ChanThreadEntity.LAST_MODIFIED_COLUMN_NAME} IS NULL THEN :lastModified
            WHEN ${ChanThreadEntity.LAST_MODIFIED_COLUMN_NAME} > :lastModified THEN ${ChanThreadEntity.LAST_MODIFIED_COLUMN_NAME}
            ELSE :lastModified
            END
        WHERE ${ChanThreadEntity.THREAD_ID_COLUMN_NAME} = :threadId
    """)
  abstract suspend fun update(
    threadId: Long,
    replies: Int,
    threadImagesCount: Int,
    uniqueIps: Int,
    sticky: Boolean,
    closed: Boolean,
    archived: Boolean,
    lastModified: Long
  )

  @Query("""
        SELECT *
        FROM ${ChanThreadEntity.TABLE_NAME}
        WHERE 
            ${ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME} = :ownerBoardId
        AND
            ${ChanThreadEntity.THREAD_NO_COLUMN_NAME} = :threadNo
    """)
  abstract suspend fun select(ownerBoardId: Long, threadNo: Long): ChanThreadEntity?

  @Query("""
        SELECT ${ChanThreadEntity.THREAD_ID_COLUMN_NAME}
        FROM ${ChanThreadEntity.TABLE_NAME}
        WHERE 
            ${ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME} = :ownerBoardId
        AND
            ${ChanThreadEntity.THREAD_NO_COLUMN_NAME} = :threadNo
    """)
  abstract suspend fun selectThreadId(ownerBoardId: Long, threadNo: Long): Long?

  suspend fun insertDefaultOrIgnore(ownerBoardId: Long, threadNo: Long): Long {
    val prev = select(ownerBoardId, threadNo)
    if (prev != null) {
      return prev.threadId
    }

    return insert(ChanThreadEntity(0L, threadNo, ownerBoardId))
  }

  suspend fun insertOrUpdate(
    ownerBoardId: Long,
    threadNo: Long,
    chanThreadEntity: ChanThreadEntity
  ): Long {
    val prev = select(ownerBoardId, threadNo)
    if (prev != null) {
      chanThreadEntity.threadId = prev.threadId

      update(
        chanThreadEntity.threadId,
        chanThreadEntity.catalogRepliesCount,
        chanThreadEntity.catalogImagesCount,
        chanThreadEntity.uniqueIps,
        chanThreadEntity.sticky,
        chanThreadEntity.closed,
        chanThreadEntity.archived,
        chanThreadEntity.lastModified
      )
      return prev.threadId
    }

    return insert(chanThreadEntity)
  }

  @Query("""
        SELECT *
        FROM ${ChanThreadEntity.TABLE_NAME}
        WHERE 
            ${ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME} = :ownerBoardId
        AND
            ${ChanThreadEntity.THREAD_NO_COLUMN_NAME} IN (:threadNos)
    """)
  abstract suspend fun selectManyByThreadNos(
    ownerBoardId: Long,
    threadNos: Collection<Long>
  ): List<ChanThreadEntity>

  @Query("""
        SELECT *
        FROM ${ChanThreadEntity.TABLE_NAME}
        WHERE ${ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME} = :ownerBoardId
        ORDER BY ${ChanThreadEntity.LAST_MODIFIED_COLUMN_NAME} DESC
        LIMIT :count
    """)
  abstract suspend fun selectLatestThreads(ownerBoardId: Long, count: Int): List<ChanThreadEntity>

  @Query("""
        SELECT *
        FROM ${ChanThreadEntity.TABLE_NAME}
        WHERE ${ChanThreadEntity.THREAD_ID_COLUMN_NAME} IN (:chanThreadIdList)
    """)
  abstract suspend fun selectManyByThreadIdList(chanThreadIdList: List<Long>): List<ChanThreadEntity>

  @Query("""
    SELECT 
        ctwp.${ChanThreadsWithPosts.THREAD_ID_COLUMN_NAME}, 
        ctwp.${ChanThreadsWithPosts.THREAD_NO_COLUMN_NAME},
        ctwp.${ChanThreadsWithPosts.LAST_MODIFIED_COLUMN_NAME},
        ctwp.${ChanThreadsWithPosts.POSTS_COUNT_COLUMN_NAME},
        tb.${ChanThreadsWithPosts.THREAD_BOOKMARK_ID_COLUMN_NAME} 
    FROM ${ChanThreadsWithPosts.VIEW_NAME} AS ctwp 
    LEFT OUTER JOIN thread_bookmark AS tb 
        ON ctwp.thread_id = tb.owner_thread_id 
    LEFT OUTER JOIN thread_download_entity as tde
        ON ctwp.thread_id = tde.owner_thread_database_id
    LIMIT :count OFFSET :offset
  """)
  abstract suspend fun selectThreadsWithPostsOtherThanOp(offset: Int, count: Int): List<ChanThreadsWithPosts>

  @Query("""
    SELECT 
        ctwp.${OldChanPostThread.THREAD_ID_COLUMN_NAME}, 
        ctwp.${OldChanPostThread.THREAD_NO_COLUMN_NAME},
        ctwp.${OldChanPostThread.LAST_MODIFIED_COLUMN_NAME},
        ctwp.${OldChanPostThread.POSTS_COUNT_COLUMN_NAME},
        tb.${OldChanPostThread.THREAD_BOOKMARK_ID_COLUMN_NAME} 
    FROM ${OldChanPostThread.VIEW_NAME} AS ctwp 
    LEFT OUTER JOIN thread_bookmark AS tb 
        ON ctwp.thread_id = tb.owner_thread_id 
    LEFT OUTER JOIN thread_download_entity as tde
        ON ctwp.thread_id = tde.owner_thread_database_id
    LIMIT :count OFFSET :offset
  """)
  abstract suspend fun selectOldThreads(offset: Int, count: Int): List<OldChanPostThread>

  @Query("SELECT COUNT(*) FROM ${ChanThreadEntity.TABLE_NAME}")
  abstract fun totalThreadsCount(): Int

  @Query("""
    DELETE 
    FROM ${ChanThreadEntity.TABLE_NAME} 
    WHERE ${ChanThreadEntity.THREAD_ID_COLUMN_NAME} IN (:threadIds)
    """)
  abstract suspend fun deleteThreads(threadIds: Collection<Long>): Int

  @Query("""
    DELETE 
    FROM ${ChanPostIdEntity.TABLE_NAME} 
    WHERE ${ChanPostIdEntity.POST_ID_COLUMN_NAME} IN 
    (
        SELECT ${ChanPostIdEntity.POST_ID_COLUMN_NAME}
        FROM ${ChanPostIdEntity.TABLE_NAME} cpid
        INNER JOIN ${ChanPostEntity.TABLE_NAME} cpe
            ON cpid.${ChanPostIdEntity.POST_ID_COLUMN_NAME} = cpe.${ChanPostEntity.CHAN_POST_ID_COLUMN_NAME}
        WHERE
            cpid.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = :threadId
        AND
            cpe.${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_FALSE}
    )
  """)
  abstract suspend fun deleteAllPostsInThreadExceptOriginalPost(threadId: Long)

}