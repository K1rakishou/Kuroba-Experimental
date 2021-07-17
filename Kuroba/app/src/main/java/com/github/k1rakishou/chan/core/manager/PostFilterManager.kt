package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.PostFilter
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@DoNotStrip
class PostFilterManager(
  private val verboseLogsEnabled: Boolean,
  private val appScope: CoroutineScope,
  private val chanThreadsCache: ChanThreadsCache
) {
  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val filterStorage = mutableMapWithCap<ChanDescriptor, MutableMap<PostDescriptor, PostFilter>>(16)

  init {
    chanThreadsCache.addChanThreadDeleteEventListener { threadDeleteEvent ->
      if (verboseLogsEnabled) {
        Logger.d(TAG, "chanThreadsCache.chanThreadDeleteEventFlow() " +
          "threadDeleteEvent=${threadDeleteEvent.javaClass.simpleName}")
      }

      onThreadDeleteEventReceived(threadDeleteEvent)
    }
  }

  fun countMatchedFilters(postDescriptors: List<PostDescriptor>): Int {
    return lock.read {
      var counter = 0

      postDescriptors.forEach { postDescriptor ->
        val chanDescriptor = postDescriptor.descriptor

        if (filterStorage[chanDescriptor]?.containsKey(postDescriptor) == true) {
          ++counter
        }
      }

      return@read counter
    }
  }

  fun insert(postDescriptor: PostDescriptor, postFilter: PostFilter) {
    lock.write {
      val chanDescriptor = postDescriptor.descriptor

      filterStorage.putIfNotContains(chanDescriptor, mutableMapWithCap(128))
      filterStorage[chanDescriptor]!![postDescriptor] = postFilter
    }
  }

  fun contains(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val chanDescriptor = postDescriptor.descriptor

      return@read filterStorage[chanDescriptor]?.containsKey(postDescriptor) == true
    }
  }

  fun remove(postDescriptor: PostDescriptor) {
    lock.write {
      val chanDescriptor = postDescriptor.descriptor

      filterStorage[chanDescriptor]?.remove(postDescriptor)
    }
  }

  fun removeMany(postDescriptorList: Collection<PostDescriptor>) {
    lock.write {
      postDescriptorList.forEach { postDescriptor ->
        val chanDescriptor = postDescriptor.descriptor

        filterStorage[chanDescriptor]?.remove(postDescriptor)
      }
    }
  }

  fun removeAllForDescriptor(chanDescriptor: ChanDescriptor) {
    lock.write { filterStorage.remove(chanDescriptor) }
  }

  fun update(postDescriptor: PostDescriptor, updateFunc: (PostFilter) -> Unit) {
    lock.write {
      val chanDescriptor = postDescriptor.descriptor
      filterStorage.putIfNotContains(chanDescriptor, mutableMapWithCap(128))

      val postFilter = filterStorage[chanDescriptor]!!.getOrPut(postDescriptor, { PostFilter() })
      updateFunc(postFilter)
      filterStorage[chanDescriptor]!![postDescriptor] = postFilter
    }
  }

  fun clear() {
    lock.write { filterStorage.clear() }
  }

  fun isEnabled(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val chanDescriptor = postDescriptor.descriptor

      return@read filterStorage[chanDescriptor]?.get(postDescriptor)?.enabled ?: false
    }
  }

  fun getFilterHash(postDescriptor: PostDescriptor): Int {
    return lock.read {
      val chanDescriptor = postDescriptor.descriptor

      return@read filterStorage[chanDescriptor]?.get(postDescriptor)?.hashCode() ?: 0
    }
  }

  fun getFilterHighlightedColor(postDescriptor: PostDescriptor): Int {
    return lock.read {
      val chanDescriptor = postDescriptor.descriptor

      val enabled = filterStorage[chanDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read 0
      }

      return@read filterStorage[chanDescriptor]?.get(postDescriptor)?.filterHighlightedColor ?: 0
    }
  }

  fun getFilterStubOrRemove(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val chanDescriptor = postDescriptor.descriptor

      val enabled = filterStorage[chanDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      if (filterStorage[chanDescriptor]?.get(postDescriptor)?.filterStub == true) {
        return@read true
      }

      if (filterStorage[chanDescriptor]?.get(postDescriptor)?.filterRemove == true) {
        return@read true
      }

      return@read false
    }
  }

  fun getFilterStub(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val chanDescriptor = postDescriptor.descriptor

      val enabled = filterStorage[chanDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[chanDescriptor]?.get(postDescriptor)?.filterStub ?: false
    }
  }

  fun getFilterRemove(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val chanDescriptor = postDescriptor.descriptor

      val enabled = filterStorage[chanDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[chanDescriptor]?.get(postDescriptor)?.filterRemove ?: false
    }
  }

  fun getFilterWatch(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val chanDescriptor = postDescriptor.descriptor

      val enabled = filterStorage[chanDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[chanDescriptor]?.get(postDescriptor)?.filterWatch ?: false
    }
  }

  fun getFilterReplies(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val chanDescriptor = postDescriptor.descriptor

      val enabled = filterStorage[chanDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[chanDescriptor]?.get(postDescriptor)?.filterReplies ?: false
    }
  }

  fun getFilterOnlyOP(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val chanDescriptor = postDescriptor.descriptor

      val enabled = filterStorage[chanDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[chanDescriptor]?.get(postDescriptor)?.filterOnlyOP ?: false
    }
  }

  fun getFilterSaved(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val chanDescriptor = postDescriptor.descriptor

      val enabled = filterStorage[chanDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[chanDescriptor]?.get(postDescriptor)?.filterSaved ?: false
    }
  }

  fun hasFilterParameters(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val chanDescriptor = postDescriptor.descriptor

      val postFilter = filterStorage[chanDescriptor]?.get(postDescriptor)
        ?: return@read false

      return@read postFilter.filterHighlightedColor != 0
        || postFilter.filterStub
        || postFilter.filterSaved
        || postFilter.filterReplies
    }
  }

  private fun onThreadDeleteEventReceived(threadDeleteEvent: ChanThreadsCache.ThreadDeleteEvent) {
    lock.write {
      when (threadDeleteEvent) {
        ChanThreadsCache.ThreadDeleteEvent.ClearAll -> {
          Logger.d(TAG, "onThreadDeleteEventReceived.ClearAll() clearing ${filterStorage.size} threads")
          filterStorage.clear()
        }
        is ChanThreadsCache.ThreadDeleteEvent.RemoveThreads -> {
          var removedThreads = 0

          threadDeleteEvent.threadDescriptors.forEach { threadDescriptor ->
            ++removedThreads
            filterStorage.remove(threadDescriptor)
          }

          Logger.d(TAG, "onThreadDeleteEventReceived.RemoveThreads() removed ${removedThreads} threads")
        }
        is ChanThreadsCache.ThreadDeleteEvent.RemoveThreadPostsExceptOP -> {
          var removedPosts = 0

          threadDeleteEvent.entries.forEach { (threadDescriptor, originalPostDescriptor) ->
            val innerFilterMap = filterStorage[threadDescriptor]
              ?: return@forEach

            innerFilterMap.mutableIteration { mutableIterator, mapEntry ->
              if (mapEntry.key != originalPostDescriptor) {
                ++removedPosts
                mutableIterator.remove()
              }

              return@mutableIteration true
            }
          }

          Logger.d(TAG, "onThreadDeleteEventReceived.RemoveThreadPostsExceptOP() removed ${removedPosts} post filters")
        }
      }
    }
  }

  companion object {
    private const val TAG = "PostFilterManager"
  }

}