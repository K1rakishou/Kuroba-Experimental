package com.github.k1rakishou.chan.core.base.okhttp

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.okhttp.interceptor.GzipInterceptor
import com.github.k1rakishou.chan.core.base.okhttp.interceptor.HttpLoggingInterceptorInstaller.install
import com.github.k1rakishou.chan.core.base.okhttp.interceptor.HttpLoggingInterceptorLazy
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.core.net.KurobaProxySelector
import com.github.k1rakishou.common.dns.CompositeDnsSelector
import com.github.k1rakishou.common.dns.DnsOverHttpsSelectorFactory
import com.github.k1rakishou.common.dns.NormalDnsSelectorFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

interface OkHttpClientForInterceptors : CustomOkHttpClient

class OkHttpClientForInterceptorsImpl(
  private val normalDnsSelectorFactory: NormalDnsSelectorFactory,
  private val dnsOverHttpsSelectorFactory: DnsOverHttpsSelectorFactory,
  private val proxyStorage: ProxyStorage,
  private val httpLoggingInterceptorLazy: HttpLoggingInterceptorLazy,
) : OkHttpClientForInterceptors {
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

          // Proxies are usually slow, so they have increased timeouts
          val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .proxySelector(kurobaProxySelector)

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