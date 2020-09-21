package com.github.k1rakishou.model.di

import com.github.k1rakishou.model.di.annotation.OkHttpDns
import com.github.k1rakishou.model.di.annotation.OkHttpProtocols
import dagger.Module
import dagger.Provides
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
class NetworkModule {

  @Singleton
  @Provides
  fun provideOkHttpClient(
    @OkHttpDns dns: Dns,
    @OkHttpProtocols okHttpProtocols: OkHttpProtocolList
  ): OkHttpClient {
    return OkHttpClient().newBuilder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .protocols(okHttpProtocols.protocols)
      .dns(dns)
      .build()
  }

  class OkHttpProtocolList(val protocols: List<Protocol>)
}