package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.linkedMapWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.common.toHashSetBy
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.SeenPost
import com.github.k1rakishou.model.repository.SeenPostRepository
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import kotlinx.coroutines.CoroutineScope
import org.joda.time.DateTime
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@Suppress("EXPERIMENTAL_API_USAGE")
class SeenPostsManager(
  private val appScope: CoroutineScope,
  private val verboseLogsEnabled: Boolean,
  private val chanThreadsCache: ChanThreadsCache,
  private val seenPostsRepository: SeenPostRepository
) {
  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val seenPostsMap = linkedMapWithCap<ChanDescriptor.ThreadDescriptor, MutableMap<PostDescriptor, SeenPost>>(256)
  @GuardedBy("lock")
  private val seenPostsToPersist = mutableMapOf<ChanDescriptor.ThreadDescriptor, MutableMap<PostDescriptor, SeenPost>>()

  private val debouncingCoroutineExecutor = DebouncingCoroutineExecutor(appScope)

  init {
    chanThreadsCache.addChanThreadDeleteEventListener { threadDeleteEvent ->
      Logger.d(TAG, "chanThreadsCache.chanThreadDeleteEventFlow() " +
        "threadDeleteEvent=${threadDeleteEvent.javaClass.simpleName}")
      onThreadDeleteEventReceived(threadDeleteEvent)
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (!isEnabled()) {
      return
    }

    val alreadyPreloaded = lock.read {
      // We consider data preloaded only if it contains more than one entry (for original post) per thread.
      return@read (seenPostsMap[threadDescriptor]?.size ?: 0) > 1
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

  private suspend fun preloadForThreadInternal(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val seenPosts = seenPostsRepository.selectAllByThreadDescriptor(threadDescriptor)
      .safeUnwrap { error ->
        Logger.e(TAG, "Error while trying to select all seen posts by threadDescriptor " +
          "($threadDescriptor), error = ${error.errorMessageOrClassName()}")

        return
      }

    lock.write {
      val resultMap = mutableMapWithCap<PostDescriptor, SeenPost>(seenPosts.size)

      for (seenPost in seenPosts) {
        resultMap[seenPost.postDescriptor] = seenPost
      }

      seenPostsMap.put(threadDescriptor, resultMap)
    }
  }

  fun onPostBind(postDescriptor: PostDescriptor) {
    if (postDescriptor.descriptor is ChanDescriptor.CatalogDescriptor) {
      return
    }

    if (!isEnabled()) {
      return
    }

    val seenPost = SeenPost(
      postDescriptor = postDescriptor,
      insertedAt = DateTime.now()
    )

    createNewSeenPosts(listOf(seenPost))
  }

  private fun createNewSeenPosts(seenPosts: Collection<SeenPost>) {
    if (seenPosts.isEmpty()) {
      return
    }

    val needPersist = lock.write {
      var needPersist = false

      for (seenPost in seenPosts) {
        val postDescriptor = seenPost.postDescriptor
        val threadDescriptor = postDescriptor.threadDescriptor()

        seenPostsToPersist.putIfNotContains(threadDescriptor, mutableMapWithCap(32))

        if (seenPostsMap[threadDescriptor]?.contains(postDescriptor) == true) {
          continue
        }

        if (seenPostsToPersist[threadDescriptor]?.contains(postDescriptor) == true) {
          continue
        }

        seenPostsToPersist[threadDescriptor]!!.put(postDescriptor, seenPost)
        needPersist = true
      }

      return@write needPersist
    }

    if (!needPersist) {
      return
    }

    debouncingCoroutineExecutor.post(DEBOUNCE_TIMEOUT_MS) {
      val threadDescriptors = seenPosts.toHashSetBy { seenPost -> seenPost.postDescriptor.threadDescriptor() }

      val toPersistMap = lock.write {
        val toPersistMap = mutableMapOf<ChanDescriptor.ThreadDescriptor, Set<SeenPost>>()

        for (threadDescriptor in threadDescriptors) {
          val seenPostsMap = seenPostsToPersist.remove(threadDescriptor)
            ?.toMutableMap()

          if (seenPostsMap == null || seenPostsMap.values.isEmpty()) {
            continue
          }

          toPersistMap[threadDescriptor] = seenPostsMap.values.toSet()
        }

        return@write toPersistMap
      }

      if (toPersistMap.isEmpty()) {
        return@post
      }

      toPersistMap.forEach { (threadDescriptor, seenPostSet) ->
        if (verboseLogsEnabled) {
          Logger.d(TAG, "onPostBind() persisting ${seenPostSet.size} posts")
        }

        seenPostsRepository.insertMany(threadDescriptor, seenPostSet)
          .safeUnwrap { error ->
            Logger.e(TAG, "Error while trying to store new seen post with threadDescriptor " +
                "($threadDescriptor), error = ${error.errorMessageOrClassName()}")
            return@post
          }

        lock.write {
          seenPostsMap.putIfNotContains(threadDescriptor, mutableMapWithCap(32))

          val innerMap = seenPostsMap[threadDescriptor]!!
          seenPostSet.forEach { seenPost -> innerMap[seenPost.postDescriptor] = seenPost }
        }
      }
    }
  }

  fun onPostUnbind(postDescriptor: PostDescriptor) {
    // No-op (maybe something will be added here in the future)
  }

  /**
   * When null is returned that means that we are supposed think that all posts were seen (usually
   * this is used in catalogs or when this feature is disabled to not show the post label).
   * When empty map is returned that means that all posts for this thread are unseen.
   * */
  fun getSeenPosts(
    chanDescriptor: ChanDescriptor,
    postDescriptors: Collection<PostDescriptor>
  ): Map<PostDescriptor, SeenPost>? {
    if (chanDescriptor is ChanDescriptor.CatalogDescriptor || !isEnabled()) {
      // When in catalog return empty set which is supposed to mean that all posts have already been
      // seen (to hide the unseen post label)
      return null
    }

    val threadDescriptor = chanDescriptor as ChanDescriptor.ThreadDescriptor

    return lock.read {
      val resultMap = mutableMapWithCap<PostDescriptor, SeenPost>(postDescriptors.size)

      for (postDescriptor in postDescriptors) {
        val seenPost = seenPostsMap[threadDescriptor]?.get(postDescriptor)
        if (seenPost == null) {
          continue
        }

        resultMap[postDescriptor] = seenPost
      }

      return@read resultMap
    }
  }

  private fun isEnabled() = ChanSettings.markUnseenPosts.get()

  private fun onThreadDeleteEventReceived(threadDeleteEvent: ChanThreadsCache.ThreadDeleteEvent) {
    lock.write {
      when (threadDeleteEvent) {
        ChanThreadsCache.ThreadDeleteEvent.ClearAll -> {
          Logger.d(TAG, "onThreadDeleteEventReceived.ClearAll() clearing ${seenPostsMap.size} threads")
          seenPostsMap.clear()
        }
        is ChanThreadsCache.ThreadDeleteEvent.RemoveThreads -> {
          var removedThreads = 0

          threadDeleteEvent.threadDescriptors.forEach { threadDescriptor ->
            ++removedThreads
            seenPostsMap.remove(threadDescriptor)
          }

          Logger.d(TAG, "onThreadDeleteEventReceived.RemoveThreads() removed ${removedThreads} threads")
        }
        is ChanThreadsCache.ThreadDeleteEvent.RemoveThreadPostsExceptOP -> {
          var removedPosts = 0

          threadDeleteEvent.entries.forEach { (threadDescriptor, originalPostDescriptor) ->
            ++removedPosts
            seenPostsMap[threadDescriptor]?.remove(originalPostDescriptor)
          }

          Logger.d(TAG, "onThreadDeleteEventReceived.RemoveThreadPostsExceptOP() removed ${removedPosts} posts")
        }
      }
    }
  }

  companion object {
    private const val TAG = "SeenPostsManager"
    private const val DEBOUNCE_TIMEOUT_MS = 250L
  }
}