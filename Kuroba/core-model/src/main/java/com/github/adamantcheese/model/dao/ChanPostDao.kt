package com.github.adamantcheese.model.dao

import androidx.room.*
import com.github.adamantcheese.model.entity.ChanPostEntity

@Dao
abstract class ChanPostDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(chanPostEntity: ChanPostEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun update(chanPostEntity: ChanPostEntity)

    @Query("""
        SELECT *
        FROM ${ChanPostEntity.TABLE_NAME}
        WHERE 
            ${ChanPostEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
        AND
            ${ChanPostEntity.POST_NO_COLUMN_NAME} = :postNo
    """)
    abstract suspend fun select(ownerThreadId: Long, postNo: Long): ChanPostEntity?

    suspend fun insertOrUpdate(ownerThreadId: Long, postNo: Long, chanPostEntity: ChanPostEntity): Long {
        val prev = select(ownerThreadId, postNo)
        if (prev != null) {
            update(chanPostEntity)
            return prev.postId
        }

        return insert(chanPostEntity)
    }

    @Query("DELETE FROM ${ChanPostEntity.TABLE_NAME}")
    abstract fun deleteAll(): Int
}