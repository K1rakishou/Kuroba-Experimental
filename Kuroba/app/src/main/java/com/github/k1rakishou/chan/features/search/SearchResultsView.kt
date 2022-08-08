package com.github.k1rakishou.chan.features.search

import com.github.k1rakishou.chan.features.bypass.FirewallType
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import okhttp3.HttpUrl

interface SearchResultsView {
  fun onFirewallDetected(firewallType: FirewallType, siteDescriptor: SiteDescriptor, requestUrl: HttpUrl)
}