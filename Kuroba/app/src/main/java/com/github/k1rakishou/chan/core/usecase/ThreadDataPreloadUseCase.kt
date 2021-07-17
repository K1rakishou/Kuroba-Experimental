package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.manager.SeenPostsManager
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.repository.ChanPostRepository
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

class ThreadDataPreloadUseCase(
  private val seenPostsManager: SeenPostsManager,
  private val chanThreadViewableInfoManager: ChanThreadViewableInfoManager,
  private val savedReplyManager: SavedReplyManager,
  private val postHideManager: PostHideManager,
  private val chanPostRepository: ChanPostRepository,
) : ISuspendUseCase<ThreadDataPreloadUseCase.Params, Unit> {

  override suspend fun execute(parameter: Params) {
    return preloadThreadInfo(parameter.threadDescriptor, parameter.isThreadCached)
  }

  private suspend fun preloadThreadInfo(threadDescriptor: ChanDescriptor.ThreadDescriptor, isThreadCached: Boolean) {
    Logger.d(TAG, "preloadThreadInfo($threadDescriptor) begin")

    supervisorScope {
      val jobs = mutableListOf<Deferred<Unit>>()

      jobs += async(Dispatchers.IO) { seenPostsManager.preloadForThread(threadDescriptor) }
      jobs += async(Dispatchers.IO) { chanThreadViewableInfoManager.preloadForThread(threadDescriptor) }
      jobs += async(Dispatchers.IO) { savedReplyManager.preloadForThread(threadDescriptor) }
      jobs += async(Dispatchers.IO) { postHideManager.preloadForThread(threadDescriptor) }

      // Only preload when this thread is not yet in cache
      if (!isThreadCached) {
        jobs += async(Dispatchers.IO) {
          chanPostRepository.preloadForThread(threadDescriptor).unwrap()
        }
      }

      ModularResult.Try { jobs.awaitAll() }
        .peekError { error -> Logger.e(TAG, "preloadThreadInfo() error", error) }
        .ignore()
    }

    Logger.d(TAG, "preloadThreadInfo($threadDescriptor) end")
  }

  data class Params(
    val threadDescriptor: ChanDescriptor.ThreadDescriptor,
    val isThreadCached: Boolean
  )

  companion object {
    private const val TAG = "ThreadDataPreloadUseCase"
  }

}