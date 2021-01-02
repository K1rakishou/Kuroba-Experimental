package com.github.k1rakishou.chan.core.base

import kotlinx.coroutines.*

class ThrottlingCoroutineExecutor(
  private val scope: CoroutineScope,
  private val mode: Mode = Mode.ThrottleFirst
) {
  @Volatile
  private var func: (suspend () -> Unit)? = null
  @Volatile
  private var job: Job? = null

  fun post(timeout: Long, func: suspend () -> Unit) {
    when (mode) {
      Mode.ThrottleFirst -> throttleFirst(timeout, func)
      Mode.ThrottleLast -> throttleLast(timeout, func)
    }
  }

  fun stop() {
    synchronized(this) {
      this.func = null
      this.job?.cancel()
      this.job = null
    }
  }

  private fun throttleFirst(timeout: Long, func: suspend () -> Unit) {
    val alreadyEnqueued = synchronized(this) { job != null }
    if (alreadyEnqueued) {
      return
    }

    val newJob = scope.launch(start = CoroutineStart.LAZY) {
      this@ThrottlingCoroutineExecutor.func?.invoke()
      this@ThrottlingCoroutineExecutor.func = null

      delay(timeout)

      this@ThrottlingCoroutineExecutor.job = null
    }

    synchronized(this) {
      if (this.job != null) {
        newJob.cancel()
        return@synchronized
      }

      this@ThrottlingCoroutineExecutor.func = func
      this.job = newJob
      this.job!!.start()
    }
  }

  private fun throttleLast(timeout: Long, func: suspend () -> Unit) {
    this.func = func

    val alreadyEnqueued = synchronized(this) { job != null }
    if (alreadyEnqueued) {
      return
    }

    val newJob = scope.launch(start = CoroutineStart.LAZY) {
      delay(timeout)

      this@ThrottlingCoroutineExecutor.func?.invoke()
      this@ThrottlingCoroutineExecutor.func = null
      this@ThrottlingCoroutineExecutor.job = null
    }

    synchronized(this) {
      if (this.job != null) {
        newJob.cancel()
        return@synchronized
      }

      this.job = newJob
      this.job!!.start()
    }
  }

  enum class Mode {
    ThrottleFirst,
    ThrottleLast
  }

}