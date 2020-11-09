package com.github.k1rakishou.model.di

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
    return OkHttpClient().newBuilder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .protocols(dependencies.okHttpProtocols.protocols)
      .dns(dependencies.dns)
      .build()
  }

  class OkHttpProtocolList(val protocols: List<Protocol>)
}