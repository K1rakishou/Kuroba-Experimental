package com.github.k1rakishou.common.datastructure

import junit.framework.Assert.assertEquals
import org.junit.Test

class RingBufferTest {

  @Test
  fun `test single element push and pop`() {
    val buffer = RingBuffer<Int>(1)

    assertEquals(0, buffer.size())

    buffer.push(1)
    assertEquals(1, buffer.size())
    assertEquals(1, buffer.peek())
    assertEquals(1, buffer.pop())
    assertEquals(0, buffer.size())
  }

  @Test
  fun `test couple elements with maxSize == 1`() {
    val buffer = RingBuffer<Int>(1)

    buffer.push(1)
    assertEquals(1, buffer.size())
    assertEquals(1, buffer.peek())

    buffer.push(2)
    assertEquals(1, buffer.size())
    assertEquals(2, buffer.peek())

    buffer.push(3)
    assertEquals(1, buffer.size())
    assertEquals(3, buffer.peek())

    assertEquals(3, buffer.pop())
    assertEquals(0, buffer.size())
  }

  @Test
  fun `test old elements must be overwritten when capacity is exceeded`() {
    val buffer = RingBuffer<Int>(5)

    buffer.push(1)
    assertEquals(1, buffer.size())
    assertEquals(1, buffer.peek())

    buffer.push(2)
    assertEquals(2, buffer.size())
    assertEquals(2, buffer.peek())

    buffer.push(10)
    assertEquals(3, buffer.size())
    assertEquals(10, buffer.peek())

    buffer.push(44)
    assertEquals(4, buffer.size())
    assertEquals(44, buffer.peek())

    buffer.push(1)
    assertEquals(5, buffer.size())
    assertEquals(1, buffer.peek())

    buffer.push(0)
    assertEquals(5, buffer.size())
    assertEquals(0, buffer.peek())

    assertEquals(0, buffer.pop())
    assertEquals(1, buffer.pop())
    assertEquals(44, buffer.pop())
    assertEquals(10, buffer.pop())
    assertEquals(2, buffer.pop())

    assertEquals(0, buffer.size())
    assertEquals(null, buffer.peek())

    buffer.getArray()
      .forEach { element -> assertEquals(null, element) }
  }

  @Test(expected = IllegalStateException::class)
  fun `test pop on empty buffer not allowed`() {
    val buffer = RingBuffer<Int>(5)

    buffer.push(1)
    buffer.pop()

    buffer.pop()
  }

  @Test(expected = IllegalArgumentException::class)
  fun `test zero or negative max size not allowed`() {
    RingBuffer<Int>(0)
  }

}