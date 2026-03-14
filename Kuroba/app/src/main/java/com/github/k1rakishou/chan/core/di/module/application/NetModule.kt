package com.github.k1rakishou.chan.core.di.module.application

import android.content.Context
import android.net.ConnectivityManager
import com.github.k1rakishou.chan.core.base.okhttp.CoilOkHttpClient
import com.github.k1rakishou.chan.core.base.okhttp.DownloaderOkHttpClient
import com.github.k1rakishou.chan.core.base.okhttp.DownloaderOkHttpClientImpl
import com.github.k1rakishou.chan.core.base.okhttp.OkHttpClientForInterceptors
import com.github.k1rakishou.chan.core.base.okhttp.OkHttpClientForInterceptorsImpl
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClientImpl
import com.github.k1rakishou.chan.core.base.okhttp.interceptor.Chan8MoeInterceptor
import com.github.k1rakishou.chan.core.base.okhttp.interceptor.CloudFlareInterceptor
import com.github.k1rakishou.chan.core.base.okhttp.interceptor.HttpLoggingInterceptorLazy
import com.github.k1rakishou.chan.core.base.okhttp.interceptor.KurobaOkHttpInterceptor
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.downloader.ChunkedMediaDownloader
import com.github.k1rakishou.chan.core.cache.downloader.ChunkedMediaDownloaderImpl
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.core.manager.FirewallBypassManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.http.HttpCallManager
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.dns.DnsOverHttpsSelectorFactory
import com.github.k1rakishou.common.dns.NormalDnsSelectorFactory
import com.github.k1rakishou.core_logger.Logger.deps
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.v2.KurobaSettings
import com.google.gson.Gson
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
class NetModule {
  @Provides
  @Singleton
  fun provideHttpLoggingInterceptorLazy(): HttpLoggingInterceptorLazy {
    return HttpLoggingInterceptorLazy()
  }

  @Provides
  @Singleton
  fun provideProxyStorage(
    appScope: CoroutineScope,
    appContext: Context,
    kurobaSettings: KurobaSettings,
    appConstants: AppConstants,
    siteResolver: SiteResolver,
    gson: Gson
  ): ProxyStorage {
    deps("ProxyStorage")
    return ProxyStorage(
      appScope,
      appContext,
      appConstants,
      kurobaSettings.application.verboseLogs.readBlocking(),
      siteResolver,
      gson
    )
  }

  @Provides
  @Singleton
  fun provideCacheHandler(
    appConstants: AppConstants,
    kurobaSettings: KurobaSettings,
  ): CacheHandler {
    deps("CacheHandler")

    return CacheHandler(
      appConstants,
      kurobaSettings
    )
  }

  @Provides
  @Singleton
  fun provideChunkedMediaDownloaderImpl(
    appConstants: AppConstants,
    fileManager: FileManager,
    siteResolver: SiteResolver,
    cacheHandler: Lazy<CacheHandler>,
    downloaderOkHttpClient: Lazy<DownloaderOkHttpClient>,
    connectivityManager: ConnectivityManager
  ): ChunkedMediaDownloader {
    deps("ChunkedMediaDownloader")

    return ChunkedMediaDownloaderImpl(
      appConstants,
      fileManager,
      siteResolver,
      cacheHandler,
      downloaderOkHttpClient,
      connectivityManager
    )
  }

  @Provides
  @Singleton
  fun provideHttpCallManager(
    okHttpClient: Lazy<ProxiedOkHttpClient>,
    appConstants: AppConstants
  ): HttpCallManager {
    deps("HttpCallManager")
    return HttpCallManager(okHttpClient, appConstants)
  }

  @Provides
  @IntoSet
  fun provideCloudflareInterceptor(
    siteResolver: SiteResolver,
    firewallBypassManager: FirewallBypassManager
  ): KurobaOkHttpInterceptor {
    deps("CloudFlareInterceptor")
    return CloudFlareInterceptor(
      siteResolver = siteResolver,
      firewallBypassManager = firewallBypassManager,
    )
  }

  @Provides
  @IntoSet
  fun provideChan8MoeInterceptor(
    okHttpClient: OkHttpClientForInterceptors,
    siteResolver: SiteResolver
  ): KurobaOkHttpInterceptor {
    deps("Chan8MoeInterceptor")
    return Chan8MoeInterceptor(
      okHttpClient = okHttpClient,
      siteResolver = siteResolver
    )
  }

