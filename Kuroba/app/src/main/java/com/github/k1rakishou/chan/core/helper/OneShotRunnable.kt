package com.github.k1rakishou.chan.core.helper

import java.util.concurrent.atomic.AtomicBoolean

class OneShotRunnable {
  private val _alreadyRun = AtomicBoolean(false)
  val alreadyRun: Boolean
    get() = _alreadyRun.get()

  suspend fun runIfNotYet(func: suspend () -> Unit) {
    if (!_alreadyRun.compareAndSet(false, true)) {
      return
    }

    func()
  }

}