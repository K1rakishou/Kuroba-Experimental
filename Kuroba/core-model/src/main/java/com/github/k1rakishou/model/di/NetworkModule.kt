package com.github.k1rakishou.model.di

import com.github.k1rakishou.common.dns.CompositeDnsSelector
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
class NetworkModule {

  @Singleton
  @Provides
  fun provideOkHttpClient(dependencies: ModelComponent.Dependencies): OkHttpClient {
    val okHttpClient = OkHttpClient().newBuilder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .protocols(dependencies.okHttpProtocols.protocols)
      .build()

    val compositeDnsSelector = CompositeDnsSelector(
      okHttpClient,
      dependencies.okHttpUseDnsOverHttps,
      dependencies.normalDnsSelectorFactory,
      dependencies.dnsOverHttpsSelectorFactory
    )

    return okHttpClient.newBuilder()
      .dns(compositeDnsSelector)
      .build()
  }

  class OkHttpProtocolList(val protocols: List<Protocol>)
}