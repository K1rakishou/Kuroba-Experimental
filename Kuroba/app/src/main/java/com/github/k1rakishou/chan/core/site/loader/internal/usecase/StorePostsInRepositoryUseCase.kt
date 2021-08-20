package com.github.k1rakishou.chan.core.site.loader.internal.usecase

import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.PostsFromServerData
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.repository.ChanPostRepository

class StorePostsInRepositoryUseCase(
  private val chanPostRepository: ChanPostRepository
) {

  suspend fun storePosts(
    chanDescriptor: ChanDescriptor,
    parsedPosts: List<ChanPost>,
    cacheOptions: ChanCacheOptions,
    cacheUpdateOptions: ChanCacheUpdateOptions,
    postsFromServerData: PostsFromServerData
  ): Int {
    BackgroundUtils.ensureBackgroundThread()
    chanPostRepository.awaitUntilInitialized()

    if (parsedPosts.isEmpty()) {
      Logger.d(TAG, "storePosts(parsedPostsCount=${parsedPosts.size}, chanDescriptor=${chanDescriptor}) -> 0")
      return 0
    }

    return chanPostRepository.insertOrUpdateMany(
      chanDescriptor = chanDescriptor,
      parsedPosts = parsedPosts,
      cacheOptions = cacheOptions,
      cacheUpdateOptions = cacheUpdateOptions,
      postsFromServerData = postsFromServerData
    ).unwrap()
  }

  companion object {
    private const val TAG = "StoreNewPostsUseCase"
  }
}