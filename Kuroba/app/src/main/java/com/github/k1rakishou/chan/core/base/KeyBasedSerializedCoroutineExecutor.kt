package com.github.k1rakishou.chan.core.base

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ConcurrentHashMap

class KeyBasedSerializedCoroutineExecutor<Key : Any>(
  private val scope: CoroutineScope,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
  private val executors = ConcurrentHashMap<Key, SerializedCoroutineExecutor>()

  fun post(key: Key, func: suspend () -> Unit): Boolean {
    return getOrCreate(key).post(func)
  }

  @Synchronized
  fun cancel(key: Key) {
    executors.remove(key)?.let { executor ->
      executor.stop()
    }
  }

  @Synchronized
  fun cancelAll() {
    executors.values.forEach { executor ->
      executor.stop()
    }

    executors.clear()
  }

  @Synchronized
  private fun getOrCreate(key: Key): SerializedCoroutineExecutor {
    val cachedExecutor = executors[key]
    if (cachedExecutor != null) {
      return cachedExecutor
    }

    val newExecutor = SerializedCoroutineExecutor(scope, dispatcher)
    executors[key] = newExecutor

    return newExecutor
  }

}