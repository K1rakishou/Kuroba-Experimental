package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomWarnings
import androidx.room.Update
import com.github.k1rakishou.model.entity.chan.filter.ChanFilterBoardConstraintEntity
import com.github.k1rakishou.model.entity.chan.filter.ChanFilterEntity
import com.github.k1rakishou.model.entity.chan.filter.ChanFilterFull
import java.io.IOException

@Dao
abstract class ChanFilterDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertOrIgnore(chanFilterEntity: ChanFilterEntity): Long

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertOrIgnore(chanFilterBoardConstraintEntityList: List<ChanFilterBoardConstraintEntity>)

  @Update(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun updateMany(chanFilterEntityList: List<ChanFilterEntity>)

  @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
  @Query("""
    SELECT *
    FROM ${ChanFilterEntity.TABLE_NAME} cfe
    LEFT JOIN ${ChanFilterBoardConstraintEntity.TABLE_NAME} cfbce
        ON cfe.${ChanFilterEntity.FILTER_ID_COLUMN_NAME} = cfbce.${ChanFilterBoardConstraintEntity.OWNER_FILTER_ID_COLUMN_NAME}
    GROUP BY cfe.${ChanFilterEntity.FILTER_ID_COLUMN_NAME}
    ORDER BY cfe.${ChanFilterEntity.FILTER_ORDER_COLUMN_NAME} ASC
  """)
  abstract suspend fun selectAll(): List<ChanFilterFull>

  @Query("""
    DELETE 
    FROM ${ChanFilterBoardConstraintEntity.TABLE_NAME}
    WHERE ${ChanFilterBoardConstraintEntity.OWNER_FILTER_ID_COLUMN_NAME} = :filterDatabaseId
  """)
  abstract suspend fun deleteAllBoardConstraintsForFilter(filterDatabaseId: Long)

  suspend fun insertOrIgnore(chanFilterFull: ChanFilterFull): Long {
    require(chanFilterFull.chanFilterEntity.filterId <= 0L) {
      "chanFilterEntity (${chanFilterFull.chanFilterEntity}) already has DB id!"
    }

    val databaseId = insertOrIgnore(chanFilterFull.chanFilterEntity)
    if (databaseId <= 0L) {
      throw IOException("Failed to insert ChanFilterEntity: ${chanFilterFull.chanFilterEntity}, id = ${databaseId}")
    }

    deleteAllBoardConstraintsForFilter(databaseId)

    chanFilterFull.chanFilterBoardConstraintEntityList.forEach { chanFilterBoardConstraintEntity ->
      chanFilterBoardConstraintEntity.ownerFilterId = databaseId
    }
    insertOrIgnore(chanFilterFull.chanFilterBoardConstraintEntityList)

    return databaseId
  }

  suspend fun updateManyOrIgnore(chanFilterFullList: List<ChanFilterFull>) {
    chanFilterFullList.forEach { chanFilterFull ->
      require(chanFilterFull.chanFilterEntity.filterId > 0L) {
        "chanFilterEntity (${chanFilterFull.chanFilterEntity}) does not have DB id!"
      }
    }

    updateMany(chanFilterFullList.map { chanFilterFull -> chanFilterFull.chanFilterEntity })

    chanFilterFullList.forEach { chanFilterFull ->
      deleteAllBoardConstraintsForFilter(chanFilterFull.chanFilterEntity.filterId)
      insertOrIgnore(chanFilterFull.chanFilterBoardConstraintEntityList)
    }
  }

  @Query("""
    DELETE 
    FROM ${ChanFilterEntity.TABLE_NAME} 
    WHERE ${ChanFilterEntity.FILTER_ID_COLUMN_NAME} = :databaseId
  """)
  abstract suspend fun deleteById(databaseId: Long)

}