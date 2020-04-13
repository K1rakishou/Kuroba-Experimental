package com.github.adamantcheese.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.model.entity.ChanThreadEntity

@Dao
abstract class ChanThreadDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(chanThreadEntity: ChanThreadEntity): Long


    @Query("""
        SELECT *
        FROM ${ChanThreadEntity.TABLE_NAME}
        WHERE 
            ${ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME} = :ownerBoardId
        AND
            ${ChanThreadEntity.THREAD_NO_COLUMN_NAME} = :threadNo
    """)
    abstract suspend fun select(ownerBoardId: Long, threadNo: Long): ChanThreadEntity?

    suspend fun insert(ownerBoardId: Long, threadNo: Long): ChanThreadEntity {
        val prev = select(ownerBoardId, threadNo)
        if (prev != null) {
            return prev
        }

        val chanThreadEntity = ChanThreadEntity(
                threadId = 0L,
                threadNo = threadNo,
                ownerBoardId = ownerBoardId
        )

        val insertedThreadId = insert(chanThreadEntity)

        chanThreadEntity.threadId = insertedThreadId
        return chanThreadEntity
    }

    @Query("""
        SELECT *
        FROM ${ChanThreadEntity.TABLE_NAME}
        WHERE ${ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME} = :boardId
    """)
    abstract fun selectAllThreadsByBoardId(boardId: Long): List<ChanThreadEntity>

}