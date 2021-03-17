package com.github.k1rakishou.chan.core.base

class RecalculatableLazy<T>(val initializer: () -> T) {
  @Volatile
  private var cachedValue: T? = null

  fun isInitialized(): Boolean {
    if (cachedValue == null) {
      return synchronized(this) { cachedValue == null }
    }

    return true
  }

  fun valueOrNull(): T? {
    if (!isInitialized()) {
      return null
    }

    return cachedValue!!
  }

  @Synchronized
  fun resetValue() {
    cachedValue = null
  }

  fun value(): T {
    if (cachedValue == null) {
      return synchronized(this) {
        if (cachedValue == null) {
          cachedValue = initializer()
        }

        return@synchronized cachedValue!!
      }
    }

    return cachedValue!!
  }

}