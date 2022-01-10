package com.github.k1rakishou.chan.core.base

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LazySuspend<T>(val initializer: suspend () -> T) {
  @Volatile
  private var cachedValue: T? = null
  private val mutex = Mutex()

  fun isInitialized(): Boolean = cachedValue != null

  fun valueOrNull(): T? {
    if (!isInitialized()) {
      return null
    }

    return cachedValue!!
  }

  suspend fun update(newValue: T) {
    mutex.withLock { cachedValue = newValue }
  }

  suspend fun value(): T {
    if (cachedValue == null) {
       return mutex.withLock {
        if (cachedValue == null) {
          cachedValue = initializer()
        }

        return@withLock cachedValue!!
      }
    }

    return cachedValue!!
  }

}