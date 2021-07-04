package com.github.k1rakishou.chan.features.search

import com.github.k1rakishou.chan.features.bypass.FirewallType
import okhttp3.HttpUrl

interface SearchResultsView {
  fun onFirewallDetected(firewallType: FirewallType, requestUrl: HttpUrl)
}