package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.github.adamantcheese.model.data.site.ChanSiteData
import com.github.adamantcheese.model.entity.chan.ChanSiteEntity
import com.github.adamantcheese.model.entity.chan.ChanSiteWithSettings

object ChanSiteMapper {

  fun fromChanSiteEntity(chanSiteWithSettings: ChanSiteWithSettings): ChanSiteData {
    return ChanSiteData(
      SiteDescriptor(chanSiteWithSettings.chanSiteIdEntity.siteName),
      chanSiteWithSettings.chanSiteEntity.siteActive,
      chanSiteWithSettings.chanSiteSettingsEntity?.userSettings
    )
  }

  fun toChanSiteEntity(index: Int, chanSiteData: ChanSiteData): ChanSiteEntity {
    return ChanSiteEntity(
      chanSiteData.siteDescriptor.siteName,
      chanSiteData.active,
      index
    )
  }

}