package com.github.adamantcheese.model.common

import android.util.Log

open class Logger(private val verboseLogs: Boolean) {
  internal open fun log(tag: String, message: String) {
    if (!verboseLogs) {
      return
    }

    Log.d(tag, String.format("[%s]: %s", Thread.currentThread().name, message))
  }

  internal open fun logError(tag: String, message: String, error: Throwable? = null) {
    if (error == null) {
      Log.e(tag, String.format("[%s]: %s", Thread.currentThread().name, message))
    } else {
      Log.e(tag, String.format("[%s]: %s", Thread.currentThread().name, message), error)
    }
  }
}