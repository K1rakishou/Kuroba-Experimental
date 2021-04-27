package com.github.k1rakishou.chan.core.site.loader.internal

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.site.loader.ChanLoaderResponse
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ReloadPostsFromDatabaseUseCase
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository

internal class DatabasePostLoader(
  private val reloadPostsFromDatabaseUseCase: ReloadPostsFromDatabaseUseCase,
  private val chanCatalogSnapshotRepository: ChanCatalogSnapshotRepository
) : AbstractPostLoader() {

  suspend fun loadPosts(chanDescriptor: ChanDescriptor): ChanLoaderResponse? {
    if (!ChanSettings.databasePostCachingEnabled.get()) {
      return null
    }

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

  suspend fun loadCatalog(catalogDescriptor: ChanDescriptor.CatalogDescriptor): ChanLoaderResponse? {
    if (!ChanSettings.databasePostCachingEnabled.get()) {
      return null
    }

    BackgroundUtils.ensureBackgroundThread()

    val currentCatalogSnapshot = chanCatalogSnapshotRepository.getCatalogSnapshot(catalogDescriptor)
      ?: return null

    val reloadedPosts = reloadPostsFromDatabaseUseCase.reloadCatalogThreads(
      currentCatalogSnapshot.catalogThreadDescriptorList
    )

    if (reloadedPosts.isEmpty()) {
      Logger.d(TAG, "loadCatalog() reloadCatalogThreads() returned empty list")
      return null
    }

    val originalPost = reloadedPosts.firstOrNull()
    if (originalPost == null) {
      return null
    }

    check(originalPost is ChanOriginalPost) { "First post is not a ChanOriginalPost: $originalPost" }
    return ChanLoaderResponse(originalPost, reloadedPosts)
  }

  companion object {
    private const val TAG = "DatabasePostLoader"
  }
}