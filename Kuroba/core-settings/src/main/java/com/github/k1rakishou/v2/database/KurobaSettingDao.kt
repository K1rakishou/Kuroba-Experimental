package com.github.k1rakishou.v2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class KurobaSettingDao {
  @Query("""
    SELECT *
    FROM kuroba_settings
    WHERE setting_key = :key
  """)
  abstract suspend fun selectByKey(key: String): KurobaSettingEntity?

  @Query("""
    SELECT * FROM kuroba_settings
  """)
  abstract suspend fun selectAll(): List<KurobaSettingEntity>

  suspend fun upsert(entity: KurobaSettingEntity) {
    if (insertOnce(entity) != -1L) {
      return
    }

    update(
      key = entity.key,
      value = entity.value,
      createdOn = entity.createdOn,
      lastAccessedOn = entity.lastAccessedOn
    )
  }

  @Query("""
    DELETE
    FROM kuroba_settings
    WHERE setting_key = :key
  """)
  abstract suspend fun deleteByKey(key: String)

  @Query("""
    DELETE
    FROM kuroba_settings
    WHERE backupable = 0
  """)
  abstract suspend fun deleteNonBackupable()

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  protected abstract suspend fun insertOnce(entity: KurobaSettingEntity): Long

  @Query("""
    UPDATE kuroba_settings 
    SET 
        setting_value = :value,
        created_on = CASE 
            WHEN created_on = 0 
            THEN :createdOn 
            ELSE created_on 
        END,
        last_accessed_on = CASE 
            WHEN :lastAccessedOn > last_accessed_on 
            THEN :lastAccessedOn 
            ELSE last_accessed_on 
        END
    WHERE setting_key = :key
""")
  protected abstract suspend fun update(
    key: String,
    value: ByteArray?,
    createdOn: Long,
    lastAccessedOn: Long
  )
}