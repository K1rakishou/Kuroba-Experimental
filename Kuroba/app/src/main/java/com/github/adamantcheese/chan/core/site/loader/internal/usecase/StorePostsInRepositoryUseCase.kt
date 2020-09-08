package com.github.adamantcheese.chan.core.site.loader.internal.usecase

import com.github.adamantcheese.chan.core.mapper.ChanPostMapper
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.repository.ChanPostRepository
import com.google.gson.Gson

class StorePostsInRepositoryUseCase(
  private val gson: Gson,
  private val chanPostRepository: ChanPostRepository
) {

  suspend fun storePosts(
    posts: List<Post>,
    isCatalog: Boolean
  ): List<Long> {
    BackgroundUtils.ensureBackgroundThread()
    Logger.d(TAG, "storePosts(postsCount=${posts.size}, isCatalog=$isCatalog)")

    chanPostRepository.awaitUntilInitialized()

    if (posts.isEmpty()) {
      return emptyList()
    }

    val chanPosts: MutableList<ChanPost> = ArrayList(posts.size)

    for (post in posts) {
      val postDescriptor = PostDescriptor.create(
        post.boardDescriptor,
        post.opNo,
        post.no
      )

      chanPosts.add(ChanPostMapper.fromPost(gson, postDescriptor, post, ArchiveDescriptor.NO_ARCHIVE_ID))
    }

    return chanPostRepository.insertOrUpdateMany(
      chanPosts,
      isCatalog
    ).unwrap()
  }

  companion object {
    private const val TAG = "StoreNewPostsUseCase"
  }
}