package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.k1rakishou.model.entity.chan.catalog.ChanCatalogSnapshotEntity

@Dao
abstract class ChanCatalogSnapshotDao {

  @Query("""
    SELECT *
    FROM ${ChanCatalogSnapshotEntity.TABLE_NAME}
    WHERE ${ChanCatalogSnapshotEntity.OWNER_BOARD_ID_COLUMN_NAME} = :ownerBoardId
    ORDER BY ${ChanCatalogSnapshotEntity.THREAD_ORDER_COLUMN_NAME} ASC
  """)
  abstract suspend fun selectManyByBoardIdOrdered(ownerBoardId: Long): List<ChanCatalogSnapshotEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertMany(chanCatalogSnapshotEntityList: List<ChanCatalogSnapshotEntity>)

  @Query("""
    DELETE
    FROM ${ChanCatalogSnapshotEntity.TABLE_NAME}
    WHERE ${ChanCatalogSnapshotEntity.OWNER_BOARD_ID_COLUMN_NAME} = :ownerBoardId
  """)
  abstract suspend fun deleteManyByBoardId(ownerBoardId: Long)

}