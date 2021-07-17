package com.github.k1rakishou.chan.core.manager

import android.util.LruCache
import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.ui.animation.PostCellAnimator
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.putIfNotContains
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
  private val seenPostsMap = LruCache<ChanDescriptor.ThreadDescriptor, MutableSet<SeenPost>>(32)
  @GuardedBy("lock")
  private val seenPostsToPersist = mutableMapOf<ChanDescriptor.ThreadDescriptor, MutableSet<SeenPost>>()

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
      seenPostsMap.put(threadDescriptor, seenPosts.toMutableSet())
    }
  }

  fun onPostBind(postDescriptor: PostDescriptor) {
    if (postDescriptor.descriptor is ChanDescriptor.CatalogDescriptor) {
      return
    }

    if (!isEnabled()) {
      return
    }

    val needPersist = lock.write {
      val threadDescriptor = postDescriptor.threadDescriptor()

      seenPostsToPersist.putIfNotContains(threadDescriptor, mutableSetOf())

      val seenPost = SeenPost(
        threadDescriptor = threadDescriptor,
        postNo = postDescriptor.postNo,
        insertedAt = DateTime.now()
      )

      if (seenPostsMap[threadDescriptor]?.contains(seenPost) == true) {
        return@write false
      }

      if (seenPostsToPersist[threadDescriptor]?.contains(seenPost) == true) {
        return@write false
      }

      seenPostsToPersist[threadDescriptor]!!.add(seenPost)
      return@write true
    }

    if (!needPersist) {
      return
    }

    debouncingCoroutineExecutor.post(DEBOUNCE_TIMEOUT_MS) {
      val threadDescriptor = postDescriptor.descriptor as ChanDescriptor.ThreadDescriptor

      val toPersist = lock.write {
        return@write seenPostsToPersist.remove(postDescriptor.threadDescriptor())
          ?.toMutableSet()
      }

      if (toPersist == null) {
        return@post
      }

      if (verboseLogsEnabled) {
        Logger.d(TAG, "onPostBind() persisting ${toPersist.size} posts")
      }

      seenPostsRepository.insertMany(threadDescriptor, toPersist)
        .safeUnwrap { error ->
          Logger.e(TAG, "Error while trying to store new seen post with threadDescriptor " +
            "($threadDescriptor), error = ${error.errorMessageOrClassName()}")
          return@post
        }

      lock.write {
        seenPostsMap.putIfNotContains(threadDescriptor, mutableSetOf())
        seenPostsMap[threadDescriptor]!!.addAll(toPersist)
      }
    }
  }

  fun onPostUnbind(postDescriptor: PostDescriptor) {
    // No-op (maybe something will be added here in the future)
  }

  fun hasAlreadySeenPost(chanDescriptor: ChanDescriptor, postDescriptor: PostDescriptor): Boolean {
    if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
      return true
    }

    if (!isEnabled()) {
      return true
    }

    val threadDescriptor = chanDescriptor as ChanDescriptor.ThreadDescriptor

    val seenPost = lock.read {
      // TODO(KurobaEx / @GhostPosts):
      seenPostsMap[threadDescriptor]
        ?.firstOrNull { seenPost -> seenPost.postNo == postDescriptor.postNo }
    }

    var hasSeenPost = false

    if (seenPost != null) {
      // We need this time check so that we don't remove the unseen post label right after
      // all loaders have completed loading and updated the post.
      val deltaTime = System.currentTimeMillis() - seenPost.insertedAt.millis
      hasSeenPost = deltaTime > PostCellAnimator.UnseenPostIndicatorFadeAnimation.ANIMATIONS_TOTAL_TIME
    }

    return hasSeenPost
  }

  private fun isEnabled() = ChanSettings.markUnseenPosts.get()

  private fun onThreadDeleteEventReceived(threadDeleteEvent: ChanThreadsCache.ThreadDeleteEvent) {
    lock.write {
      when (threadDeleteEvent) {
        ChanThreadsCache.ThreadDeleteEvent.ClearAll -> {
          Logger.d(TAG, "onThreadDeleteEventReceived.ClearAll() clearing ${seenPostsMap.size()} threads")
          seenPostsMap.evictAll()
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
            val seenPostsSet = seenPostsMap[threadDescriptor]
              ?: return@forEach

            seenPostsSet.mutableIteration { mutableIterator, seenPost ->
              if (seenPost.postNo != originalPostDescriptor.postNo) {
                ++removedPosts
                mutableIterator.remove()
              }

              return@mutableIteration true
            }
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