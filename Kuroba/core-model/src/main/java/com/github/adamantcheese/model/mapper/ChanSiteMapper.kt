package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.github.adamantcheese.model.data.site.ChanSiteData
import com.github.adamantcheese.model.entity.chan.ChanSiteEntity

object ChanSiteMapper {

  fun fromChanSiteEntity(chanSiteEntity: ChanSiteEntity): ChanSiteData {
    return ChanSiteData(
      SiteDescriptor(chanSiteEntity.ownerChanSiteName),
      chanSiteEntity.siteActive,
      chanSiteEntity.userSettings
    )
  }

  fun toChanSiteEntity(index: Int, chanSiteData: ChanSiteData): ChanSiteEntity {
    return ChanSiteEntity(
      chanSiteData.siteDescriptor.siteName,
      chanSiteData.active,
      index,
      chanSiteData.siteUserSettings
    )
  }

}