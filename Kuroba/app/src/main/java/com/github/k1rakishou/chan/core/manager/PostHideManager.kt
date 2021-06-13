package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostHide
import com.github.k1rakishou.model.repository.ChanPostHideRepository
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class PostHideManager(
  private val verboseLogsEnabled: Boolean,
  private val appScope: CoroutineScope,
  private val chanPostHideRepository: ChanPostHideRepository
) {
  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val postHideMap = mutableMapOf<PostDescriptor, ChanPostHide>()
  @GuardedBy("lock")
  private val alreadyPreloadedForThreadsSet = hashSetWithCap<ChanDescriptor.ThreadDescriptor>(128)
  @GuardedBy("lock")
  private val alreadyPreloadedForCatalogsSet = hashSetWithCap<ChanDescriptor.CatalogDescriptor>(128)

  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

  fun countPostHides(postDescriptors: List<PostDescriptor>): Int {
    return lock.read {
      var counter = 0

      postDescriptors.forEach { postDescriptor ->
        if (postHideMap.containsKey(postDescriptor)) {
          ++counter
        }
      }

      return@read counter
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val alreadyPreloaded = lock.read { alreadyPreloadedForThreadsSet.contains(threadDescriptor) }
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
    val alreadyPreloaded = lock.read { alreadyPreloadedForCatalogsSet.contains(catalogDescriptor) }
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
        postHideMap[chanPostHide.postDescriptor] = chanPostHide
      }

      alreadyPreloadedForThreadsSet.add(threadDescriptor)
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
        postHideMap[chanPostHide.postDescriptor] = chanPostHide
      }

      alreadyPreloadedForCatalogsSet.add(catalogDescriptor)
    }

    Logger.d(TAG, "chanPostHideRepository.preloadForCatalogInternal() " +
      "preloaded ${chanPostHides.size} post hides")
  }

  fun create(chanPostHide: ChanPostHide) {
    createMany(listOf(chanPostHide))
  }

  fun createMany(chanPostHideList: List<ChanPostHide>) {
    lock.write {
      chanPostHideList.forEach { chanPostHide -> postHideMap[chanPostHide.postDescriptor] = chanPostHide }
    }

    serializedCoroutineExecutor.post {
      chanPostHideRepository.createMany(chanPostHideList)
        .safeUnwrap { error ->
          Logger.e(TAG, "chanPostHideRepository.createMany() error", error)
          lock.write { chanPostHideList.forEach { chanPostHide -> postHideMap.remove(chanPostHide.postDescriptor) } }
          return@post
        }
    }
  }

  suspend fun createManySuspend(chanPostHideList: List<ChanPostHide>) {
    lock.write {
      chanPostHideList.forEach { chanPostHide -> postHideMap[chanPostHide.postDescriptor] = chanPostHide }
    }

    chanPostHideRepository.createMany(chanPostHideList)
      .safeUnwrap { error ->
        Logger.e(TAG, "chanPostHideRepository.createMany() error", error)
        lock.write { chanPostHideList.forEach { chanPostHide -> postHideMap.remove(chanPostHide.postDescriptor) } }
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
      postDescriptorList.mapNotNull { postDescriptor -> postHideMap.remove(postDescriptor) }
    }

    serializedCoroutineExecutor.post {
      chanPostHideRepository.removeMany(postDescriptorList)
        .safeUnwrap { error ->
          Logger.e(TAG, "chanPostHideRepository.removeMany() error", error)
          lock.write { copy.forEach { chanPostHide -> postHideMap[chanPostHide.postDescriptor] = chanPostHide } }
          return@post
        }
    }
  }

  fun getHiddenPostsForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ChanPostHide> {
    val chanPostHideList = mutableListOf<ChanPostHide>()

    lock.read {
      postHideMap.entries.forEach { (postDescriptor, chanPostHide) ->
        if (postDescriptor.threadDescriptor() == threadDescriptor) {
          chanPostHideList += chanPostHide
        }
      }
    }

    return chanPostHideList
  }

  fun getHiddenPostsMap(postDescriptorSet: Set<PostDescriptor>): MutableMap<Long, ChanPostHide> {
    val resultMap = mutableMapOf<Long, ChanPostHide>()

    lock.read {
      postHideMap.entries.forEach { (postDescriptor, chanPostHide) ->
        if (postDescriptor in postDescriptorSet) {
          resultMap[postDescriptor.postNo] = chanPostHide
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

  companion object {
    private const val TAG = "PostHideManager"
    private const val CATALOG_PRELOAD_MAX_COUNT = 1024
  }
}