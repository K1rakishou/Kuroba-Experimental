package com.github.adamantcheese.chan.core.base

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class SuspendDebouncer(
  scope: CoroutineScope
) {
  private val channel = Channel<Payload>(Channel.UNLIMITED)
  private val counter = AtomicLong(0L)
  private val working = AtomicBoolean(false)
  private val channelJob: Job

  init {
    channelJob = scope.launch {
      var activeJob: Job? = null

      channel.consumeEach { payload ->
        if (counter.get() != payload.id || !isActive || working.get()) {
          return@consumeEach
        }

        activeJob?.cancel()
        activeJob = null

        activeJob = scope.launch {
          delay(payload.timeout)

          if (counter.get() != payload.id || !isActive) {
            return@launch
          }

          if (!working.compareAndSet(false, true)) {
            return@launch
          }

          try {
            payload.func.invoke()
          } finally {
            working.set(false)
          }
        }
      }
    }
  }

  fun post(timeout: Long, func: suspend () -> Unit) {
    require(timeout > 0L) { "Bad timeout!" }

    channel.offer(Payload(counter.incrementAndGet(), timeout, func))
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