package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.SeenPostsManager
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import dagger.Lazy
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

class CatalogDataPreloader(
  private val boardManager: BoardManager,
  private val postHideManager: PostHideManager,
  private val chanCatalogSnapshotRepository: ChanCatalogSnapshotRepository,
  private val seenPostsManager: Lazy<SeenPostsManager>
) {

  suspend fun preloadCatalogInfo(catalogDescriptor: ChanDescriptor.CatalogDescriptor) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "preloadCatalogInfo($catalogDescriptor) begin")

    supervisorScope {
      val jobs = mutableListOf<Deferred<Unit>>()

      jobs += async(Dispatchers.IO) { postHideManager.preloadForCatalog(catalogDescriptor) }
      jobs += async(Dispatchers.IO) {
        val isUnlimitedCatalog = boardManager.byBoardDescriptor(catalogDescriptor.boardDescriptor)
          ?.isUnlimitedCatalog
          ?: false

        if (!isUnlimitedCatalog) {
          chanCatalogSnapshotRepository.preloadChanCatalogSnapshot(catalogDescriptor, isUnlimitedCatalog)
            .peekError { error -> Logger.e(TAG, "preloadChanCatalogSnapshot($catalogDescriptor) error", error) }
            .ignore()
        }

        return@async
      }

      ModularResult.Try { jobs.awaitAll() }
        .peekError { error -> Logger.e(TAG, "preloadCatalogInfo() error", error) }
        .ignore()
    }

    Logger.d(TAG, "preloadCatalogInfo($catalogDescriptor) end")
  }

  suspend fun postloadCatalogInfo(catalogDescriptor: ChanDescriptor.CatalogDescriptor) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "postloadCatalogInfo($catalogDescriptor) begin")

    supervisorScope {
      val jobs = mutableListOf<Deferred<Unit>>()

      jobs += async(Dispatchers.IO) { seenPostsManager.get().postloadForCatalog(catalogDescriptor) }

      ModularResult.Try { jobs.awaitAll() }
        .peekError { error -> Logger.e(TAG, "preloadCatalogInfo() error", error) }
        .ignore()
    }

    Logger.d(TAG, "postloadCatalogInfo($catalogDescriptor) end")
  }

  companion object {
    private const val TAG = "CatalogDataPreloadUseCase"
  }

}