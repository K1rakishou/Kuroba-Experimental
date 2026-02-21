package com.github.k1rakishou.chan.core.concurrency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Executes a callback only if there are no other attempts to execute another callback over a period
 * of time. If another callback is attempted to be executed, the previous one is discarded. This
 * executor is really helpful when you don't want to execute some callback too often.
 * */
class DebouncingCoroutineExecutor(
  private val scope: CoroutineScope
) {
  private var _job: Job? = null

  fun post(timeout: Long, func: suspend () -> Unit) {
    require(timeout > 0L) { "Bad timeout!" }

    _job?.cancel()
    _job = scope.launch {
      delay(timeout)
      func()
    }
  }

  // For tests. Most of the time you don't really need to call this.
  fun stop() {
    _job?.cancel()
  }
}