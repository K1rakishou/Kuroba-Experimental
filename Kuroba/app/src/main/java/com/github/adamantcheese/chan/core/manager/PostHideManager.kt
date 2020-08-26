package com.github.adamantcheese.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.core.base.SerializedCoroutineExecutor
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPostHide
import com.github.adamantcheese.model.repository.ChanPostHideRepository
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
  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

  @OptIn(ExperimentalTime::class)
  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForThread($threadDescriptor) begin")
    }

    val time = measureTime { preloadForThreadInternal(threadDescriptor) }

    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForThread($threadDescriptor) end, took $time")
    }
  }

  private suspend fun preloadForThreadInternal(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val chanPostHides = chanPostHideRepository.preloadForThread(threadDescriptor)
      .safeUnwrap { error ->
        Logger.e(TAG, "chanPostHideRepository.preloadForThread() error", error)
        return
      }

    lock.write {
      chanPostHides.forEach { chanPostHide ->
        postHideMap[chanPostHide.postDescriptor] = chanPostHide
      }
    }
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
  }
}