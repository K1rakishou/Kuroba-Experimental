package com.github.k1rakishou.v2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class KurobaSeenSettingDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insert(kurobaSeenSettingEntity: KurobaSeenSettingEntity)

  @Query("""
    SELECT * FROM kuroba_seen_settings
  """)
  abstract suspend fun selectAll(): List<KurobaSeenSettingEntity>
}