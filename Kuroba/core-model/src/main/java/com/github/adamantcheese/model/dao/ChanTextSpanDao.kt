package com.github.adamantcheese.model.dao

import androidx.room.*
import com.github.adamantcheese.model.entity.ChanTextSpanEntity

@Dao
abstract class ChanTextSpanDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(chanTextSpanEntity: ChanTextSpanEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun update(chanTextSpanEntity: ChanTextSpanEntity)

    @Query("""
        SELECT *
        FROM ${ChanTextSpanEntity.TABLE_NAME}
        WHERE 
            ${ChanTextSpanEntity.OWNER_POST_ID_COLUMN_NAME} = :ownerPostId
        AND
            ${ChanTextSpanEntity.TEXT_TYPE_COLUMN_NAME} = :textType
    """)
    abstract suspend fun select(
            ownerPostId: Long,
            textType: ChanTextSpanEntity.TextType
    ): ChanTextSpanEntity?

    @Query("""
        SELECT *
        FROM ${ChanTextSpanEntity.TABLE_NAME}
        WHERE ${ChanTextSpanEntity.OWNER_POST_ID_COLUMN_NAME} IN (:ownerPostIdList)
    """)
    abstract suspend fun selectManyByOwnerPostIdList(
            ownerPostIdList: List<Long>
    ): List<ChanTextSpanEntity>

    suspend fun insertOrUpdate(
            ownerPostId: Long,
            chanTextSpanEntity: ChanTextSpanEntity
    ): Long {
        val prev = select(ownerPostId, chanTextSpanEntity.textType)
        if (prev != null) {
            chanTextSpanEntity.textSpanId = prev.textSpanId
            update(chanTextSpanEntity)
            return prev.textSpanId
        }

        return insert(chanTextSpanEntity)
    }


}