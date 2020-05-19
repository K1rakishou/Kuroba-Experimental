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
            ${ChanPostEntity.POST_NO_COLUMN_NAME} IN (:postNoCollection)
    """)
    abstract suspend fun selectMany(
            ownerThreadId: Long,
            postNoCollection: Collection<Long>
    ): List<ChanPostEntity>

    @Query("""
        SELECT *
        FROM ${ChanPostEntity.TABLE_NAME}
        WHERE 
            ${ChanPostEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
        AND 
            ${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_FALSE}
        AND 
            ${ChanPostEntity.POST_NO_COLUMN_NAME} NOT IN (:postsToIgnore)
        ORDER BY ${ChanPostEntity.POST_NO_COLUMN_NAME} DESC
        LIMIT :maxCount
    """)
    abstract suspend fun selectAllByThreadId(
      ownerThreadId: Long,
      postsToIgnore: Collection<Long>,
      maxCount: Int
    ): List<ChanPostEntity>

    @Query("""
        SELECT *
        FROM ${ChanPostEntity.TABLE_NAME}
        WHERE 
            ${ChanPostEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
        AND 
            ${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_TRUE}
    """)
    abstract suspend fun selectOriginalPost(ownerThreadId: Long): ChanPostEntity?

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

    @Query("SELECT COUNT(*) FROM ${ChanPostEntity.TABLE_NAME}")
    abstract suspend fun count(): Int

    @Query("DELETE FROM ${ChanPostEntity.TABLE_NAME}")
    abstract suspend fun deleteAll(): Int

    @Query("""
        DELETE 
        FROM ${ChanPostEntity.TABLE_NAME} 
        WHERE 
            ${ChanPostEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
        AND
            ${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_FALSE}
    """)
    abstract suspend fun deletePostsByThreadId(ownerThreadId: Long): Int
}