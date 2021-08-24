package com.github.k1rakishou.chan.core.helper

import java.util.concurrent.atomic.AtomicBoolean

class OneShotRunnable {
  private val alreadyRun = AtomicBoolean(false)

  suspend fun runIfNotYet(func: suspend () -> Unit) {
    if (!alreadyRun.compareAndSet(false, true)) {
      return
    }

    func()
  }

}