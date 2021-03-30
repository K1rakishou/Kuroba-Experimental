package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomWarnings
import androidx.room.Update
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.entity.chan.site.ChanSiteEntity
import com.github.k1rakishou.model.entity.chan.site.ChanSiteFull
import com.github.k1rakishou.model.entity.chan.site.ChanSiteIdEntity

@Dao
abstract class ChanSiteDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertDefaultSiteIdsOrIgnore(siteIdEntityList: List<ChanSiteIdEntity>)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertDefaultSitesOrIgnore(siteEntityList: List<ChanSiteEntity>)

  @Update(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun updateManySites(entities: List<ChanSiteEntity>)

  @Query("SELECT * FROM ${ChanSiteIdEntity.TABLE_NAME}")
  abstract suspend fun selectAllSiteIdEntities(): List<ChanSiteIdEntity>

  @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
  @Query("""
    SELECT * 
    FROM ${ChanSiteIdEntity.TABLE_NAME} csie
    INNER JOIN ${ChanSiteEntity.TABLE_NAME} cse
        ON csie.${ChanSiteIdEntity.SITE_NAME_COLUMN_NAME} = cse.${ChanSiteEntity.OWNER_CHAN_SITE_NAME_COLUMN_NAME}
    ORDER BY ${ChanSiteEntity.SITE_ORDER_COLUMN_NAME} DESC
  """)
  abstract suspend fun selectAllOrderedDescWithSettings(): List<ChanSiteFull>

  suspend fun createDefaultsIfNecessary(allSiteDescriptors: Collection<SiteDescriptor>) {
    val existingSiteDescriptors = selectAllSiteIdEntities()
      .map { chanSiteIdEntity -> SiteDescriptor.create(chanSiteIdEntity.siteName) }
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