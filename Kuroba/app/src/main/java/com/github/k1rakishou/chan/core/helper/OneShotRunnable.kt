package com.github.k1rakishou.chan.core.helper

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicBoolean

class OneShotRunnable {
  private val _alreadyRun = AtomicBoolean(false)
  private val _runFinished = AtomicBoolean(false)
  private val completableDeferred = CompletableDeferred<Unit>()

  val alreadyRun: Boolean
    get() = _alreadyRun.get() && _runFinished.get()

  suspend fun runIfNotYet(func: suspend () -> Unit) {
    if (!_alreadyRun.compareAndSet(false, true)) {
      completableDeferred.await()
      return
    }

    try {
      func()
    } finally {
      _runFinished.set(true)
      completableDeferred.complete(Unit)
    }
  }

}