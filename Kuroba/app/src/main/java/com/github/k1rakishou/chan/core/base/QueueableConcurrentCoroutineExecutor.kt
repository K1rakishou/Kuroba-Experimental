package com.github.k1rakishou.chan.core.base

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class QueueableConcurrentCoroutineExecutor(
  private val maxConcurrency: Int = Runtime.getRuntime().availableProcessors(),
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val scope: CoroutineScope
) {
  private val semaphore = Semaphore(permits = maxConcurrency)

  init {
    require(maxConcurrency > 0) { "Bad maxConcurrency: $maxConcurrency" }
  }

  fun post(action: suspend CoroutineScope.() -> Unit): Job {
    return scope.launch(dispatcher) {
      semaphore.withPermit { action.invoke(this) }
    }
  }

}