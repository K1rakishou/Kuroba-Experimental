package com.github.k1rakishou.chan.core.base.okhttp

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.okhttp.HttpLoggingInterceptorInstaller.install
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.core.manager.FirewallBypassManager
import com.github.k1rakishou.chan.core.net.KurobaProxySelector
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.common.dns.CompositeDnsSelector
import com.github.k1rakishou.common.dns.DnsOverHttpsSelectorFactory
import com.github.k1rakishou.common.dns.NormalDnsSelectorFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.Volatile

// this is basically the same as OkHttpClient, but with a singleton for a proxy instance
class RealProxiedOkHttpClient @Inject constructor(
  private val normalDnsSelectorFactory: NormalDnsSelectorFactory,
  private val dnsOverHttpsSelectorFactory: DnsOverHttpsSelectorFactory,
  private val proxyStorage: ProxyStorage,
  private val httpLoggingInterceptorLazy: HttpLoggingInterceptorLazy,
  private val siteResolver: SiteResolver,
  private val firewallBypassManager: FirewallBypassManager
) : ProxiedOkHttpClient {
  @Volatile
  private var proxiedClient: OkHttpClient? = null

  override fun okHttpClient(): OkHttpClient {
    if (proxiedClient == null) {
      synchronized(this) {
        if (proxiedClient == null) {
          val kurobaProxySelector = KurobaProxySelector(
            proxyStorage,
            ProxyStorage.ProxyActionType.SiteRequests
          )

          val interceptor: Interceptor = CloudFlareHandlerInterceptor(
            siteResolver,
            firewallBypassManager,
            "Generic"
          )

          // Proxies are usually slow, so they have increased timeouts
          val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .proxySelector(kurobaProxySelector)
            .addInterceptor(interceptor)

          install(builder, httpLoggingInterceptorLazy)
          val okHttpClient = builder.build()

          val compositeDnsSelector = CompositeDnsSelector(
            okHttpClient,
            ChanSettings.okHttpUseDnsOverHttps.get(),
            normalDnsSelectorFactory,
            dnsOverHttpsSelectorFactory
          )

          proxiedClient = okHttpClient.newBuilder()
            .dns(compositeDnsSelector)
            .addNetworkInterceptor(GzipInterceptor())
            .build()
        }
      }
    }

    return proxiedClient!!
  }
}