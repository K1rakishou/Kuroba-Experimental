package com.github.adamantcheese.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.model.entity.LoadableEntity

@Dao
abstract class LoadableEntityDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(loadableEntity: LoadableEntity)

    @Query("""
        SELECT * 
        FROM ${LoadableEntity.TABLE_NAME}
        WHERE ${LoadableEntity.THREAD_UID_COLUMN_NAME} = :threadUid
    """)
    abstract suspend fun selectByThreadUid(threadUid: String): LoadableEntity?

    @Query("""
        SELECT *
        FROM ${LoadableEntity.TABLE_NAME}
        WHERE 
            ${LoadableEntity.SITE_NAME_COLUMN_NAME} = :siteName
        AND 
            ${LoadableEntity.BOARD_CODE_COLUMN_NAME} = :boardCode
        AND 
            ${LoadableEntity.OP_ID_COLUMN_NAME} = :opId
    """)
    abstract suspend fun selectBySiteBoardOpId(siteName: String, boardCode: String, opId: Long): LoadableEntity?

}