  /**
   * This okHttpClient is for posting.
   */
  @Provides
  @Singleton
  fun provideProxiedOkHttpClient(
    kurobaSettings: KurobaSettings,
    normalDnsSelectorFactory: NormalDnsSelectorFactory,
    dnsOverHttpsSelectorFactory: DnsOverHttpsSelectorFactory,
    proxyStorage: ProxyStorage,
    httpLoggingInterceptorLazy: HttpLoggingInterceptorLazy,
    interceptors: Set<@JvmSuppressWildcards KurobaOkHttpInterceptor>
  ): ProxiedOkHttpClient {
    deps("ProxiedOkHttpClient")

    return ProxiedOkHttpClientImpl(
      kurobaSettings = kurobaSettings,
      normalDnsSelectorFactory = normalDnsSelectorFactory,
      dnsOverHttpsSelectorFactory = dnsOverHttpsSelectorFactory,
      proxyStorage = proxyStorage,
      httpLoggingInterceptorLazy = httpLoggingInterceptorLazy,
      interceptors = interceptors
    )
  }

  /**
   * This okHttpClient is for Coil image loading library
   */
  @Provides
  @Singleton
  fun provideCoilOkHttpClient(
    kurobaSettings: KurobaSettings,
    normalDnsSelectorFactory: NormalDnsSelectorFactory,
    dnsOverHttpsSelectorFactory: DnsOverHttpsSelectorFactory,
    proxyStorage: ProxyStorage,
    httpLoggingInterceptorLazy: HttpLoggingInterceptorLazy,
    interceptors: Set<@JvmSuppressWildcards KurobaOkHttpInterceptor>
  ): CoilOkHttpClient {
    deps("CoilOkHttpClient")

    return CoilOkHttpClient(
      kurobaSettings = kurobaSettings,
      normalDnsSelectorFactory = normalDnsSelectorFactory,
      dnsOverHttpsSelectorFactory = dnsOverHttpsSelectorFactory,
      proxyStorage = proxyStorage,
      httpLoggingInterceptorLazy = httpLoggingInterceptorLazy,
      interceptors = interceptors
    )
  }

  /**
   * This okHttpClient is for images/file/apk updates/ downloading, prefetching, etc.
   */
  @Provides
  @Singleton
  fun provideDownloaderOkHttpClient(
    kurobaSettings: KurobaSettings,
    normalDnsSelectorFactory: NormalDnsSelectorFactory,
    dnsOverHttpsSelectorFactory: DnsOverHttpsSelectorFactory,
    proxyStorage: ProxyStorage,
    httpLoggingInterceptorLazy: HttpLoggingInterceptorLazy,
    interceptors: Set<@JvmSuppressWildcards KurobaOkHttpInterceptor>
  ): DownloaderOkHttpClient {
    deps("DownloaderOkHttpClient")

    return DownloaderOkHttpClientImpl(
      kurobaSettings = kurobaSettings,
      normalDnsSelectorFactory = normalDnsSelectorFactory,
      dnsOverHttpsSelectorFactory = dnsOverHttpsSelectorFactory,
      proxyStorage = proxyStorage,
      httpLoggingInterceptorLazy = httpLoggingInterceptorLazy,
      interceptors = interceptors
    )
  }

  /**
   * Special OkHttpClient which is only for use inside of OkHttpInterceptors
   * */
  @Provides
  @Singleton
  fun provideOkHttpClientForInterceptors(
    kurobaSettings: KurobaSettings,
    normalDnsSelectorFactory: NormalDnsSelectorFactory,
    dnsOverHttpsSelectorFactory: DnsOverHttpsSelectorFactory,
    proxyStorage: ProxyStorage,
    httpLoggingInterceptorLazy: HttpLoggingInterceptorLazy,
  ): OkHttpClientForInterceptors {
    deps("OkHttpClientForInterceptorsImpl")
    return OkHttpClientForInterceptorsImpl(
      kurobaSettings = kurobaSettings,
      normalDnsSelectorFactory = normalDnsSelectorFactory,
      dnsOverHttpsSelectorFactory = dnsOverHttpsSelectorFactory,
      proxyStorage = proxyStorage,
      httpLoggingInterceptorLazy = httpLoggingInterceptorLazy,
    )
  }
}
