package com.github.k1rakishou.chan.core.site.loader.internal.usecase

import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.options.ChanCacheOptions
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.repository.ChanPostRepository

class StorePostsInRepositoryUseCase(
  private val chanPostRepository: ChanPostRepository
) {

  suspend fun storePosts(
    posts: List<ChanPost>,
    cacheOptions: ChanCacheOptions,
    isCatalog: Boolean
  ): Int {
    BackgroundUtils.ensureBackgroundThread()
    chanPostRepository.awaitUntilInitialized()

    if (posts.isEmpty()) {
      Logger.d(TAG, "storePosts(postsCount=${posts.size}, isCatalog=$isCatalog) -> 0")
      return 0
    }

    return chanPostRepository.insertOrUpdateMany(
      posts,
      cacheOptions,
      isCatalog
    ).unwrap()
  }

  companion object {
    private const val TAG = "StoreNewPostsUseCase"
  }
}