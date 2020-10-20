package com.github.k1rakishou.chan.features.proxies.data

import com.github.k1rakishou.chan.core.manager.ProxyStorage

data class ProxyEntryView(
  val address: String,
  val port: Int,
  val enabled: Boolean,
  val supportedSites: String,
  val supportedActions: String,
  val proxyType: String
) {

  fun proxyKeyString(): String = "${address}_${port}"

  fun proxyKey(): ProxyStorage.ProxyKey = ProxyStorage.ProxyKey(address, port)

}