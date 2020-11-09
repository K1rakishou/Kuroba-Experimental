package com.github.k1rakishou.chan.features.proxies.data

import com.github.k1rakishou.chan.core.helper.ProxyStorage

data class ProxyEntryView(
  val address: String,
  val port: Int,
  val enabled: Boolean,
  val selection: ProxyEntryViewSelection?,
  val supportedSites: String,
  val supportedActions: String,
  val proxyType: String
) {

  fun proxyKeyString(): String = "${address}_${port}"

  fun proxyKey(): ProxyStorage.ProxyKey = ProxyStorage.ProxyKey(address, port)

}

data class ProxyEntryViewSelection(
  @get:Synchronized
  @set:Synchronized
  var selected: Boolean
)