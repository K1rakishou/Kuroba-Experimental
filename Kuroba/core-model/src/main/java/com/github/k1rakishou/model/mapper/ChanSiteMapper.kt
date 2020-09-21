package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.ChanSiteData
import com.github.k1rakishou.model.entity.chan.site.ChanSiteEntity
import com.github.k1rakishou.model.entity.chan.site.ChanSiteWithSettings

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