package com.github.k1rakishou.chan.core.helper.synchronizers

import com.github.k1rakishou.chan.core.synchronizers.SuspendKeySynchronizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test

class SuspendFullKeySynchronizerTest {

  @Test(timeout = 10_000L)
  fun `should not deadlock when locking with different keys`() = runTest {
    var value = 0
    val cacheHandlerSynchronizer = SuspendKeySynchronizer<String>()

    cacheHandlerSynchronizer.withLocalLock("1") {
      cacheHandlerSynchronizer.withLocalLock("2") {
        cacheHandlerSynchronizer.withLocalLock("3") {
          cacheHandlerSynchronizer.withLocalLock("4") {
            ++value
          }
        }
      }
    }

    Assert.assertEquals(1, value)
    Assert.assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

  @Test(timeout = 10_000L)
  fun `should not deadlock when nested locking with the same key`() = runTest {
    var value = 0
    val cacheHandlerSynchronizer = SuspendKeySynchronizer<String>()

    cacheHandlerSynchronizer.withLocalLock("1") {
      cacheHandlerSynchronizer.withLocalLock("1") {
        ++value
      }
    }

    Assert.assertEquals(1, value)
    Assert.assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

  @Test(timeout = 10_000L)
  fun `should not deadlock when nested locking with global lock`() = runTest {
    var value = 0
    val cacheHandlerSynchronizer = SuspendKeySynchronizer<String>()

    cacheHandlerSynchronizer.withGlobalLock {
      cacheHandlerSynchronizer.withGlobalLock {
        ++value
      }
    }

    Assert.assertEquals(1, value)
    Assert.assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

  @Test(timeout = 10_000L)
  fun `should not deadlock when mixing local and global locks`() = runTest {
    var value = 0
    val cacheHandlerSynchronizer = SuspendKeySynchronizer<String>()

    cacheHandlerSynchronizer.withGlobalLock {
      cacheHandlerSynchronizer.withLocalLock("1") {
        cacheHandlerSynchronizer.withGlobalLock {
          cacheHandlerSynchronizer.withLocalLock("1") {
            cacheHandlerSynchronizer.withGlobalLock {
              cacheHandlerSynchronizer.withLocalLock("2") {
                cacheHandlerSynchronizer.withLocalLock("3") {
                  ++value
                }
              }
            }
          }
        }
      }
    }

    Assert.assertEquals(1, value)
    Assert.assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

  // Test fails. The problem is if a local lock is already locked by the time a global lock gets locked, code within
  // local lock's lock/unlock method calls can modify a variable accessed by the global lock. This is bad, in general,
  // but it kinda works for file cache.
//  @Test(timeout = 10_000L)
  fun `should not allow locking a local lock when a global lock is already locked`() = runTest {
    var value = 0
    val startTime = System.currentTimeMillis()
    val cacheHandlerSynchronizer = SuspendKeySynchronizer<String>()

    coroutineScope {
      launch(Dispatchers.IO) {
        cacheHandlerSynchronizer.withGlobalLock {
          while (System.currentTimeMillis() - startTime < 10) {
            Assert.assertEquals(value, 0)
            Assert.assertFalse(cacheHandlerSynchronizer.isLocalLockLocked("1"))
          }
        }
      }

      launch(Dispatchers.IO) {
        cacheHandlerSynchronizer.withLocalLock("1") {
          ++value
        }
      }
    }

    Assert.assertEquals(value, 1)
  }

  @Test(timeout = 10_000L)
  fun `concurrent access from multiple threads only local`() = runTest {
    val values = IntArray(50) { 0 }
    val cacheHandlerSynchronizer = SuspendKeySynchronizer<String>()

    (0 until 50).map { id ->
      async(Dispatchers.IO) {
        repeat(100) {
          cacheHandlerSynchronizer.withLocalLock(key = id.toString()) {
            values[id] = values[id] + 1
          }
        }
      }
    }.awaitAll()

    Assert.assertEquals(50 * 100, values.sum())
    Assert.assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

  @Test(timeout = 10_000L)
  fun `concurrent access from multiple threads only global`() = runTest {
    var value = 0
    val cacheHandlerSynchronizer = SuspendKeySynchronizer<String>()

    (0 until 50).map { id ->
      async(Dispatchers.IO) {
        repeat(100) {
          cacheHandlerSynchronizer.withGlobalLock {
            ++value
          }
        }
      }
    }.awaitAll()

    Assert.assertEquals(50 * 100, value)
    Assert.assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

  @Test(timeout = 10_000L)
  fun `concurrent access from multiple threads mixed 1`() = runTest {
    var value = 0
    val cacheHandlerSynchronizer = SuspendKeySynchronizer<String>()

    (0 until 50).map { id ->
      async(Dispatchers.IO) {
        repeat(100) {
          cacheHandlerSynchronizer.withGlobalLock {
            cacheHandlerSynchronizer.withLocalLock(key = id.toString()) {
              ++value
            }
          }
        }
      }
    }.awaitAll()

    Assert.assertEquals(50 * 100, value)
    Assert.assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

  @Test(timeout = 10_000L)
  fun `concurrent access from multiple threads mixed 2`() = runTest {
    var value = 0
    val cacheHandlerSynchronizer = SuspendKeySynchronizer<String>()

    (0 until 50).map { id ->
      async(Dispatchers.IO) {
        repeat(100) {
          cacheHandlerSynchronizer.withLocalLock(key = id.toString()) {
            cacheHandlerSynchronizer.withGlobalLock {
              ++value
            }
          }
        }
      }
    }.awaitAll()

    Assert.assertEquals(50 * 100, value)
    Assert.assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }


}
