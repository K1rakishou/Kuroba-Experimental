package com.github.k1rakishou.chan.core.base

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

class QueueableConcurrentCoroutineExecutor(
  private val maxConcurrency: Int = Runtime.getRuntime().availableProcessors(),
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val scope: CoroutineScope
) {
  private val semaphore = Semaphore(permits = maxConcurrency)

  init {
    require(maxConcurrency > 0) { "Bad maxConcurrency: $maxConcurrency" }
  }

  fun post(action: suspend () -> Unit): Job {
    return scope.launch(dispatcher) {
      semaphore.acquire()

      try {
        action.invoke()
      } finally {
        semaphore.release()
      }
    }
  }

}