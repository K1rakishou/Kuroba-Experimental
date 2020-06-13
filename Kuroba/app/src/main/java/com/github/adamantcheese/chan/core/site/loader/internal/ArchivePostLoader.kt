package com.github.adamantcheese.chan.core.site.loader.internal

import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderRequestParams
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.GetPostsFromArchiveUseCase
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.ParsePostsUseCase
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.StorePostsInRepositoryUseCase
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.CancellationException

internal class ArchivePostLoader(
  private val parsePostsUseCase: ParsePostsUseCase,
  private val getPostsFromArchiveUseCase: GetPostsFromArchiveUseCase,
  private val storePostsUseCase: StorePostsInRepositoryUseCase,
  private val archivesManager: ArchivesManager
) : AbstractPostLoader() {

  suspend fun updateThreadPostsFromArchiveIfNeeded(
    descriptor: ChanDescriptor,
    requestParams: ChanLoaderRequestParams
  ): ModularResult<Unit> {
    return Try {
      if (descriptor !is ChanDescriptor.ThreadDescriptor) {
        // We don't support catalog loading from archives
        return@Try
      }

      val postsFromArchive = getPostsFromArchiveUseCase.getPostsFromArchiveIfNecessary(
        emptyList(),
        requestParams.loadable,
        descriptor,
        Utils.getArchiveDescriptor(archivesManager, descriptor, requestParams, true)
      ).safeUnwrap { error ->
        if (error is CancellationException) {
          Logger.e(TAG, "tryLoadFromArchivesOrLocalCopyIfPossible() Error while trying to get " +
            "posts from archive (CANCELED)")
        } else {
          Logger.e(TAG, "tryLoadFromArchivesOrLocalCopyIfPossible() Error while trying to get " +
            "posts from archive", error)
        }

        return@Try
      }

      if (postsFromArchive.isNotEmpty()) {
        val parsedPosts = parsePostsUseCase.parseNewPostsPosts(
          descriptor,
          requestParams.loadable,
          requestParams.chanReader,
          postsFromArchive,
          Int.MAX_VALUE
        )

        storePostsUseCase.storePosts(parsedPosts, false)
      }

      return@Try
    }
  }

  companion object {
    private const val TAG = "ArchivePostLoader"
  }
}