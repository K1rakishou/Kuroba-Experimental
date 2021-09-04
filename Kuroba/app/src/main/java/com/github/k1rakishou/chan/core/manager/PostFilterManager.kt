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
  private val filterStorage = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, MutableMap<PostDescriptor, PostFilter>>(16)

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
        val threadDescriptor = postDescriptor.threadDescriptor()
        if (filterStorage[threadDescriptor]?.containsKey(postDescriptor) == true) {
          ++counter
        }
      }

      return@read counter
    }
  }

  fun insert(postDescriptor: PostDescriptor, postFilter: PostFilter) {
    lock.write {
      val threadDescriptor = postDescriptor.threadDescriptor()

      filterStorage.putIfNotContains(threadDescriptor, mutableMapWithCap(128))
      filterStorage[threadDescriptor]!![postDescriptor] = postFilter
    }
  }

  fun contains(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val threadDescriptor = postDescriptor.threadDescriptor()
      return@read filterStorage[threadDescriptor]?.containsKey(postDescriptor) == true
    }
  }

  fun remove(postDescriptor: PostDescriptor) {
    lock.write {
      val threadDescriptor = postDescriptor.threadDescriptor()
      filterStorage[threadDescriptor]?.remove(postDescriptor)
    }
  }

  fun removeMany(postDescriptorList: Collection<PostDescriptor>) {
    lock.write {
      postDescriptorList.forEach { postDescriptor ->
        val threadDescriptor = postDescriptor.threadDescriptor()

        filterStorage[threadDescriptor]?.remove(postDescriptor)
      }
    }
  }

  fun removeAllForDescriptor(chanDescriptor: ChanDescriptor) {
    lock.write {
      when (chanDescriptor) {
        is ChanDescriptor.ICatalogDescriptor -> {
          val boardDescriptors = when (chanDescriptor) {
            is ChanDescriptor.CatalogDescriptor -> {
              setOf(chanDescriptor.boardDescriptor)
            }
            is ChanDescriptor.CompositeCatalogDescriptor -> {
              chanDescriptor.catalogDescriptors
                .map { catalogDescriptor -> catalogDescriptor.boardDescriptor }
                .toSet()
            }
          }

          filterStorage.mutableIteration { mutableIterator, entry ->
            if (entry.key.boardDescriptor in boardDescriptors) {
              mutableIterator.remove()
            }

            return@mutableIteration true
          }
        }
        is ChanDescriptor.ThreadDescriptor -> filterStorage.remove(chanDescriptor)
      }
    }
  }

  fun update(postDescriptor: PostDescriptor, updateFunc: (PostFilter) -> Unit) {
    lock.write {
      val threadDescriptor = postDescriptor.threadDescriptor()
      filterStorage.putIfNotContains(threadDescriptor, mutableMapWithCap(128))

      val postFilter = filterStorage[threadDescriptor]!!.getOrPut(postDescriptor, { PostFilter() })
      updateFunc(postFilter)
      filterStorage[threadDescriptor]!![postDescriptor] = postFilter
    }
  }

  fun clear() {
    lock.write { filterStorage.clear() }
  }

  fun isEnabled(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val threadDescriptor = postDescriptor.threadDescriptor()

      return@read filterStorage[threadDescriptor]?.get(postDescriptor)?.enabled ?: false
    }
  }

  fun getManyFilterHashes(postDescriptors: Collection<PostDescriptor>): Map<PostDescriptor, Int> {
    if (postDescriptors.isEmpty()) {
      return emptyMap()
    }

    return lock.read {
      val resultMap = mutableMapWithCap<PostDescriptor, Int>(postDescriptors.size)

      for (postDescriptor in postDescriptors) {
        val filterHash = filterStorage[postDescriptor.threadDescriptor()]
          ?.get(postDescriptor)
          ?.hashCode()
          ?: 0

        resultMap[postDescriptor] = filterHash
      }

      return@read resultMap
    }
  }

  fun getFilterHighlightedColor(postDescriptor: PostDescriptor): Int {
    return lock.read {
      val threadDescriptor = postDescriptor.threadDescriptor()

      val enabled = filterStorage[threadDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read 0
      }

      return@read filterStorage[threadDescriptor]?.get(postDescriptor)?.filterHighlightedColor ?: 0
    }
  }

  fun getManyFilterHighlightedColors(postDescriptors: Collection<PostDescriptor>): Map<PostDescriptor, Int> {
    if (postDescriptors.isEmpty()) {
      return emptyMap()
    }

    return lock.read {
      val resultMap = mutableMapWithCap<PostDescriptor, Int>(postDescriptors.size)

      for (postDescriptor in postDescriptors) {
        val threadDescriptor = postDescriptor.threadDescriptor()
        val enabled = filterStorage[threadDescriptor]?.get(postDescriptor)?.enabled ?: false

        val filterHighlightColor = if (!enabled) {
          null
        } else {
          filterStorage[threadDescriptor]?.get(postDescriptor)?.filterHighlightedColor
        }

        if (filterHighlightColor == null) {
          continue
        }

        resultMap[postDescriptor] = filterHighlightColor
      }

      return@read resultMap
    }
  }

  fun getFilterStubOrRemove(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val threadDescriptor = postDescriptor.threadDescriptor()

      val enabled = filterStorage[threadDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      if (filterStorage[threadDescriptor]?.get(postDescriptor)?.filterStub == true) {
        return@read true
      }

      if (filterStorage[threadDescriptor]?.get(postDescriptor)?.filterRemove == true) {
        return@read true
      }

      return@read false
    }
  }

  fun getFilterStub(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val threadDescriptor = postDescriptor.threadDescriptor()

      val enabled = filterStorage[threadDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[threadDescriptor]?.get(postDescriptor)?.filterStub ?: false
    }
  }

  fun getManyFilterStubs(postDescriptors: Collection<PostDescriptor>): Map<PostDescriptor, Boolean> {
    if (postDescriptors.isEmpty()) {
      return emptyMap()
    }

    return lock.read {
      val resultMap = mutableMapWithCap<PostDescriptor, Boolean>(postDescriptors.size)

      for (postDescriptor in postDescriptors) {
        val threadDescriptor = postDescriptor.threadDescriptor()
        val enabled = filterStorage[threadDescriptor]?.get(postDescriptor)?.enabled ?: false

        val filterStub = if (!enabled) {
          false
        } else {
          filterStorage[threadDescriptor]?.get(postDescriptor)?.filterStub ?: false
        }

        resultMap[postDescriptor] = filterStub
      }

      return@read resultMap
    }
  }

  fun getFilterRemove(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val threadDescriptor = postDescriptor.threadDescriptor()

      val enabled = filterStorage[threadDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[threadDescriptor]?.get(postDescriptor)?.filterRemove ?: false
    }
  }

  fun getFilterWatch(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val threadDescriptor = postDescriptor.threadDescriptor()

      val enabled = filterStorage[threadDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[threadDescriptor]?.get(postDescriptor)?.filterWatch ?: false
    }
  }

  fun getFilterReplies(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val threadDescriptor = postDescriptor.threadDescriptor()

      val enabled = filterStorage[threadDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[threadDescriptor]?.get(postDescriptor)?.filterReplies ?: false
    }
  }

  fun getFilterOnlyOP(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val threadDescriptor = postDescriptor.threadDescriptor()

      val enabled = filterStorage[threadDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[threadDescriptor]?.get(postDescriptor)?.filterOnlyOP ?: false
    }
  }

  fun getFilterSaved(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val threadDescriptor = postDescriptor.threadDescriptor()

      val enabled = filterStorage[threadDescriptor]?.get(postDescriptor)?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[threadDescriptor]?.get(postDescriptor)?.filterSaved ?: false
    }
  }

  fun hasFilterParameters(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val threadDescriptor = postDescriptor.threadDescriptor()

      val postFilter = filterStorage[threadDescriptor]?.get(postDescriptor)
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