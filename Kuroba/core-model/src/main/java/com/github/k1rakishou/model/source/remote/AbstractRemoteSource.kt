package com.github.k1rakishou.model.source.remote

import okhttp3.OkHttpClient

abstract class AbstractRemoteSource(
  protected val okHttpClient: OkHttpClient
)