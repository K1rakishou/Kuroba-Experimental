package com.github.k1rakishou.chan.core.base.okhttp.interceptor

import com.github.k1rakishou.core_logger.Logger
import okhttp3.OkHttpClient

object HttpLoggingInterceptorInstaller {
  private const val LoggingInterceptorEnabled = false

  @JvmStatic
  fun install(
    okHttpClientBuilder: OkHttpClient.Builder,
    httpLoggingInterceptorLazy: HttpLoggingInterceptorLazy
  ) {
    if (!LoggingInterceptorEnabled) {
      return
    }

    Logger.e("HttpLoggingInterceptorInstaller", "\n\n\nHttpLoggingInterceptor have been installed. " +
      "If you see this message in a not development build (beta/stable) then this is a bug!!!\n\n\n")

    okHttpClientBuilder.addInterceptor(httpLoggingInterceptorLazy.loggingInterceptorLazyKt.value)
    return
  }

}