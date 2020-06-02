package com.github.adamantcheese.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.model.entity.chan.ChanPostHttpIconEntity
import okhttp3.HttpUrl

@Dao
abstract class ChanPostHttpIconDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertMany(chanPostHttpIconEntityList: List<ChanPostHttpIconEntity>): List<Long>

  @Query("""
        SELECT *
        FROM ${ChanPostHttpIconEntity.TABLE_NAME}
        WHERE ${ChanPostHttpIconEntity.ICON_URL_COLUMN_NAME} = :iconUrl
    """)
  abstract suspend fun selectByIconUrl(iconUrl: HttpUrl): ChanPostHttpIconEntity?

  @Query("""
        SELECT *
        FROM ${ChanPostHttpIconEntity.TABLE_NAME}
        WHERE ${ChanPostHttpIconEntity.OWNER_POST_ID_COLUMN_NAME} IN (:ownerPostIdList)
    """)
  abstract suspend fun selectByOwnerPostIdList(ownerPostIdList: List<Long>): List<ChanPostHttpIconEntity>

  @Query("SELECT * FROM ${ChanPostHttpIconEntity.TABLE_NAME}")
  abstract suspend fun testGetAll(): List<ChanPostHttpIconEntity>
}