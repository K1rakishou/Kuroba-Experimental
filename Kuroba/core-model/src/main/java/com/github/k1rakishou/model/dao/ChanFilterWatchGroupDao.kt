package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.k1rakishou.model.entity.chan.filter.ChanFilterWatchGroupEntity

@Dao
abstract class ChanFilterWatchGroupDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertMany(watchGroups: List<ChanFilterWatchGroupEntity>)

  @Query("SELECT * FROM ${ChanFilterWatchGroupEntity.TABLE_NAME}")
  abstract suspend fun selectAll(): List<ChanFilterWatchGroupEntity>

  @Query("DELETE FROM ${ChanFilterWatchGroupEntity.TABLE_NAME}")
  abstract suspend fun deleteAll()

}