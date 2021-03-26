package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger

internal fun log(tag: String, message: String) {
  Logger.d(tag, String.format("[%s]: %s", Thread.currentThread().name, message))
}

internal fun logError(tag: String, message: String, error: Throwable? = null) {
  if (error == null) {
    Logger.e(tag, String.format("[%s]: %s", Thread.currentThread().name, message))
  } else {
    if (error is FileCacheException.HttpCodeException
      || error is FileCacheException.CancellationException
      || error is FileCacheException.FileNotFoundOnTheServerException
    ) {
      Logger.e(tag, String.format("[%s]: %s, error=%s", Thread.currentThread().name, message, error.errorMessageOrClassName()))
    } else {
      Logger.e(tag, String.format("[%s]: %s", Thread.currentThread().name, message), error)
    }
  }
}