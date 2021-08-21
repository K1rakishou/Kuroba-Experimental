package com.github.k1rakishou.model.data.options

import java.util.concurrent.TimeUnit

sealed class ChanCacheUpdateOptions {

  fun canUpdate(lastUpdateTimeMs: Long): Boolean {
    when (this) {
      DoNotUpdateCache -> return false
      UpdateCache -> return true
      is UpdateIfCacheIsOlderThan -> {
        return (System.currentTimeMillis() - lastUpdateTimeMs > timePeriodMs)
      }
    }
  }

  object UpdateCache : ChanCacheUpdateOptions() {
    override fun toString(): String {
      return "UpdateCache"
    }
  }

  object DoNotUpdateCache : ChanCacheUpdateOptions() {
    override fun toString(): String {
      return "DoNotUpdateCache"
    }
  }

  /**
   * Only used in the [ChanThreadManager] to figure out whether we can reload posts from the database
   * or need to load fresh posts from the server. After that is replaced by either [UpdateCache] or
   * [DoNotUpdateCache]
   * */
  data class UpdateIfCacheIsOlderThan(val timePeriodMs: Long) : ChanCacheUpdateOptions()

  companion object {
    val DEFAULT_PERIOD = TimeUnit.MINUTES.toMillis(1)
  }
}