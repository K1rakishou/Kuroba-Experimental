package com.github.k1rakishou.common.datastructure

import androidx.annotation.VisibleForTesting
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LockFreeGrowableArray<T>(
  private val initialSize: Int
) {
  private val growing = AtomicBoolean(false)
  private val working = AtomicBoolean(false)

  private val currentIndex = AtomicInteger(0)

  @Volatile
  private var array: Array<T?> = arrayOfNulls<Any?>(initialSize) as Array<T?>

  fun getOrCreate(comparatorFunc: (T) -> Boolean, instrantiatorFunc: () -> T): T {
    var index = 0

    while (true) {
      waitUntilDoneGrowing()

      val value = array[index]
      if (value != null && comparatorFunc(value)) {
        return value
      }

      ++index

      if (index >= array.size) {
        break
      }
    }

    val newValue = instrantiatorFunc()
    push(newValue)

    return newValue
  }

  private fun push(value: T) {
    while (true) {
      if (working.compareAndSet(false, true)) {
        val nextIndex = currentIndex.getAndIncrement()

        if (nextIndex >= array.size) {
          grow()
        }

        array[nextIndex] = value
        working.set(false)

        return
      }

      Thread.yield()
    }
  }

  private fun grow() {
    if (!growing.compareAndSet(false, true)) {
      waitUntilDoneGrowing()
      return
    }

    val newArray = arrayOfNulls<Any?>(array.size * 2) as Array<T?>
    array.copyInto(newArray)

    array = newArray
    growing.set(false)
  }

  private fun waitUntilDoneGrowing() {
    while (growing.get()) {
      Thread.yield()
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  fun asList(): List<T?> = array.toList()

}