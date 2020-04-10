package com.github.adamantcheese.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.model.entity.ChanCatalogEntity

@Dao
abstract class ChanCatalogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(chanCatalogEntity: ChanCatalogEntity): Long

    @Query("""
        SELECT * 
        FROM ${ChanCatalogEntity.TABLE_NAME}
        WHERE 
            ${ChanCatalogEntity.SITE_NAME_COLUMN_NAME} = :siteName
        AND
            ${ChanCatalogEntity.BOARD_CODE_COLUMN_NAME} = :boardCode
    """)
    abstract suspend fun select(siteName: String, boardCode: String): ChanCatalogEntity?

    @Query("""
        SELECT *
        FROM ${ChanCatalogEntity.TABLE_NAME}
        WHERE ${ChanCatalogEntity.BOARD_ID_COLUMN_NAME} = :boardId
    """)
    abstract suspend fun select(boardId: Long): ChanCatalogEntity?

    suspend fun insert(siteName: String, boardCode: String): ChanCatalogEntity {
        val prev = select(siteName, boardCode)
        if (prev != null) {
            return prev
        }

        val chanCatalogEntity = ChanCatalogEntity(
                boardId = 0L,
                siteName = siteName,
                boardCode = boardCode
        )

        val insertedId = insert(chanCatalogEntity)
        check(insertedId >= 0L) { "Couldn't insert entity, insert() returned ${insertedId}" }

        return chanCatalogEntity.copy(boardId = insertedId)
    }
}