package com.github.adamantcheese.model.dao

import androidx.room.*
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.github.adamantcheese.model.entity.chan.ChanSiteEntity
import com.github.adamantcheese.model.entity.chan.ChanSiteIdEntity

@Dao
abstract class ChanSiteDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertDefaultSiteIdsOrIgnore(siteIdEntityList: List<ChanSiteIdEntity>)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertDefaultSitesOrIgnore(siteEntityList: List<ChanSiteEntity>)

  @Update(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun updateMany(entities: List<ChanSiteEntity>)

  @Query("""
    SELECT * 
    FROM ${ChanSiteEntity.TABLE_NAME}
    ORDER BY ${ChanSiteEntity.SITE_ORDER_COLUMN_NAME} DESC
  """)
  abstract suspend fun selectAllOrderedDesc(): List<ChanSiteEntity>

  suspend fun createDefaults(allSiteDescriptors: Collection<SiteDescriptor>) {
    val chanSiteIdEntityList = allSiteDescriptors.map { siteDescriptor ->
      ChanSiteIdEntity(siteDescriptor.siteName)
    }

    insertDefaultSiteIdsOrIgnore(chanSiteIdEntityList)

    val defaultSites = allSiteDescriptors.mapIndexed { index, siteDescriptor ->
      ChanSiteEntity(siteDescriptor.siteName, false, index, null)
    }

    insertDefaultSitesOrIgnore(defaultSites)
  }

}