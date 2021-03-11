package com.github.k1rakishou.common.datastructure

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.Future

class LockFreeGrowableArrayTest {

  @Test
  fun `single-threaded test`() {
    val arrayCache = LockFreeGrowableArray<Int>(1)
    val count = 100

    repeat(count) { value ->
      arrayCache.getOrCreate(
        comparatorFunc = { v -> v == value },
        instantiatorFunc = { value }
      )
    }

    val expected = (0 until count).toList().toTypedArray()
    val actual = arrayCache.asList()
    val actualWithoutNulls = actual.filterNotNull()

    assertEquals(128, actual.size)
    assertEquals(expected.size, actualWithoutNulls.size)
    assertEquals(28, actual.size - actualWithoutNulls.size)

    expected.forEachIndexed { index, _ ->
      assertEquals(expected[index], actual[index])
    }
  }

  @Test
  fun `multi-threaded test`() {
    val arrayCache = LockFreeGrowableArray<Int>(1)
    val count = (128 * 1024) + 1

    val executor = Executors.newFixedThreadPool(32)
    val futures = mutableListOf<Future<*>>()

    repeat(count) { value ->
      futures += executor.submit {
        arrayCache.getOrCreate(
          comparatorFunc = { v -> v == value },
          instantiatorFunc = { value }
        )
      }
    }

    futures.forEach { future -> future.get() }

    val expected = (0 until count).toList().toTypedArray()
    val actual = arrayCache.asList()
    val actualWithoutNulls = actual.filterNotNull()

    assertEquals(262144, actual.size)
    assertEquals(expected.size, actualWithoutNulls.size)
    assertEquals(131071, actual.size - actualWithoutNulls.size)

    expected.forEachIndexed { index, _ ->
      assertTrue(actual.contains(expected[index]))
    }
  }

}