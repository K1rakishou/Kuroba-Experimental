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
open class PostFilterManager(
  private val verboseLogsEnabled: Boolean,
  private val appScope: CoroutineScope,
  private val chanThreadsCache: ChanThreadsCache
) : IPostFilterManager{
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

  override fun countMatchedFilters(postDescriptors: List<PostDescriptor>): Int {
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

  fun countMatchedPosts(filterDatabaseId: Long): Int {
    return lock.read {
      var postsCount = 0

      for (postFilterMap in filterStorage.values) {
        for ((_, postFilter) in postFilterMap) {
          if (postFilter.ownerFilterId == filterDatabaseId) {
            ++postsCount
          }
        }
      }

      return@read postsCount
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

  fun update(postDescriptor: PostDescriptor, ownerFilterId: Long?, updateFunc: (PostFilter) -> Unit) {
    lock.write {
      val threadDescriptor = postDescriptor.threadDescriptor()
      filterStorage.putIfNotContains(threadDescriptor, mutableMapWithCap(128))

      val postFilter = filterStorage[threadDescriptor]!!.getOrPut(
        key = postDescriptor,
        defaultValue = { PostFilter(ownerFilterId = ownerFilterId) }
      )

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

  fun getPostFilter(postDescriptor: PostDescriptor): PostFilter? {
    return lock.read { filterStorage[postDescriptor.threadDescriptor()]?.get(postDescriptor) }
  }

  override fun getManyPostFilters(postDescriptors: Collection<PostDescriptor>): Map<PostDescriptor, PostFilter> {
    if (postDescriptors.isEmpty()) {
      return emptyMap()
    }

    return lock.read {
      val postFilterMap = mutableMapWithCap<PostDescriptor, PostFilter>(postDescriptors.size)

      for (postDescriptor in postDescriptors) {
        val postFilter = filterStorage[postDescriptor.threadDescriptor()]?.get(postDescriptor)
        if (postFilter != null && postFilter.enabled) {
          postFilterMap[postDescriptor] = postFilter
        }
      }

      return@read postFilterMap
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

  fun getManyFilterHighlights(postDescriptors: Collection<PostDescriptor>): Map<PostDescriptor, PostFilter> {
    if (postDescriptors.isEmpty()) {
      return emptyMap()
    }

    return lock.read {
      val resultMap = mutableMapWithCap<PostDescriptor, PostFilter>(postDescriptors.size)

      for (postDescriptor in postDescriptors) {
        val threadDescriptor = postDescriptor.threadDescriptor()
        val enabled = filterStorage[threadDescriptor]?.get(postDescriptor)?.enabled ?: false

        val postFilterHighlight = if (!enabled) {
          null
        } else {
          filterStorage[threadDescriptor]?.get(postDescriptor)
        }

        if (postFilterHighlight == null) {
          continue
        }

        resultMap[postDescriptor] = postFilterHighlight
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

      if (filterStorage[threadDescriptor]?.get(postDescriptor)?.stub == true) {
        return@read true
      }

      if (filterStorage[threadDescriptor]?.get(postDescriptor)?.remove == true) {
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

      return@read filterStorage[threadDescriptor]?.get(postDescriptor)?.stub ?: false
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
          filterStorage[threadDescriptor]?.get(postDescriptor)?.stub ?: false
        }

        resultMap[postDescriptor] = filterStub
      }

      return@read resultMap
    }
  }

  private fun onThreadDeleteEventReceived(threadDeleteEvent: ChanThreadsCache.ThreadDeleteEvent) {
    lock.write {
      when (threadDeleteEvent) {
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