package com.github.adamantcheese.model.dao

import androidx.room.*
import com.github.adamantcheese.model.entity.archive.ThirdPartyArchiveInfoEntity

@Dao
abstract class ThirdPartyArchiveInfoDao {

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insert(thirdPartyArchiveInfoEntity: ThirdPartyArchiveInfoEntity): Long

  @Update(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun update(thirdPartyArchiveInfoEntity: ThirdPartyArchiveInfoEntity)

  @Query("""
        SELECT *
        FROM ${ThirdPartyArchiveInfoEntity.TABLE_NAME}
        WHERE ${ThirdPartyArchiveInfoEntity.ARCHIVE_DOMAIN_COLUMN_NAME} = :domain
    """)
  abstract suspend fun select(domain: String): ThirdPartyArchiveInfoEntity?

  @Query("""
        SELECT *
        FROM ${ThirdPartyArchiveInfoEntity.TABLE_NAME}
        WHERE ${ThirdPartyArchiveInfoEntity.ARCHIVE_DOMAIN_COLUMN_NAME} IN (:domainList)
    """)
  abstract suspend fun selectMany(domainList: List<String>): List<ThirdPartyArchiveInfoEntity>

  suspend fun insertOrUpdate(thirdPartyArchiveInfoEntity: ThirdPartyArchiveInfoEntity): Long {
    val prev = select(thirdPartyArchiveInfoEntity.archiveDomain)
    if (prev != null) {
      thirdPartyArchiveInfoEntity.archiveId = prev.archiveId
      update(thirdPartyArchiveInfoEntity)
      return thirdPartyArchiveInfoEntity.archiveId
    }

    return insert(thirdPartyArchiveInfoEntity)
  }

  @Query("""
        SELECT ${ThirdPartyArchiveInfoEntity.ARCHIVE_ENABLED_COLUMN_NAME}
        FROM ${ThirdPartyArchiveInfoEntity.TABLE_NAME}
        WHERE ${ThirdPartyArchiveInfoEntity.ARCHIVE_DOMAIN_COLUMN_NAME} = :domain
    """)
  abstract suspend fun isArchiveEnabled(domain: String): Boolean

  @Query("""
        UPDATE ${ThirdPartyArchiveInfoEntity.TABLE_NAME}
        SET ${ThirdPartyArchiveInfoEntity.ARCHIVE_ENABLED_COLUMN_NAME} = :enabled
        WHERE ${ThirdPartyArchiveInfoEntity.ARCHIVE_DOMAIN_COLUMN_NAME} = :domain
    """)
  abstract suspend fun updateArchiveEnabled(domain: String, enabled: Boolean)
}