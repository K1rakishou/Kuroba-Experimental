package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.ChanSiteData
import com.github.k1rakishou.model.entity.chan.site.ChanSiteEntity
import com.github.k1rakishou.model.entity.chan.site.ChanSiteFull

object ChanSiteMapper {

  fun fromChanSiteEntity(chanSiteFull: ChanSiteFull): ChanSiteData {
    return ChanSiteData(
      SiteDescriptor(chanSiteFull.chanSiteIdEntity.siteName),
      chanSiteFull.chanSiteEntity.siteActive
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