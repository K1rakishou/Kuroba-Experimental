package com.github.k1rakishou.chan.core.base

import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

/**
 * Executes all callbacks sequentially using an unlimited channel. This means that there won't
 * be two callbacks running at the same, they will be queued and executed sequentially instead.
 * */
@OptIn(ExperimentalCoroutinesApi::class)
class SerializedCoroutineExecutor(
  private val scope: CoroutineScope,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
  private val channel = Channel<SerializedAction>(Channel.UNLIMITED)
  private var job: Job? = null

  init {
    job = scope.launch(dispatcher) {
      channel.consumeEach { serializedAction ->
        try {
          serializedAction.action()
        } catch (error: Throwable) {
          if (error is RuntimeException) {
            throw error
          }

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

  fun cancelChildren() {
    job?.cancelChildren()
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