package com.github.adamantcheese.chan.core.base

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class SuspendDebouncer(
  scope: CoroutineScope
) {
  private val channel = Channel<Payload>(Channel.UNLIMITED)
  private val counter = AtomicLong(0L)
  private val channelJob: Job

  init {
    channelJob = scope.launch {
      var activeJob: Job? = null

      channel.consumeEach { payload ->
        if (counter.get() != payload.id) {
          return@consumeEach
        }

        activeJob?.cancel()
        activeJob = null

        activeJob = scope.launch {
          delay(payload.timeout)

          if (counter.get() != payload.id) {
            return@launch
          }

          payload.func.invoke()
        }
      }
    }
  }

  fun post(timeout: Long, func: suspend () -> Unit) {
    require(timeout > 0L) { "Bad timeout!" }

    channel.offer(
      Payload(counter.incrementAndGet(), timeout, func)
    )
  }

  // For tests
  fun stop() {
    channelJob.cancel()
  }

  class Payload(
    val id: Long,
    val timeout: Long,
    val func: suspend () -> Unit
  )
}