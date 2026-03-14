package com.github.k1rakishou.chan.utils

import android.os.Handler
import android.os.Looper
import androidx.core.os.HandlerCompat
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.common.AndroidUtils.appContext
import com.github.k1rakishou.common.callStack
import com.github.k1rakishou.core_logger.Logger

object BackgroundUtils {
  private val mainHandler = Handler(Looper.getMainLooper())

  fun isInForeground(): Boolean {
    return (appContext as Chan).applicationInForeground
  }

  /**
   * Causes the runnable to be added to the message queue. The runnable will
   * be run on the ui thread.
   */
  fun runOnMainThread(runnable: Runnable) {
    mainHandler.post(runnable)
  }

  fun runOnMainThread(runnable: Runnable, delay: Long) {
    mainHandler.postDelayed(runnable, delay)
  }

  fun runOnMainThreadWithToken(delay: Long, token: Any?, runnable: Runnable) {
    HandlerCompat.postDelayed(mainHandler, runnable, token, delay)
  }

  fun cancelAllByToken(token: Any?) {
    mainHandler.removeCallbacksAndMessages(token)
  }

  val isMainThread: Boolean
    get() = Thread.currentThread() === Looper.getMainLooper().getThread()

  @JvmStatic
  fun ensureMainThread() {
    if (isMainThread) {
      return
    }

    val kurobaSettings = appDependencies().kurobaSettings
    if (kurobaSettings.application.crashOnSafeThrow.readBlocking()) {
      error("Cannot be executed on a background thread!")
    }

    Logger.e("BackgroundUtils", "ensureMainThread() expected main thread but got " + Thread.currentThread().getName())
    Logger.e("BackgroundUtils", Thread.currentThread().callStack())
  }

  fun ensureBackgroundThread() {
    if (!isMainThread) {
      return
    }

    val kurobaSettings = appDependencies().kurobaSettings
    if (kurobaSettings.application.crashOnSafeThrow.readBlocking()) {
      error("Cannot be executed on the main thread!")
    }

    Logger.e("BackgroundUtils", "ensureBackgroundThread() expected background thread but got main")
    Logger.e("BackgroundUtils", Thread.currentThread().callStack())
  }
}
