package com.github.adamantcheese.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.model.entity.ChanTextSpanEntity

@Dao
abstract class ChanTextSpanDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertMany(chanTextSpanEntityList: List<ChanTextSpanEntity>): List<Long>

  @Query("""
        SELECT *
        FROM ${ChanTextSpanEntity.TABLE_NAME}
        WHERE ${ChanTextSpanEntity.OWNER_POST_ID_COLUMN_NAME} IN (:ownerPostIdList)
    """)
  abstract suspend fun selectManyByOwnerPostIdList(
    ownerPostIdList: List<Long>
  ): List<ChanTextSpanEntity>

  @Query("SELECT * FROM ${ChanTextSpanEntity.TABLE_NAME}")
  abstract suspend fun testGetAll(): List<ChanTextSpanEntity>
}