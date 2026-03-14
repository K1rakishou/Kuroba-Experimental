package com.github.k1rakishou.chan.core.base.okhttp

import com.github.k1rakishou.chan.core.base.okhttp.interceptor.GzipInterceptor
import com.github.k1rakishou.chan.core.base.okhttp.interceptor.HttpLoggingInterceptorInstaller.install
import com.github.k1rakishou.chan.core.base.okhttp.interceptor.HttpLoggingInterceptorLazy
import com.github.k1rakishou.chan.core.base.okhttp.interceptor.KurobaOkHttpInterceptor
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.core.net.KurobaProxySelector
import com.github.k1rakishou.common.dns.CompositeDnsSelector
import com.github.k1rakishou.common.dns.DnsOverHttpsSelectorFactory
import com.github.k1rakishou.common.dns.NormalDnsSelectorFactory
import com.github.k1rakishou.v2.KurobaSettings
import okhttp3.OkHttpClient
import kotlin.concurrent.Volatile

class CoilOkHttpClient(
  private val kurobaSettings: KurobaSettings,
  private val normalDnsSelectorFactory: NormalDnsSelectorFactory,
  private val dnsOverHttpsSelectorFactory: DnsOverHttpsSelectorFactory,
  private val proxyStorage: ProxyStorage,
  private val httpLoggingInterceptorLazy: HttpLoggingInterceptorLazy,
  private val interceptors: Set<KurobaOkHttpInterceptor>
) : CustomOkHttpClient {
  @Volatile
  private var coilClient: OkHttpClient? = null

  override fun okHttpClient(): OkHttpClient {
    if (coilClient == null) {
      synchronized(this) {
        if (coilClient == null) {
          val kurobaProxySelector = KurobaProxySelector(
            proxyStorage,
            ProxyStorage.ProxyActionType.SiteMediaPreviews
          )

          val builder = OkHttpClient.Builder()
            .proxySelector(kurobaProxySelector)

          interceptors.forEach { interceptor ->
            interceptor.okHttpType = "Coil"
            builder.addInterceptor(interceptor)
          }

          install(builder, httpLoggingInterceptorLazy)
          val okHttpClient = builder.build()

          val compositeDnsSelector = CompositeDnsSelector(
            okHttpClient = okHttpClient,
            okHttpUseDnsOverHttps = kurobaSettings.application.okHttpUseDnsOverHttps.readBlocking(),
            normalDnsSelectorFactory = normalDnsSelectorFactory,
            dnsOverHttpsSelectorFactory = dnsOverHttpsSelectorFactory
          )

          coilClient = okHttpClient.newBuilder()
            .dns(compositeDnsSelector)
            .addNetworkInterceptor(GzipInterceptor())
            .build()
        }
      }
    }

    return coilClient!!
  }
}
