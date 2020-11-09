package com.github.k1rakishou.chan.core.base.okhttp

import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.core_logger.Logger
import okhttp3.OkHttpClient

object HttpLoggingInterceptorInstaller {

  @JvmStatic
  fun install(
    okHttpClientBuilder: OkHttpClient.Builder,
    httpLoggingInterceptorLazy: HttpLoggingInterceptorLazy
  ) {
    if (!isDevBuild()) {
      return
    }

    Logger.e("HttpLoggingInterceptorInstaller", "\n\n\nHttpLoggingInterceptor have been installed. " +
      "If you see this message in a not development build (beta/stable) then this is a bug!!!\n\n\n")

    okHttpClientBuilder.addInterceptor(httpLoggingInterceptorLazy.loggingInterceptorLazyKt.value)
    return
  }

}