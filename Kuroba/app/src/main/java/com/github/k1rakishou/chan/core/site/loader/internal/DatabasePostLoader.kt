package com.github.k1rakishou.chan.core.site.loader.internal

import com.github.k1rakishou.chan.core.site.loader.ChanLoaderResponse
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ReloadPostsFromDatabaseUseCase
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

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

    val originalPost = reloadedPosts.firstOrNull { post -> post.isOP }
    if (originalPost == null) {
      Logger.e(TAG, "loadPosts() Reloaded from the database posts have no OP")
      return null
    }

    fillInReplies(reloadedPosts)

    return ChanLoaderResponse(originalPost.toPostBuilder(null), reloadedPosts).apply {
      preloadPostsInfo()
    }
  }

  suspend fun loadCatalog(threadDescriptors: List<ChanDescriptor.ThreadDescriptor>): ChanLoaderResponse? {
    BackgroundUtils.ensureBackgroundThread()

    val reloadedPosts = reloadPostsFromDatabaseUseCase.reloadCatalogThreads(threadDescriptors)
    if (reloadedPosts.isEmpty()) {
      Logger.d(TAG, "loadCatalog() reloadCatalogThreads() returned empty list")
      return null
    }

    check(reloadedPosts.all { post -> post.isOP }) { "Not all posts are OP!" }

    val originalPost = reloadedPosts.firstOrNull { post -> post.isOP }
    if (originalPost == null) {
      return null
    }

    fillInReplies(reloadedPosts)

    return ChanLoaderResponse(originalPost.toPostBuilder(null), reloadedPosts).apply {
      preloadPostsInfo()
    }
  }

  companion object {
    private const val TAG = "DatabasePostLoader"
  }
}