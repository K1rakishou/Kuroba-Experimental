package com.github.k1rakishou.chan.core.helper

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.max

class LastViewedPostNoInfoHolder {
  private val lock = ReentrantReadWriteLock()
  private val positions = mutableMapOf<ChanDescriptor.ThreadDescriptor, Long>()

  fun setLastViewedPostNo(threadDescriptor: ChanDescriptor.ThreadDescriptor, newPostNo: Long) {
    require(newPostNo > 0L) { "Bad postNo: $newPostNo" }

    lock.write {
      val prevPostNo = positions[threadDescriptor] ?: 0L
      positions[threadDescriptor] = max(prevPostNo, newPostNo)
    }
  }

  fun getLastViewedPostNoOrZero(threadDescriptor: ChanDescriptor.ThreadDescriptor): Long {
    return lock.read { positions[threadDescriptor] } ?: 0L
  }
}