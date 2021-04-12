package com.github.k1rakishou.chan.core.base

import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

/**
 * Executes all callbacks sequentially using an rendezvous channel. This means that if a callback
 * is currently running all other callbacks are ignored. This executor is really helpful when you
 * need to run a function only once at a time and the function can be executed from multiple places
 * asynchronously.
 * */
@OptIn(ExperimentalCoroutinesApi::class)
class RendezvousCoroutineExecutor(
  private val scope: CoroutineScope,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
  private val channel = Channel<SerializedAction>(
    capacity = Channel.RENDEZVOUS,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  private var job: Job? = null

  init {
    job = scope.launch(context = dispatcher) {
      channel.consumeEach { serializedAction ->
        try {
          serializedAction.action()
        } catch (error: Throwable) {
          Logger.e(TAG, "serializedAction unhandled exception", error)
        }
      }
    }
  }

  fun post(func: suspend () -> Unit) {
    if (channel.isClosedForSend) {
      return
    }

    val serializedAction = SerializedAction(func)
    channel.offer(serializedAction)
  }

  fun stop() {
    job?.cancel()
    job = null
  }

  data class SerializedAction(
    val action: suspend () -> Unit
  )

  companion object {
    private const val TAG = "RendezvousCoroutineExecutor"
  }
}