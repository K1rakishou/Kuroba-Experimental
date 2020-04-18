package com.github.adamantcheese.model.dao

import androidx.room.*
import com.github.adamantcheese.model.entity.ChanThreadEntity

@Dao
abstract class ChanThreadDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(chanThreadEntity: ChanThreadEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun update(chanThreadEntity: ChanThreadEntity)

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
            update(chanThreadEntity)
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

}