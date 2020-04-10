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
        WHERE ${ChanThreadEntity.THREAD_ID_COLUMN_NAME} = :threadId
    """)
    abstract suspend fun select(threadId: Long): ChanThreadEntity?

    suspend fun insert(threadId: Long, ownerBoardId: Long): ChanThreadEntity {
        val prev = select(threadId)
        if (prev != null) {
            return prev
        }

        val chanThreadEntity = ChanThreadEntity(
                threadId,
                ownerBoardId
        )

        insert(chanThreadEntity)
        return chanThreadEntity
    }

}