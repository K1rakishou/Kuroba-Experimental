package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.manager.SeenPostsManager
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.repository.ChanPostRepository
import dagger.Lazy
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ThreadDataPreloader(
  private val seenPostsManager: Lazy<SeenPostsManager>,
  private val chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>,
  private val savedReplyManager: Lazy<SavedReplyManager>,
  private val postHideManager: Lazy<PostHideManager>,
  private val chanPostRepository: Lazy<ChanPostRepository>,
) {

  @OptIn(ExperimentalTime::class)
  suspend fun preloadThreadInfo(threadDescriptor: ChanDescriptor.ThreadDescriptor, isThreadCached: Boolean) {
    Logger.d(TAG, "preloadThreadInfo($threadDescriptor) begin")

    val time = measureTime {
      supervisorScope {
        val jobs = mutableListOf<Deferred<Unit>>()

        jobs += async(Dispatchers.IO) { seenPostsManager.get().preloadForThread(threadDescriptor) }
        jobs += async(Dispatchers.IO) { chanThreadViewableInfoManager.get().preloadForThread(threadDescriptor) }
        jobs += async(Dispatchers.IO) { savedReplyManager.get().preloadForThread(threadDescriptor) }
        jobs += async(Dispatchers.IO) { postHideManager.get().preloadForThread(threadDescriptor) }

        // Only preload when this thread is not yet in cache
        if (!isThreadCached) {
          jobs += async(Dispatchers.IO) {
            chanPostRepository.get().preloadForThread(threadDescriptor).unwrap()
          }
        }

        ModularResult.Try { jobs.awaitAll() }
          .peekError { error -> Logger.e(TAG, "preloadThreadInfo() error", error) }
          .ignore()
      }
    }

    Logger.d(TAG, "preloadThreadInfo($threadDescriptor) end, took $time")
  }

  suspend fun postloadThreadInfo(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    Logger.d(TAG, "postloadThreadInfo($threadDescriptor) begin")
    // no-op
    Logger.d(TAG, "postloadThreadInfo($threadDescriptor) end")
  }

  companion object {
    private const val TAG = "ThreadDataPreloadUseCase"
  }

}