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

open class PostHideManager(
  private val verboseLogsEnabled: Boolean,
  private val appScope: CoroutineScope,
  private val chanPostHideRepository: ChanPostHideRepository,
  private val chanThreadsCache: ChanThreadsCache
) : IPostHideManager {
  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val postHideMap = mutableMapOf<ChanDescriptor, MutableMap<PostDescriptor, ChanPostHide>>()
  @GuardedBy("lock")
  private val alreadyPreloaded = mutableSetOf<ChanDescriptor>()

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

  override fun countPostHides(postDescriptors: List<PostDescriptor>): Int {
    return lock.read {
      var counter = 0

      postDescriptors.forEach { postDescriptor ->
        val chanDescriptor = postDescriptor.descriptor
        val chanPostHide = postHideMap[chanDescriptor]?.get(postDescriptor)

        if (chanPostHide != null && !chanPostHide.manuallyRestored) {
          ++counter
        }
      }

      return@read counter
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    // TODO(KurobaEx): this may not be correct, probably should use the previous solution but also
    //  check whether the post hide is for the OP.
    val alreadyPreloaded = lock.read { !alreadyPreloaded.add(threadDescriptor) }
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
    createOrUpdateMany(listOf(chanPostHide))
  }

  override fun createOrUpdateMany(chanPostHideList: Collection<ChanPostHide>) {
    lock.write {
      chanPostHideList.forEach { chanPostHide ->
        val chanDescriptor = chanPostHide.postDescriptor.descriptor

        postHideMap.putIfNotContains(chanDescriptor, mutableMapWithCap(16))
        postHideMap[chanDescriptor]!!.put(chanPostHide.postDescriptor, chanPostHide)
      }
    }

    serializedCoroutineExecutor.post {
      chanPostHideRepository.createOrUpdateMany(chanPostHideList)
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

    chanPostHideRepository.createOrUpdateMany(chanPostHideList)
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

  fun removeManyChanPostHides(postDescriptorList: Collection<PostDescriptor>) {
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

  fun update(postDescriptor: PostDescriptor, updater: (PostDescriptor, ChanPostHide?) -> ChanPostHide) {
    updateMany(listOf(postDescriptor), updater)
  }

  fun updateMany(
    postDescriptors: Collection<PostDescriptor>,
    updater: (PostDescriptor, ChanPostHide?) -> ChanPostHide
  ) {
    if (postDescriptors.isEmpty()) {
      return
    }

    val (oldPostHides, updatedPostHides) = lock.write {
      val oldPostHides = mutableListOf<ChanPostHide?>()
      val updatedPostHides = mutableListOf<ChanPostHide>()

      postDescriptors.forEach { postDescriptor ->
        val chanDescriptor = postDescriptor.descriptor
        val oldPostHide = postHideMap[chanDescriptor]?.get(postDescriptor)
        val updatedPostHide = updater(postDescriptor, oldPostHide)

        if (oldPostHide == updatedPostHide) {
          return@forEach
        }

        postHideMap[chanDescriptor]?.set(postDescriptor, updatedPostHide)

        oldPostHides += oldPostHide
        updatedPostHides += updatedPostHide
      }

      return@write oldPostHides to updatedPostHides
    }

    if (updatedPostHides.isEmpty()) {
      return
    }

    serializedCoroutineExecutor.post {
      chanPostHideRepository.createOrUpdateMany(updatedPostHides)
        .safeUnwrap { error ->
          Logger.e(TAG, "chanPostHideRepository.removeMany() error", error)

          lock.write {
            for (index in oldPostHides.indices) {
              val oldPostHide = oldPostHides[index]
              if (oldPostHide == null) {
                val updatedPostHide = updatedPostHides[index]
                val chanDescriptor = updatedPostHide.postDescriptor.descriptor

                postHideMap[chanDescriptor]?.remove(updatedPostHide.postDescriptor)
                continue
              }

              val chanDescriptor = oldPostHide.postDescriptor.descriptor

              postHideMap.putIfNotContains(chanDescriptor, mutableMapWithCap(16))
              postHideMap[chanDescriptor]!!.put(oldPostHide.postDescriptor, oldPostHide)
            }
          }

          return@post
        }
    }
  }

  fun getHiddenPostsForCatalog(
    threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>,
    filterManuallyRestored: Boolean = true
  ): List<ChanPostHide> {
    val chanPostHideList = mutableListOf<ChanPostHide>()

    lock.read {
      for (threadDescriptor in threadDescriptors) {
        val innerMap = postHideMap[threadDescriptor]
          ?: continue
        val chanPostHide = innerMap[threadDescriptor.toOriginalPostDescriptor()]
          ?: continue

        if (filterManuallyRestored && chanPostHide.manuallyRestored) {
          continue
        }

        chanPostHideList += chanPostHide
      }
    }

    return chanPostHideList
  }


  fun getHiddenPostsForThread(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    filterManuallyRestored: Boolean = true
  ): List<ChanPostHide> {
    val chanPostHideList = mutableListOf<ChanPostHide>()

    lock.read {
      postHideMap[threadDescriptor]?.values?.forEach { chanPostHide ->
        if (filterManuallyRestored && chanPostHide.manuallyRestored) {
          return@forEach
        }

        chanPostHideList += chanPostHide
      }
    }

    return chanPostHideList
  }

  fun hiddenOrRemoved(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val chanPostHide = postHideMap[postDescriptor.threadDescriptor()]?.get(postDescriptor)
        ?: return@read false

      return@read !chanPostHide.manuallyRestored
    }
  }

  override fun getHiddenPostsMap(postDescriptors: Set<PostDescriptor>): MutableMap<PostDescriptor, ChanPostHide> {
    val resultMap = mutableMapOf<PostDescriptor, ChanPostHide>()

    lock.read {
      postDescriptors.forEach { postDescriptor ->
        val chanDescriptor = postDescriptor.descriptor

        postHideMap[chanDescriptor]?.entries?.forEach { (postDescriptor, chanPostHide) ->
          if (postDescriptor in postDescriptors) {
            resultMap[postDescriptor] = chanPostHide
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
    if (!threadDeleteEvent.evictingOld) {
      return
    }

    lock.write {
      when (threadDeleteEvent) {
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