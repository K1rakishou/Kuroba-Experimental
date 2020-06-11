package com.github.adamantcheese.chan.core.site.loader.internal

import com.github.adamantcheese.chan.core.site.loader.ChanLoaderRequestParams
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderResponse
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.ReloadPostsFromDatabaseUseCase
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.DescriptorUtils
import com.github.adamantcheese.chan.utils.Logger

internal class DatabasePostLoader(
  private val reloadPostsFromDatabaseUseCase: ReloadPostsFromDatabaseUseCase
) : AbstractPostLoader() {

  suspend fun loadPosts(requestParams: ChanLoaderRequestParams): ChanLoaderResponse? {
    BackgroundUtils.ensureBackgroundThread()

    val reloadedPosts = reloadPostsFromDatabaseUseCase.reloadPosts(
      DescriptorUtils.getDescriptor(requestParams.loadable),
      requestParams.loadable
    )

    if (reloadedPosts.isEmpty()) {
      Logger.d(TAG, "tryLoadFromDiskCache() returned empty list")
      return null
    }

    val originalPost = reloadedPosts.firstOrNull { post -> post.isOP }
    if (originalPost == null) {
      Logger.e(TAG, "tryLoadFromDiskCache() Reloaded from the database posts have no OP")
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