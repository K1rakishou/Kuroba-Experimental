package com.github.k1rakishou.model.data.site

import com.github.k1rakishou.json.JsonSettings
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

data class ChanSiteData(
  val siteDescriptor: SiteDescriptor,
  var active: Boolean,
  var siteUserSettings: JsonSettings?
)