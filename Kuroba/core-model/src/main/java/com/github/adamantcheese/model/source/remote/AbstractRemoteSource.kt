package com.github.adamantcheese.model.source.remote

import com.github.adamantcheese.model.common.Logger
import okhttp3.OkHttpClient

abstract class AbstractRemoteSource(
  protected val okHttpClient: OkHttpClient,
  protected val logger: Logger
)