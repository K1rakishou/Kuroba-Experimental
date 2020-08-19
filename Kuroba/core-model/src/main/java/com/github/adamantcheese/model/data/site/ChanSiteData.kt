package com.github.adamantcheese.model.data.site

import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.github.adamantcheese.model.data.misc.KeyValueSettings

data class ChanSiteData(
  val siteDescriptor: SiteDescriptor,
  var active: Boolean,
  var siteUserSettings: KeyValueSettings?
)