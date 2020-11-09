package com.github.k1rakishou.chan.core.base

import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

/**
 * Executes all callbacks sequentially using an unlimited channel. This means that there won't
 * be two callbacks running at the same, they will be queued and executed sequentially instead.
 * */
@OptIn(ExperimentalCoroutinesApi::class)
class SerializedCoroutineExecutor(private val scope: CoroutineScope) {
  private val channel = Channel<SerializedAction>(Channel.UNLIMITED)
  private var job: Job? = null

  init {
    job = scope.launch {
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
      throw IllegalStateException("Channel is closed!")
    }

    val serializedAction = SerializedAction(func)
    channel.offer(serializedAction)
  }

  fun stop() {
    job?.cancel()
    job = null
  }

  class SerializedAction(
    val action: suspend () -> Unit
  )

  companion object {
    private const val TAG = "SerializedCoroutineExecutor"
  }
}