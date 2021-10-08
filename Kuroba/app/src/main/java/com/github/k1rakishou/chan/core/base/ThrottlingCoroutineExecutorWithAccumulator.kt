package com.github.k1rakishou.chan.core.base

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ThrottlingCoroutineExecutorWithAccumulator<T>(
  private val scope: CoroutineScope,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
  private val accumulator = hashSetOf<T>()

  private val coroutineExceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
    throw RuntimeException(throwable)
  }

  @Volatile
  private var func: (suspend (Set<T>) -> Unit)? = null
  @Volatile
  private var job: Job? = null
  private val queued = AtomicBoolean(false)

  fun post(item: T, timeout: Long, func: suspend (Set<T>) -> Unit) {
    synchronized(this) {
      this.func = func
      this.accumulator.add(item)
    }

    if (!queued.compareAndSet(false, true)) {
      return
    }

    job = scope.launch(
      context = dispatcher + coroutineExceptionHandler
    ) {
      delay(timeout)

      if (!isActive) {
        return@launch
      }

      val (callback, items) = synchronized(this@ThrottlingCoroutineExecutorWithAccumulator) {
        val itemsCopy = accumulator.toSet()
        accumulator.clear()

        return@synchronized Pair(
          this@ThrottlingCoroutineExecutorWithAccumulator.func,
          itemsCopy
        )
      }

      if (items.isNotEmpty()) {
        callback?.invoke(items)
      }

      synchronized(this@ThrottlingCoroutineExecutorWithAccumulator) {
        this@ThrottlingCoroutineExecutorWithAccumulator.func = null
        this@ThrottlingCoroutineExecutorWithAccumulator.job = null
        queued.set(false)
      }
    }
  }

  fun cancel(item: T) {
    synchronized(this) {
      accumulator.remove(item)
    }
  }

  fun cancelAll() {
    synchronized(this) {
      accumulator.clear()

      this.func = null
      this.job?.cancel()
      this.job = null

      queued.set(false)
    }
  }

}