package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.core_logger.Logger

internal fun log(tag: String, message: String) {
  Logger.d(tag, String.format("[%s]: %s", Thread.currentThread().name, message))
}

internal fun logError(tag: String, message: String, error: Throwable? = null) {
  if (error == null) {
    Logger.e(tag, String.format("[%s]: %s", Thread.currentThread().name, message))
  } else {
    Logger.e(tag, String.format("[%s]: %s", Thread.currentThread().name, message), error)
  }
}