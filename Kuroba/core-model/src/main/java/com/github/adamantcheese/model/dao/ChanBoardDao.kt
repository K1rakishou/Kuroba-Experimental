package com.github.adamantcheese.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.entity.chan.ChanBoardEntity
import com.github.adamantcheese.model.entity.chan.ChanBoardFull
import com.github.adamantcheese.model.entity.chan.ChanBoardIdEntity

@Dao
abstract class ChanBoardDao {

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insert(chanBoardIdEntity: ChanBoardIdEntity): Long

  @Query("""
        SELECT * 
        FROM ${ChanBoardIdEntity.TABLE_NAME}
        WHERE 
            ${ChanBoardIdEntity.OWNER_SITE_NAME_COLUMN_NAME} = :siteName
        AND
            ${ChanBoardIdEntity.BOARD_CODE_COLUMN_NAME} = :boardCode
    """)
  abstract suspend fun select(siteName: String, boardCode: String): ChanBoardIdEntity?

  @Query("""
        SELECT ${ChanBoardIdEntity.BOARD_ID_COLUMN_NAME} 
        FROM ${ChanBoardIdEntity.TABLE_NAME}
        WHERE 
            ${ChanBoardIdEntity.OWNER_SITE_NAME_COLUMN_NAME} = :siteName
        AND
            ${ChanBoardIdEntity.BOARD_CODE_COLUMN_NAME} = :boardCode
    """)
  abstract suspend fun selectBoardId(siteName: String, boardCode: String): Long?

  @Query("""
    SELECT *
    FROM ${ChanBoardIdEntity.TABLE_NAME} cbie
    INNER JOIN ${ChanBoardEntity.TABLE_NAME} cbe 
        ON cbie.${ChanBoardIdEntity.BOARD_ID_COLUMN_NAME} = cbe.${ChanBoardEntity.OWNER_CHAN_BOARD_ID_COLUMN_NAME}
    WHERE cbe.${ChanBoardEntity.BOARD_ACTIVE_COLUMN_NAME} = ${KurobaDatabase.SQLITE_TRUE}
  """)
  abstract suspend fun selectAllActiveBoards(): List<ChanBoardFull>

  suspend fun contains(siteName: String, boardCode: String): Boolean {
    return select(siteName, boardCode) != null
  }

  @Query("""
        SELECT *
        FROM ${ChanBoardIdEntity.TABLE_NAME}
        WHERE ${ChanBoardIdEntity.BOARD_ID_COLUMN_NAME} = :boardId
    """)
  abstract suspend fun select(boardId: Long): ChanBoardIdEntity?

  @Query("""
        SELECT *
        FROM ${ChanBoardIdEntity.TABLE_NAME}
        WHERE ${ChanBoardIdEntity.BOARD_ID_COLUMN_NAME} IN (:boardIdList)
    """)
  abstract suspend fun selectMany(boardIdList: List<Long>): List<ChanBoardIdEntity>

  suspend fun insert(siteName: String, boardCode: String): ChanBoardIdEntity {
    val prev = select(siteName, boardCode)
    if (prev != null) {
      return prev
    }

    val chanBoardEntity = ChanBoardIdEntity(
      boardId = 0L,
      ownerSiteName = siteName,
      boardCode = boardCode
    )

    val insertedId = insert(chanBoardEntity)
    check(insertedId >= 0L) { "Couldn't insert entity, insert() returned ${insertedId}" }

    chanBoardEntity.boardId = insertedId
    return chanBoardEntity
  }

}