package com.github.k1rakishou.chan.core.net

import com.github.k1rakishou.chan.core.manager.ProxyStorage
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.errorMessageOrClassName
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

class KurobaProxySelector(
  private val proxyStorage: ProxyStorage,
  private val proxyActionType: ProxyStorage.ProxyActionType
) : ProxySelector() {

  override fun select(uri: URI): List<Proxy> {
    val proxies = proxyStorage.getProxyByUri(uri, proxyActionType)
    Logger.d(TAG, "select($uri) -> ${proxies}")

    if (proxies.isEmpty()) {
      return listOf(Proxy.NO_PROXY)
    }

    return proxies
  }

  override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {
    Logger.e(TAG, "connectFailed($uri, $sa, ${ioe.errorMessageOrClassName()})")
  }

  companion object {
    private const val TAG = "KurobaProxySelector"
  }
}