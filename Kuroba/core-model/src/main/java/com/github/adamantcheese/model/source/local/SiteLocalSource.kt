package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.github.adamantcheese.model.data.site.ChanSiteData
import com.github.adamantcheese.model.entity.chan.ChanSiteSettingsEntity
import com.github.adamantcheese.model.mapper.ChanSiteMapper
import com.github.adamantcheese.model.source.cache.ChanDescriptorCache

class SiteLocalSource(
  database: KurobaDatabase,
  loggerTag: String,
  private val isDevFlavor: Boolean,
  private val logger: Logger,
  private val chanDescriptorCache: ChanDescriptorCache
) : AbstractLocalSource(database) {
  private val TAG = "$loggerTag SiteLocalSource"
  private val chanSiteDao = database.chanSiteDao()

  suspend fun createDefaults(allSiteDescriptors: Collection<SiteDescriptor>) {
    ensureInTransaction()

    chanSiteDao.createDefaults(allSiteDescriptors)
  }

  suspend fun selectAllOrderedDesc(): List<ChanSiteData> {
    ensureInTransaction()

    return chanSiteDao.selectAllOrderedDescWithSettings()
      .map { chanSiteEntity -> ChanSiteMapper.fromChanSiteEntity(chanSiteEntity) }
  }

  suspend fun persist(chanSiteDataList: Collection<ChanSiteData>) {
    ensureInTransaction()

    val entities = chanSiteDataList.mapIndexed { index, chanSiteData ->
      ChanSiteMapper.toChanSiteEntity(index, chanSiteData)
    }

    chanSiteDao.updateManySites(entities)

    val settings = chanSiteDataList.mapNotNull { chanSiteData ->
      if (chanSiteData.siteUserSettings == null) {
        return@mapNotNull null
      }

      return@mapNotNull ChanSiteSettingsEntity(
        chanSiteData.siteDescriptor.siteName,
        chanSiteData.siteUserSettings
      )
    }

    chanSiteDao.updateManySettings(settings)
  }

}