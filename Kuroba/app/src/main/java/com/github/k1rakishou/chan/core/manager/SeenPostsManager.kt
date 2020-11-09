package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.SeenPost
import com.github.k1rakishou.model.repository.SeenPostRepository
import kotlinx.coroutines.CoroutineScope
import org.joda.time.DateTime
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
  private val seenPostsMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, MutableSet<SeenPost>>(128)
  @GuardedBy("lock")
  private val alreadyPreloadedSet = hashSetWithCap<ChanDescriptor.ThreadDescriptor>(128)

  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

  @OptIn(ExperimentalTime::class)
  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (!isEnabled()) {
      return
    }

    val alreadyPreloaded = lock.read { alreadyPreloadedSet.contains(threadDescriptor) }
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
      seenPostsMap[threadDescriptor] = seenPosts.toMutableSet()
      alreadyPreloadedSet.add(threadDescriptor)
    }
  }

  fun onPostBind(chanDescriptor: ChanDescriptor, post: ChanPost) {
    if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
      return
    }

    if (!isEnabled()) {
      return
    }

    serializedCoroutineExecutor.post {
      val threadDescriptor = post.postDescriptor.descriptor as ChanDescriptor.ThreadDescriptor

      val seenPost = SeenPost(
        threadDescriptor,
        post.postDescriptor.postNo,
        DateTime.now()
      )

      seenPostsRepository.insert(seenPost)
        .safeUnwrap { error ->
          Logger.e(TAG, "Error while trying to store new seen post with threadDescriptor " +
            "($threadDescriptor), error = ${error.errorMessageOrClassName()}")
          return@post
        }

      seenPostsMap.putIfNotContains(threadDescriptor, mutableSetOf())
      seenPostsMap[threadDescriptor]!!.add(seenPost)
    }
  }

  fun onPostUnbind(chanDescriptor: ChanDescriptor, post: ChanPost) {
    // No-op (maybe something will be added here in the future)
  }

  fun hasAlreadySeenPost(chanDescriptor: ChanDescriptor, post: ChanPost): Boolean {
    if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
      return true
    }

    if (!isEnabled()) {
      return true
    }

    val threadDescriptor = chanDescriptor as ChanDescriptor.ThreadDescriptor
    val postNo = post.postNo()

    val seenPost = lock.read {
      seenPostsMap[threadDescriptor]?.firstOrNull { seenPost -> seenPost.postNo == postNo }
    }

    var hasSeenPost = false

    if (seenPost != null) {
      // We need this time check so that we don't remove the unseen post label right after
      // all loaders have completed loading and updated the post.
      val deltaTime = System.currentTimeMillis() - seenPost.insertedAt.millis
      hasSeenPost = deltaTime > OnDemandContentLoaderManager.MAX_LOADER_LOADING_TIME_MS
    }

    return hasSeenPost
  }

  private fun isEnabled() = ChanSettings.markUnseenPosts.get()

  companion object {
    private const val TAG = "SeenPostsManager"
  }
}