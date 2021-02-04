package com.github.k1rakishou.model.source.cache

import android.util.LruCache
import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * The whole point of this class is to store original, unparsed post builders in memory which may be
 * used when the user wants to mark a post as own or similar stuff that requires post comment reparsing.
 * */
class ChanPostBuilderCache {
  private val lock = ReentrantReadWriteLock()

  @GuardedBy("lock")
  private val actualCache = LruCache<ChanDescriptor.ThreadDescriptor, MutableList<ChanPostBuilder>>(4)

  fun storePostBuilders(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    postBuildersToStore: List<ChanPostBuilder>
  ) {
    lock.write {
      val oldPostBuilders = actualCache[threadDescriptor]
        ?: mutableListWithCap(128)

      for (postBuilderToStore in postBuildersToStore) {
        val indexOfOld = oldPostBuilders.indexOfFirst { oldPostBuilder ->
          return@indexOfFirst oldPostBuilder.postDescriptor == postBuilderToStore.postDescriptor
        }

        if (indexOfOld < 0) {
          oldPostBuilders += ChanPostBuilder(postBuilderToStore)
        } else {
          oldPostBuilders[indexOfOld] = ChanPostBuilder(postBuilderToStore)
        }
      }

      actualCache.put(threadDescriptor, oldPostBuilders)
    }
  }

  fun isCached(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean {
    return actualCache[threadDescriptor]?.isNotEmpty() ?: false
  }

  fun getPostBuilders(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ChanPostBuilder> {
    return lock.read {
      return@read actualCache[threadDescriptor]
        ?.map { chanPostBuilder -> ChanPostBuilder(chanPostBuilder) }
        ?: emptyList()
    }
  }
}