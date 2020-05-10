package com.github.adamantcheese.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.model.entity.ChanThreadEntity
import com.github.adamantcheese.model.entity.ChanThreadsWithPosts

@Dao
abstract class ChanThreadDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(chanThreadEntity: ChanThreadEntity): Long

    @Query("""
        UPDATE ${ChanThreadEntity.TABLE_NAME}
        SET ${ChanThreadEntity.STICKY_COLUMN_NAME} = :sticky,
            ${ChanThreadEntity.CLOSED_COLUMN_NAME} = :closed,
            ${ChanThreadEntity.ARCHIVED_COLUMN_NAME} = :archived,
            
            ${ChanThreadEntity.THREAD_IMAGES_COUNT_COLUMN_NAME} = CASE 
            WHEN ${ChanThreadEntity.THREAD_IMAGES_COUNT_COLUMN_NAME} IS NULL THEN :threadImagesCount
            WHEN ${ChanThreadEntity.THREAD_IMAGES_COUNT_COLUMN_NAME} > :threadImagesCount THEN ${ChanThreadEntity.THREAD_IMAGES_COUNT_COLUMN_NAME}
            ELSE :threadImagesCount
            END,
            
            ${ChanThreadEntity.REPLIES_COLUMN_NAME} = CASE 
            WHEN ${ChanThreadEntity.REPLIES_COLUMN_NAME} IS NULL THEN :replies
            WHEN ${ChanThreadEntity.REPLIES_COLUMN_NAME} > :replies THEN ${ChanThreadEntity.REPLIES_COLUMN_NAME}
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
              chanThreadEntity.replies,
              chanThreadEntity.threadImagesCount,
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
            ${ChanThreadEntity.THREAD_NO_COLUMN_NAME} IN (:threadNoList)
    """)
    abstract suspend fun selectManyByThreadNoList(
            ownerBoardId: Long,
            threadNoList: List<Long>
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

    @Query("SELECT * FROM ${ChanThreadsWithPosts.VIEW_NAME} LIMIT :count OFFSET :offset")
    abstract suspend fun selectThreadsWithPostsOtherThanOp(offset: Int, count: Int): List<ChanThreadsWithPosts>
}