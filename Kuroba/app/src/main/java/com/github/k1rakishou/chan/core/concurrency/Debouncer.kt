package com.github.k1rakishou.chan.core.concurrency

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

open class Debouncer(
  private val eagerInitialization: Boolean
) {
  private val eagerlyInitialized = AtomicBoolean(!eagerInitialization)
  private val handler: Handler = Handler(Looper.getMainLooper())

  open fun post(delayMs: Long, runnable: Runnable) {
    if (eagerInitialization && !eagerlyInitialized.get()) {
      handler.post {
        if (eagerlyInitialized.compareAndSet(false, true)) {
          runnable.run()
        }
      }

      return
    }

    handler.removeCallbacksAndMessages(null)
    handler.postDelayed(runnable, delayMs)
  }

  fun clear() {
    handler.removeCallbacksAndMessages(null)
  }
}