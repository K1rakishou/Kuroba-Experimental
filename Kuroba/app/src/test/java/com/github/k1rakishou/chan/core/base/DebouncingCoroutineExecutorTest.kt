package com.github.k1rakishou.chan.core.base

import junit.framework.Assert.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class DebouncingCoroutineExecutorTest {

  @Test
  fun `test call the callback once after time out`() {
    runBlockingTest {
      val suspendDebouncer = DebouncingCoroutineExecutor(this)
      val counter = AtomicInteger(0)

      suspendDebouncer.post(500L) { counter.getAndIncrement() }
      testScheduler.apply { advanceTimeBy(600L); runCurrent() }

      assertEquals(1, counter.get())

      suspendDebouncer.stop()
    }
  }

  @Test
  fun `test debouncing`() {
    runBlockingTest {
      val suspendDebouncer = DebouncingCoroutineExecutor(this)
      val counter = AtomicInteger(0)

      repeat(100) { suspendDebouncer.post(500L) { counter.getAndIncrement() } }
      testScheduler.apply { advanceTimeBy(600L); runCurrent() }

      assertEquals(1, counter.get())

      suspendDebouncer.stop()
    }
  }

  @Test
  fun `test debouncing should not call the callback`() {
    runBlockingTest {
      val suspendDebouncer = DebouncingCoroutineExecutor(this)
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
      val suspendDebouncer = DebouncingCoroutineExecutor(this)
      val counter = AtomicInteger(0)

      repeat(10) {
        suspendDebouncer.post(500L) { counter.getAndIncrement() }
        testScheduler.apply { advanceTimeBy(400L); runCurrent() }
      }

      advanceUntilIdle()
      assertEquals(1, counter.get())

      suspendDebouncer.stop()
    }
  }

}