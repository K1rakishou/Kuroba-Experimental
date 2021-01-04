package com.github.k1rakishou.common.datastructure

import androidx.annotation.VisibleForTesting

class RingBuffer<T>(
  private val maxSize: Int
) {
  private val array: Array<T?>

  private var tailIndex: Int = 0
  private var headIndex: Int = 0

  init {
    require(maxSize > 0) { "Bad maxSize: $maxSize" }

    array = Array<Any?>(maxSize) { null } as Array<T?>
  }

  fun push(element: T) {
    array[++headIndex % maxSize] = element

    if ((headIndex - tailIndex) > maxSize) {
      ++tailIndex
    }
  }

  fun peek(): T? {
    return array[headIndex % maxSize]
  }

  fun size(): Int {
    return (headIndex - tailIndex).coerceAtLeast(0)
  }

  fun isEmpty(): Boolean {
    return size() <= 0
  }

  fun pop(): T {
    if (tailIndex >= headIndex) {
      throw IllegalStateException("Attempt to remove head element on an empty RingBuffer")
    }

    val element = requireNotNull(array[headIndex % maxSize]) {
      "Element is null! tailIndex=$tailIndex, headIndex=$headIndex, maxSize=$maxSize"
    }

    array[headIndex-- % maxSize] = null
    return element
  }

  fun clear() {
    tailIndex = 0
    headIndex = 0

    array.fill(null)
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  fun getArray(): Array<T?> = array

}