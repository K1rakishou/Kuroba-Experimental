package com.github.adamantcheese.model.data.site

import com.github.adamantcheese.json.JsonSettings
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor

data class ChanSiteData(
  val siteDescriptor: SiteDescriptor,
  var active: Boolean,
  var siteUserSettings: JsonSettings?
)