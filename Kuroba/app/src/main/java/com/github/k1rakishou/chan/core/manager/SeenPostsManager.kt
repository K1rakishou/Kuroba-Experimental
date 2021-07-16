package com.github.k1rakishou.chan.core.manager

import android.util.LruCache
import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.ui.animation.PostCellAnimator
import com.github.k1rakishou.common.contains
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.SeenPost
import com.github.k1rakishou.model.repository.SeenPostRepository
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
  private val seenPostsRepository: SeenPostRepository
) {
  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val seenPostsMap = LruCache<ChanDescriptor.ThreadDescriptor, MutableSet<SeenPost>>(32)
  @GuardedBy("lock")
  private val seenPostsToPersist = mutableMapOf<ChanDescriptor.ThreadDescriptor, MutableSet<SeenPost>>()

  private val debouncingCoroutineExecutor = DebouncingCoroutineExecutor(appScope)

  @OptIn(ExperimentalTime::class)
  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (!isEnabled()) {
      return
    }

    val alreadyPreloaded = lock.read { seenPostsMap.contains(threadDescriptor) }
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

    lock.write {
      seenPostsToPersist.putIfNotContains(postDescriptor.threadDescriptor(), mutableSetOf())

      val seenPost = SeenPost(
        threadDescriptor = postDescriptor.threadDescriptor(),
        postNo = postDescriptor.postNo,
        insertedAt = DateTime.now()
      )

      seenPostsToPersist[postDescriptor.threadDescriptor()]!!.add(seenPost)
    }

    debouncingCoroutineExecutor.post(DEBOUNCE_TIMEOUT_MS) {
      val threadDescriptor = postDescriptor.descriptor as ChanDescriptor.ThreadDescriptor

      val toPersist = lock.write {
        val toPersist = seenPostsToPersist.remove(postDescriptor.threadDescriptor())
          ?.toMutableSet()

        if (toPersist == null) {
          return@write null
        }

        toPersist.mutableIteration { mutableIterator, seenPost ->
          if (seenPostsMap[threadDescriptor].contains(seenPost)) {
            mutableIterator.remove()
          }

          return@mutableIteration true
        }

        return@write toPersist
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

  companion object {
    private const val TAG = "SeenPostsManager"
    private const val DEBOUNCE_TIMEOUT_MS = 250L
  }
}