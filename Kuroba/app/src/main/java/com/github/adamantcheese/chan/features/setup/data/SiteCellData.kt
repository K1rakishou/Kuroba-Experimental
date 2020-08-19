package com.github.adamantcheese.chan.features.setup.data

import com.github.adamantcheese.model.data.descriptor.SiteDescriptor

data class SiteCellData(
  val siteDescriptor: SiteDescriptor,
  val siteIcon: String,
  val siteName: String,
  val siteEnableState: SiteEnableState
)