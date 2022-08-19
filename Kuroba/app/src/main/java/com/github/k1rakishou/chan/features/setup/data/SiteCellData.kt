package com.github.k1rakishou.chan.features.setup.data

import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

data class SiteCellData(
  val siteDescriptor: SiteDescriptor,
  val siteIcon: SiteIcon,
  val siteName: String,
  val siteEnableState: SiteEnableState
)