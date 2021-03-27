package com.github.k1rakishou.chan.core.cache

import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import java.lang.ref.WeakReference

/**
 * A synchronizer class for CacheHandler that allows synchronization on a value of the key (by it's value).
 * This is very useful for CacheHandler since we won't to lock disk access per file separately not
 * for the whole disk at a time. This should drastically improve CacheHandler's performance when
 * many different threads access different files. In the previous implementation we would lock
 * access to disk globally every time a thread is doing something with a file which could slow down
 * everything when there were a lot of disk access from multiple threads
 * (Album with 5 columns + prefetch + high-res thumbnails + huge cache size (1GB+).)
 * */
class CacheHandlerSynchronizer {
  @GuardedBy("this")
  private val synchronizerMap = HashMap<String, WeakReference<Any>>()

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  fun getOrCreate(key: String): Any {
    return synchronized(this) {
      var value = synchronizerMap[key]?.get()
      if (value == null) {
        value = Any()
        synchronizerMap[key] = WeakReference(value)
      }

      return@synchronized value
    }
  }

  fun <T : Any?> withLocalLock(key: String, func: () -> T): T {
    return synchronized(getOrCreate(key)) { func() }
  }

  fun <T : Any?> withGlobalLock(func: () -> T): T {
    return synchronized(this) { func() }
  }

}