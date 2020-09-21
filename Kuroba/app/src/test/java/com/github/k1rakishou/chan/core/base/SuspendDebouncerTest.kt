package com.github.k1rakishou.chan.core.base

import junit.framework.Assert.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SuspendDebouncerTest {

  @Test
  fun `test call the callback once after time out`() {
    runBlockingTest {
      val suspendDebouncer = SuspendDebouncer(this)
      val counter = AtomicInteger(0)

      suspendDebouncer.post(500L) { counter.getAndIncrement() }
      advanceTimeBy(600L)

      assertEquals(1, counter.get())

      suspendDebouncer.stop()
    }
  }

  @Test
  fun `test debouncing`() {
    runBlockingTest {
      val suspendDebouncer = SuspendDebouncer(this)
      val counter = AtomicInteger(0)

      repeat(100) { suspendDebouncer.post(500L) { counter.getAndIncrement() } }
      advanceTimeBy(600L)

      assertEquals(1, counter.get())

      suspendDebouncer.stop()
    }
  }

  @Test
  fun `test debouncing should not call the callback`() {
    runBlockingTest {
      val suspendDebouncer = SuspendDebouncer(this)
      val counter = AtomicInteger(0)

      suspendDebouncer.post(500L) { counter.getAndIncrement() }
      suspendDebouncer.post(500L) { counter.getAndIncrement() }
      suspendDebouncer.post(500L) { counter.getAndIncrement() }
      suspendDebouncer.post(500L) { counter.getAndIncrement() }
      suspendDebouncer.post(500L) { counter.getAndIncrement() }

      assertEquals(0, counter.get())

      suspendDebouncer.stop()
    }
  }

  @Test
  fun `test multiple post updates`() {
    runBlockingTest {
      val suspendDebouncer = SuspendDebouncer(this)
      val counter = AtomicInteger(0)

      repeat(10) {
        suspendDebouncer.post(500L) { counter.getAndIncrement() }
        advanceTimeBy(400L)
      }

      advanceUntilIdle()
      assertEquals(1, counter.get())

      suspendDebouncer.stop()
    }
  }

}