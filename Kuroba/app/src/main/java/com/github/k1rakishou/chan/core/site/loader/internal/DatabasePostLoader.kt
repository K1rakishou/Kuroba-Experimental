package com.github.k1rakishou.chan.core.site.loader.internal

import com.github.k1rakishou.chan.core.site.loader.ChanLoaderResponse
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ReloadPostsFromDatabaseUseCase
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost

internal class DatabasePostLoader(
  private val reloadPostsFromDatabaseUseCase: ReloadPostsFromDatabaseUseCase
) : AbstractPostLoader() {

  suspend fun loadPosts(chanDescriptor: ChanDescriptor): ChanLoaderResponse? {
    BackgroundUtils.ensureBackgroundThread()

    val reloadedPosts = reloadPostsFromDatabaseUseCase.reloadPosts(chanDescriptor)
    if (reloadedPosts.isEmpty()) {
      Logger.d(TAG, "loadPosts() returned empty list")
      return null
    }

    val originalPost = reloadedPosts.firstOrNull { post -> post is ChanOriginalPost }
    if (originalPost !is ChanOriginalPost) {
      Logger.e(TAG, "loadPosts() Reloaded from the database posts have no OP")
      return null
    }

    return ChanLoaderResponse(originalPost, reloadedPosts)
  }

  companion object {
    private const val TAG = "DatabasePostLoader"
  }
}