package com.github.k1rakishou.model.source.remote

import com.github.k1rakishou.model.common.Logger
import okhttp3.OkHttpClient

abstract class AbstractRemoteSource(
  protected val okHttpClient: OkHttpClient,
  protected val logger: Logger
)