package com.github.k1rakishou.chan.core.base.okhttp

import okhttp3.logging.HttpLoggingInterceptor

class HttpLoggingInterceptorLazy {
  val loggingInterceptorLazyKt = lazy {
    val logging = HttpLoggingInterceptor()
    logging.setLevel(HttpLoggingInterceptor.Level.BODY)
    return@lazy logging
  }

}