package com.github.k1rakishou.chan.features.proxies.data

sealed class ProxySetupState {
  object Uninitialized : ProxySetupState()
  object Empty : ProxySetupState()
  data class Data(val proxyEntryViewList: List<ProxyEntryView>) : ProxySetupState()
}