package com.github.adamantcheese.chan.core.base

import com.github.adamantcheese.chan.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

/**
 * Executes all callbacks sequentially using an unlimited channel. This means that there won't
 * be two callbacks running at the same time posted on this executor.
 * */
@OptIn(ExperimentalCoroutinesApi::class)
class SerializedCoroutineExecutor(private val scope: CoroutineScope) {
  private val channel = Channel<SerializedAction>(Channel.UNLIMITED)

  init {
    scope.launch {
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

  data class SerializedAction(
    val action: suspend () -> Unit
  )

  companion object {
    private const val TAG = "SerializedCoroutineExecutor"
  }
}