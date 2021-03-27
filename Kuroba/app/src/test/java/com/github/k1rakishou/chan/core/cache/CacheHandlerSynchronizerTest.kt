package com.github.k1rakishou.chan.core.cache

import org.junit.Test
import java.util.concurrent.Executors

class CacheHandlerSynchronizerTest {
  private val executor = Executors.newFixedThreadPool(100)

  @Test
  fun `test getOrCreate multithreaded`() {
    val cacheHandlerSynchronizer = CacheHandlerSynchronizer()
    val key = "1234.jpg"
    val initial = cacheHandlerSynchronizer.getOrCreate(key)
    val retries = 10000

    (0..retries).map {
      executor.submit {
        val current = cacheHandlerSynchronizer.getOrCreate(key)
        check(current === initial) { "Current: ${current.hashCode()} != initial ${initial.hashCode()}" }
      }
    }.forEach { it.get() }

  }

  @Test
  fun `test lockLocal multithreaded`() {
    val cacheHandlerSynchronizer = CacheHandlerSynchronizer()
    val retries = 10000
    val sharedMutableResource = IntArray(retries)

    (0 until retries).map { key ->
      executor.submit {
        (0 until retries).map {
          cacheHandlerSynchronizer.withLocalLock(key.toString()) {
            sharedMutableResource[key] = sharedMutableResource[key] + 1
          }
        }

      }
    }.forEach { it.get() }

    (0 until retries).forEach { key ->
      val value = sharedMutableResource[key]
      check(value == retries) { "Bad value: $value" }
    }
  }

}