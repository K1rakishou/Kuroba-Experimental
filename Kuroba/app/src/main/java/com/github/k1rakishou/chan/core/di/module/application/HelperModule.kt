package com.github.k1rakishou.chan.core.di.module.application

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class HelperModule {

  @Provides
  @Singleton
  fun provideChanThreadLoaderCoordinator(
    proxiedOkHttpClient: RealProxiedOkHttpClient,
    savedReplyManager: SavedReplyManager,
    filterEngine: FilterEngine,
    chanPostRepository: ChanPostRepository,
    chanCatalogSnapshotRepository: ChanCatalogSnapshotRepository,
    appConstants: AppConstants,
    postFilterManager: PostFilterManager,
    boardManager: BoardManager
  ): ChanThreadLoaderCoordinator {
    Logger.d(AppModule.DI_TAG, "ChanThreadLoaderCoordinator")

    return ChanThreadLoaderCoordinator(
      proxiedOkHttpClient,
      savedReplyManager,
      filterEngine,
      chanPostRepository,
      chanCatalogSnapshotRepository,
      appConstants,
      postFilterManager,
      ChanSettings.verboseLogs.get(),
      boardManager
    )
  }

}