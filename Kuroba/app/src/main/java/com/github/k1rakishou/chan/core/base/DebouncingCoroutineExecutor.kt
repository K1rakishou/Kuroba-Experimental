package com.github.k1rakishou.chan.core.base

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Executes a callback only if there are no other attempts to execute another callback over a period
 * of time. If another callback is attempted to be executed, the previous one is discarded. This
 * executor is really helpful when you don't want to execute some callback too often. BUT, if a
 * callback is already being executed and a new one is posted, then the new one is ignored. In other
 * words this executor DOES NOT cancel callbacks that are already in execution progress.
 * */
@Suppress("JoinDeclarationAndAssignment")
@OptIn(ExperimentalCoroutinesApi::class)
class DebouncingCoroutineExecutor(
  scope: CoroutineScope
) {
  private val channel = Channel<Payload>(Channel.UNLIMITED)
  private val counter = AtomicLong(0L)
  private val isProgress = AtomicBoolean(false)
  private val channelJob: Job

  private val coroutineExceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
    throw RuntimeException(throwable)
  }

  init {
    channelJob = scope.launch(coroutineExceptionHandler) {
      var activeJob: Job? = null

      channel.consumeEach { payload ->
        if (counter.get() != payload.id || !isActive || isProgress.get()) {
          return@consumeEach
        }

        activeJob?.cancel()
        activeJob = null

        activeJob = scope.launch {
          delay(payload.timeout)

          if (counter.get() != payload.id || !isActive) {
            return@launch
          }

          if (!isProgress.compareAndSet(false, true)) {
            return@launch
          }

          try {
            payload.func.invoke()
          } finally {
            isProgress.set(false)
          }
        }
      }
    }
  }

  fun post(timeout: Long, func: suspend () -> Unit): Boolean {
    require(timeout > 0L) { "Bad timeout!" }

    if (channel.isClosedForSend) {
      return false
    }

    return channel.trySend(Payload(counter.incrementAndGet(), timeout, func)).isSuccess
  }

  // For tests. Most of the time you don't really need to call this.
  fun stop() {
    channelJob.cancel()
  }

  class Payload(
    val id: Long,
    val timeout: Long,
    val func: suspend () -> Unit
  )
}