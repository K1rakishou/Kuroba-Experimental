package com.github.k1rakishou.chan.core.base.okhttp

import okhttp3.OkHttpClient

interface CustomOkHttpClient {
  fun okHttpClient(): OkHttpClient
}