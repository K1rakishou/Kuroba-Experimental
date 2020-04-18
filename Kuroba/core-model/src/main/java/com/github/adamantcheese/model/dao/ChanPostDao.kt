package com.github.adamantcheese.model.dao

import androidx.room.*
import com.github.adamantcheese.model.KurobaDatabase
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

    @Query("""
        SELECT *
        FROM ${ChanPostEntity.TABLE_NAME}
        WHERE 
            ${ChanPostEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
        AND
            ${ChanPostEntity.POST_NO_COLUMN_NAME} IN (:postNoList)
    """)
    abstract suspend fun selectManyByThreadIdAndPostNoList(
            ownerThreadId: Long,
            postNoList: List<Long>
    ): List<ChanPostEntity>

    @Query("""
        SELECT ${ChanPostEntity.POST_NO_COLUMN_NAME}
        FROM ${ChanPostEntity.TABLE_NAME}
        WHERE ${ChanPostEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
    """)
    abstract suspend fun selectManyPostNoByThreadId(ownerThreadId: Long): List<Long>

    @Query("""
        SELECT * 
        FROM ${ChanPostEntity.TABLE_NAME}
        WHERE 
            ${ChanPostEntity.OWNER_THREAD_ID_COLUMN_NAME} IN (:ownerThreadIdList)
        AND
            ${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_TRUE}
    """)
    abstract suspend fun selectManyOriginalPostsByThreadIdList(
            ownerThreadIdList: List<Long>
    ): List<ChanPostEntity>

    suspend fun insertOrUpdate(
            ownerThreadId: Long,
            postNo: Long,
            chanPostEntity: ChanPostEntity
    ): Long {
        val prev = select(ownerThreadId, postNo)
        if (prev != null) {
            chanPostEntity.postId = prev.postId
            update(chanPostEntity)
            return prev.postId
        }

        return insert(chanPostEntity)
    }

    @Query("DELETE FROM ${ChanPostEntity.TABLE_NAME}")
    abstract suspend fun deleteAll(): Int
}