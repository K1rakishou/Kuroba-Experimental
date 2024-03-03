package com.github.k1rakishou.chan.core.base

import androidx.annotation.GuardedBy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ThrottleFirstCoroutineExecutor(
  private val scope: CoroutineScope,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
  private val executor = ThrottlingCoroutineExecutor(
    scope = scope,
    mode = ThrottlingCoroutineExecutor.Mode.ThrottleFirst,
    dispatcher = dispatcher
  )

  fun post(timeout: Long, func: suspend () -> Unit) {
    executor.post(timeout, func)
  }

  fun stop() {
    executor.stop()
  }

}

class ThrottleLastCoroutineExecutor(
  private val scope: CoroutineScope,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {

  private val executor = ThrottlingCoroutineExecutor(
    scope = scope,
    mode = ThrottlingCoroutineExecutor.Mode.ThrottleLast,
    dispatcher = dispatcher
  )

  fun post(timeout: Long, func: suspend () -> Unit) {
    executor.post(timeout, func)
  }

  fun stop() {
    executor.stop()
  }

}

private class ThrottlingCoroutineExecutor(
  private val scope: CoroutineScope,
  private val mode: Mode = Mode.ThrottleFirst,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
  private val coroutineExceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
    throw RuntimeException(throwable)
  }

  private val lock = Any()

  @Volatile
  @GuardedBy("lock")

  private var storedFunc: (suspend () -> Unit)? = null
  @Volatile
  @GuardedBy("lock")
  private var storedJob: Job? = null

  fun post(timeout: Long, func: suspend () -> Unit) {
    when (mode) {
      Mode.ThrottleFirst -> throttleFirst(timeout, func)
      Mode.ThrottleLast -> throttleLast(timeout, func)
    }
  }

  fun stop() {
    synchronized(lock) {
      storedFunc = null
      storedJob?.cancel()
      storedJob = null
    }
  }

  private fun throttleFirst(timeout: Long, func: suspend () -> Unit) {
    val alreadyEnqueued = synchronized(lock) { storedJob?.isActive == true }
    if (alreadyEnqueued) {
      return
    }

    val newJob = scope.launch(start = CoroutineStart.LAZY, context = dispatcher + coroutineExceptionHandler) {
      try {
        consumeStoredFunc()?.invoke()
        delay(timeout)
      } finally {
        synchronized(lock) { storedJob = null }
      }
    }

    synchronized(lock) {
      if (storedJob?.isActive == true) {
        newJob.cancel()
        return@synchronized
      }

      storedFunc = func
      storedJob = newJob
      newJob.start()
    }
  }

  private fun throttleLast(timeout: Long, func: suspend () -> Unit) {
    synchronized(lock) { storedFunc = func }

    val alreadyEnqueued = synchronized(lock) { storedJob?.isActive == true }
    if (alreadyEnqueued) {
      return
    }

    val newJob = scope.launch(start = CoroutineStart.LAZY, context = dispatcher + coroutineExceptionHandler) {
      try {
        delay(timeout)
        consumeStoredFunc()?.invoke()
      } finally {
        synchronized(lock) { storedJob = null }
      }
    }

    synchronized(lock) {
      if (storedJob?.isActive == true) {
        newJob.cancel()
        return@synchronized
      }

      storedJob = newJob
      newJob.start()
    }
  }

  private fun consumeStoredFunc(): (suspend () -> Unit)? {
    return synchronized(lock) {
      val f = storedFunc
      storedFunc = null
      return@synchronized f
    }
  }

  enum class Mode {
    ThrottleFirst,
    ThrottleLast
  }

}