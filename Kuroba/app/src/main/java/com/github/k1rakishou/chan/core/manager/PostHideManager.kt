package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostHide
import com.github.k1rakishou.model.repository.ChanPostHideRepository
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class PostHideManager(
  private val verboseLogsEnabled: Boolean,
  private val appScope: CoroutineScope,
  private val chanPostHideRepository: ChanPostHideRepository,
  private val chanThreadsCache: ChanThreadsCache
) {
  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val postHideMap = mutableMapOf<ChanDescriptor, MutableMap<PostDescriptor, ChanPostHide>>()

  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

  init {
    chanThreadsCache.addChanThreadDeleteEventListener { threadDeleteEvent ->
      if (verboseLogsEnabled) {
        Logger.d(TAG, "chanThreadsCache.chanThreadDeleteEventFlow() " +
          "threadDeleteEvent=${threadDeleteEvent.javaClass.simpleName}")
      }

      onThreadDeleteEventReceived(threadDeleteEvent)
    }
  }

  fun countPostHides(postDescriptors: List<PostDescriptor>): Int {
    return lock.read {
      var counter = 0

      postDescriptors.forEach { postDescriptor ->
        val chanDescriptor = postDescriptor.descriptor

        if (postHideMap[chanDescriptor]?.containsKey(postDescriptor) == true) {
          ++counter
        }
      }

      return@read counter
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val alreadyPreloaded = lock.read {
      // We consider data preloaded only if it contains more than one entry (for original post) per thread.
      return@read (postHideMap[threadDescriptor]?.size ?: 0) > 1
    }

    if (alreadyPreloaded) {
      return
    }

    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForThread($threadDescriptor) begin")
    }

    val time = measureTime { preloadForThreadInternal(threadDescriptor) }

    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForThread($threadDescriptor) end, took $time")
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun preloadForCatalog(catalogDescriptor: ChanDescriptor.CatalogDescriptor) {
    val alreadyPreloaded = lock.read { postHideMap.contains(catalogDescriptor) }
    if (alreadyPreloaded) {
      return
    }

    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForCatalog($catalogDescriptor) begin")
    }

    val time = measureTime { preloadForCatalogInternal(catalogDescriptor, CATALOG_PRELOAD_MAX_COUNT) }

    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForCatalog($catalogDescriptor) end, took $time")
    }
  }

  private suspend fun preloadForThreadInternal(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val chanPostHides = chanPostHideRepository.preloadForThread(threadDescriptor)
      .safeUnwrap { error ->
        Logger.e(TAG, "chanPostHideRepository.preloadForThreadInternal() error", error)
        return
      }

    lock.write {
      chanPostHides.forEach { chanPostHide ->
        val chanDescriptor = chanPostHide.postDescriptor.descriptor

        postHideMap.putIfNotContains(chanDescriptor, mutableMapWithCap(16))
        postHideMap[chanDescriptor]!!.put(chanPostHide.postDescriptor, chanPostHide)
      }
    }

    Logger.d(TAG, "chanPostHideRepository.preloadForThreadInternal() " +
      "preloaded ${chanPostHides.size} post hides")
  }

  private suspend fun preloadForCatalogInternal(
    catalogDescriptor: ChanDescriptor.CatalogDescriptor,
    count: Int
  ) {
    val chanPostHides = chanPostHideRepository.preloadForCatalog(catalogDescriptor, count)
      .safeUnwrap { error ->
        Logger.e(TAG, "chanPostHideRepository.preloadForCatalogInternal() error", error)
        return
      }

    lock.write {
      chanPostHides.forEach { chanPostHide ->
        val chanDescriptor = chanPostHide.postDescriptor.descriptor

        postHideMap.putIfNotContains(chanDescriptor, mutableMapWithCap(16))
        postHideMap[chanDescriptor]!!.put(chanPostHide.postDescriptor, chanPostHide)
      }
    }

    Logger.d(TAG, "chanPostHideRepository.preloadForCatalogInternal() " +
      "preloaded ${chanPostHides.size} post hides")
  }

  fun create(chanPostHide: ChanPostHide) {
    createMany(listOf(chanPostHide))
  }

  fun createMany(chanPostHideList: List<ChanPostHide>) {
    lock.write {
      chanPostHideList.forEach { chanPostHide ->
        val chanDescriptor = chanPostHide.postDescriptor.descriptor

        postHideMap.putIfNotContains(chanDescriptor, mutableMapWithCap(16))
        postHideMap[chanDescriptor]!!.put(chanPostHide.postDescriptor, chanPostHide)
      }
    }

    serializedCoroutineExecutor.post {
      chanPostHideRepository.createMany(chanPostHideList)
        .safeUnwrap { error ->
          Logger.e(TAG, "chanPostHideRepository.createMany() error", error)

          lock.write {
            chanPostHideList.forEach { chanPostHide ->
              val chanDescriptor = chanPostHide.postDescriptor.descriptor
              postHideMap[chanDescriptor]?.remove(chanPostHide.postDescriptor)
            }
          }
          return@post
        }
    }
  }

  suspend fun createManySuspend(chanPostHideList: List<ChanPostHide>) {
    lock.write {
      chanPostHideList.forEach { chanPostHide ->
        val chanDescriptor = chanPostHide.postDescriptor.descriptor

        postHideMap.putIfNotContains(chanDescriptor, mutableMapWithCap(16))
        postHideMap[chanDescriptor]!!.put(chanPostHide.postDescriptor, chanPostHide)
      }
    }

    chanPostHideRepository.createMany(chanPostHideList)
      .safeUnwrap { error ->
        Logger.e(TAG, "chanPostHideRepository.createMany() error", error)

        lock.write {
          chanPostHideList.forEach { chanPostHide ->
            val chanDescriptor = chanPostHide.postDescriptor.descriptor
            postHideMap[chanDescriptor]?.remove(chanPostHide.postDescriptor)
          }
        }

        return
      }
  }

  suspend fun getTotalCount(): ModularResult<Int> {
    return chanPostHideRepository.getTotalCount()
  }

  fun remove(postDescriptor: PostDescriptor) {
    removeManyChanPostHides(listOf(postDescriptor))
  }

  fun removeManyChanPostHides(postDescriptorList: List<PostDescriptor>) {
    val copy = lock.write {
      postDescriptorList.mapNotNull { postDescriptor ->
        val chanDescriptor = postDescriptor.descriptor
        return@mapNotNull postHideMap[chanDescriptor]?.remove(postDescriptor)
      }
    }

    serializedCoroutineExecutor.post {
      chanPostHideRepository.removeMany(postDescriptorList)
        .safeUnwrap { error ->
          Logger.e(TAG, "chanPostHideRepository.removeMany() error", error)

          lock.write {
            copy.forEach { chanPostHide ->
              val chanDescriptor = chanPostHide.postDescriptor.descriptor

              postHideMap.putIfNotContains(chanDescriptor, mutableMapWithCap(16))
              postHideMap[chanDescriptor]!!.put(chanPostHide.postDescriptor, chanPostHide)
            }
          }
          return@post
        }
    }
  }

  fun getHiddenPostsForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ChanPostHide> {
    val chanPostHideList = mutableListOf<ChanPostHide>()

    lock.read {
      postHideMap[threadDescriptor]?.values?.forEach { chanPostHide ->
        chanPostHideList += chanPostHide
      }
    }

    return chanPostHideList
  }

  fun getHiddenPostsMap(postDescriptorSet: Set<PostDescriptor>): MutableMap<Long, ChanPostHide> {
    val resultMap = mutableMapOf<Long, ChanPostHide>()

    lock.read {
      postDescriptorSet.forEach { postDescriptor ->
        val chanDescriptor = postDescriptor.descriptor

        postHideMap[chanDescriptor]?.entries?.forEach { (postDescriptor, chanPostHide) ->
          if (postDescriptor in postDescriptorSet) {
            resultMap[postDescriptor.postNo] = chanPostHide
          }
        }
      }
    }

    return resultMap
  }

  fun clearAllPostHides() {
    lock.write { postHideMap.clear() }

    serializedCoroutineExecutor.post {
      chanPostHideRepository.deleteAll()
        .peekError { error -> Logger.e(TAG, "chanPostHideRepository.deleteAll() error", error) }
        .ignore()
    }
  }

  private fun onThreadDeleteEventReceived(threadDeleteEvent: ChanThreadsCache.ThreadDeleteEvent) {
    lock.write {
      when (threadDeleteEvent) {
        ChanThreadsCache.ThreadDeleteEvent.ClearAll -> {
          Logger.d(TAG, "onThreadDeleteEventReceived.ClearAll() clearing ${postHideMap.size} threads")
          postHideMap.clear()
        }
        is ChanThreadsCache.ThreadDeleteEvent.RemoveThreads -> {
          var removedThreads = 0

          threadDeleteEvent.threadDescriptors.forEach { threadDescriptor ->
            ++removedThreads
            postHideMap.remove(threadDescriptor)
          }

          Logger.d(TAG, "onThreadDeleteEventReceived.RemoveThreads() removed ${removedThreads} threads")
        }
        is ChanThreadsCache.ThreadDeleteEvent.RemoveThreadPostsExceptOP -> {
          var removedPosts = 0

          threadDeleteEvent.entries.forEach { (threadDescriptor, originalPostDescriptor) ->
            val innerPostHideMap = postHideMap[threadDescriptor]
              ?: return@forEach

            innerPostHideMap.mutableIteration { mutableIterator, mapEntry ->
              if (mapEntry.key != originalPostDescriptor) {
                ++removedPosts
                mutableIterator.remove()
              }

              return@mutableIteration true
            }
          }

          Logger.d(TAG, "onThreadDeleteEventReceived.RemoveThreadPostsExceptOP() removed ${removedPosts} post hides")
        }
      }
    }
  }

  companion object {
    private const val TAG = "PostHideManager"
    private const val CATALOG_PRELOAD_MAX_COUNT = 1024
  }
}