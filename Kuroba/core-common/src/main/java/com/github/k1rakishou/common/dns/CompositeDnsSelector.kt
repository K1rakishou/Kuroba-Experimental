package com.github.k1rakishou.common.dns

import com.github.k1rakishou.core_logger.Logger
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

class CompositeDnsSelector(
  private val okHttpClient: OkHttpClient,
  private val okHttpUseDnsOverHttps: Boolean,
  private val normalDnsSelectorFactory: NormalDnsSelectorFactory,
  private val dnsOverHttpsSelectorFactory: DnsOverHttpsSelectorFactory
) : Dns {
  private val initialLog = AtomicBoolean(false)

  private var normalNormalDnsSelector: NormalDnsSelector? = null
  private var dnsOverHttpsSelector: Dns? = null

  override fun lookup(hostname: String): List<InetAddress> {
    if (initialLog.compareAndSet(false, true)) {
      Logger.d(TAG, "lookup okHttpUseDnsOverHttps: $okHttpUseDnsOverHttps")
    }

    if (okHttpUseDnsOverHttps) {
      return getOrCreateDnsOverHttpsSelector().lookup(hostname)
    } else {
      return getOrCreateNormalDnsSelector().lookup(hostname)
    }
  }

  @Synchronized
  fun getOrCreateDnsOverHttpsSelector(): Dns {
    if (dnsOverHttpsSelector == null) {
      dnsOverHttpsSelector = dnsOverHttpsSelectorFactory.createDnsSelector(okHttpClient)
    }

    return dnsOverHttpsSelector!!
  }

  @Synchronized
  fun getOrCreateNormalDnsSelector(): Dns {
    if (normalNormalDnsSelector == null) {
      normalNormalDnsSelector = normalDnsSelectorFactory.createDnsSelector(okHttpClient)
    }

    return normalNormalDnsSelector!!
  }

  companion object {
    private const val TAG = "CompositeDnsSelector"
  }
}