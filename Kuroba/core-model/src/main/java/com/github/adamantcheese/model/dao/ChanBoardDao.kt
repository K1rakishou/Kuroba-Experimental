package com.github.adamantcheese.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.model.entity.ChanBoardEntity

@Dao
abstract class ChanBoardDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(chanBoardEntity: ChanBoardEntity): Long

    @Query("""
        SELECT * 
        FROM ${ChanBoardEntity.TABLE_NAME}
        WHERE 
            ${ChanBoardEntity.SITE_NAME_COLUMN_NAME} = :siteName
        AND
            ${ChanBoardEntity.BOARD_CODE_COLUMN_NAME} = :boardCode
    """)
    abstract suspend fun select(siteName: String, boardCode: String): ChanBoardEntity?

    @Query("""
        SELECT *
        FROM ${ChanBoardEntity.TABLE_NAME}
        WHERE ${ChanBoardEntity.BOARD_ID_COLUMN_NAME} = :boardId
    """)
    abstract suspend fun select(boardId: Long): ChanBoardEntity?

    suspend fun insert(siteName: String, boardCode: String): ChanBoardEntity {
        val prev = select(siteName, boardCode)
        if (prev != null) {
            return prev
        }

        val chanBoardEntity = ChanBoardEntity(
                boardId = 0L,
                siteName = siteName,
                boardCode = boardCode
        )

        val insertedId = insert(chanBoardEntity)
        check(insertedId >= 0L) { "Couldn't insert entity, insert() returned ${insertedId}" }

        return chanBoardEntity.copy(boardId = insertedId)
    }
}