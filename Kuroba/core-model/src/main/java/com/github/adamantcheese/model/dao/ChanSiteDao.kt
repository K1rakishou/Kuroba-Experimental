package com.github.adamantcheese.model.dao

import androidx.room.*
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.github.adamantcheese.model.entity.chan.ChanSiteEntity
import com.github.adamantcheese.model.entity.chan.ChanSiteIdEntity
import com.github.adamantcheese.model.entity.chan.ChanSiteSettingsEntity
import com.github.adamantcheese.model.entity.chan.ChanSiteWithSettings

@Dao
abstract class ChanSiteDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertDefaultSiteIdsOrIgnore(siteIdEntityList: List<ChanSiteIdEntity>)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertDefaultSitesOrIgnore(siteEntityList: List<ChanSiteEntity>)

  @Update(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun updateManySites(entities: List<ChanSiteEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun updateManySettings(settings: List<ChanSiteSettingsEntity>)

  @Query("SELECT * FROM ${ChanSiteIdEntity.TABLE_NAME}")
  abstract suspend fun selectAllSiteIdEntities(): List<ChanSiteIdEntity>

  @Query("""
    SELECT * 
    FROM ${ChanSiteIdEntity.TABLE_NAME} csie
    INNER JOIN ${ChanSiteEntity.TABLE_NAME} cse
        ON csie.${ChanSiteIdEntity.SITE_NAME_COLUMN_NAME} = cse.${ChanSiteEntity.OWNER_CHAN_SITE_NAME_COLUMN_NAME}
    LEFT JOIN ${ChanSiteSettingsEntity.TABLE_NAME} csse
        ON csie.${ChanSiteIdEntity.SITE_NAME_COLUMN_NAME} = csse.${ChanSiteSettingsEntity.OWNER_CHAN_SITE_NAME_COLUMN_NAME}
    ORDER BY ${ChanSiteEntity.SITE_ORDER_COLUMN_NAME} DESC
  """)
  abstract suspend fun selectAllOrderedDescWithSettings(): List<ChanSiteWithSettings>

  suspend fun createDefaultsIfNecessary(allSiteDescriptors: Collection<SiteDescriptor>) {
    val existingSiteDescriptors = selectAllSiteIdEntities()
      .map { chanSiteIdEntity -> SiteDescriptor(chanSiteIdEntity.siteName) }
      .toSet()

    val chanSiteIdEntityList = allSiteDescriptors
      .filter { siteDescriptor -> siteDescriptor !in existingSiteDescriptors }
      .map { siteDescriptor -> ChanSiteIdEntity(siteDescriptor.siteName) }

    if (chanSiteIdEntityList.isEmpty()) {
      return
    }

    insertDefaultSiteIdsOrIgnore(chanSiteIdEntityList)

    val defaultSites = allSiteDescriptors.mapIndexed { index, siteDescriptor ->
      ChanSiteEntity(siteDescriptor.siteName, false, index)
    }

    insertDefaultSitesOrIgnore(defaultSites)
  }

}