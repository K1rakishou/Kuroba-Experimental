package com.github.adamantcheese.chan.core.site.loader.internal

import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderRequestParams
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.GetPostsFromArchiveUseCase
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.ParsePostsUseCase
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.StorePostsInRepositoryUseCase
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.common.errorMessageOrClassName
import com.github.adamantcheese.common.isExceptionImportant
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor

internal class ArchivePostLoader(
  private val parsePostsUseCase: ParsePostsUseCase,
  private val getPostsFromArchiveUseCase: GetPostsFromArchiveUseCase,
  private val storePostsUseCase: StorePostsInRepositoryUseCase,
  private val archivesManager: ArchivesManager
) : AbstractPostLoader() {

  suspend fun updateThreadPostsFromArchiveIfNeeded(
    requestParams: ChanLoaderRequestParams
  ): ModularResult<Unit> {
    return Try {
      if (requestParams.chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
        // We don't support catalog loading from archives
        return@Try
      }

      val postsFromArchive = getPostsFromArchiveUseCase.getPostsFromArchiveIfNecessary(
        emptyList(),
        requestParams.chanDescriptor,
        Utils.getArchiveDescriptor(archivesManager, requestParams, true)
      ).safeUnwrap { error ->
        if (error.isExceptionImportant()) {
          Logger.e(TAG, "tryLoadFromArchivesOrLocalCopyIfPossible() Error while trying to get " +
            "posts from archive", error)
        } else {
          Logger.e(TAG, "tryLoadFromArchivesOrLocalCopyIfPossible() Error while trying to get " +
            "posts from archive: ${error.errorMessageOrClassName()}")
        }

        return@Try
      }

      if (postsFromArchive.isNotEmpty()) {
        val parsedPosts = parsePostsUseCase.parseNewPostsPosts(
          requestParams.chanDescriptor,
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