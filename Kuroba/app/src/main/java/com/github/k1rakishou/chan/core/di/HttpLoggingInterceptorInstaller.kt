package com.github.k1rakishou.chan.core.di

import com.github.k1rakishou.chan.core.base.okhttp.HttpLoggingInterceptorLazy
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.Logger
import okhttp3.OkHttpClient

object HttpLoggingInterceptorInstaller {

  @JvmStatic
  fun install(
    okHttpClientBuilder: OkHttpClient.Builder,
    httpLoggingInterceptorLazy: HttpLoggingInterceptorLazy
  ) {
    if (!AndroidUtils.isDevBuild()) {
      return
    }

    Logger.e("HttpLoggingInterceptorInstaller", "\n\n\nHttpLoggingInterceptor have been installed. " +
      "If you see this message in a not development build (beta/stable) then this is a bug!!!\n\n\n")

    okHttpClientBuilder.addInterceptor(httpLoggingInterceptorLazy.loggingInterceptorLazyKt.value)
    return
  }

}