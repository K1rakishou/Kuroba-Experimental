package com.github.k1rakishou.chan.core.di.module.application

import com.github.k1rakishou.core_logger.Logger.deps
import com.github.k1rakishou.model.di.ModelComponent
import com.github.k1rakishou.model.repository.BoardRepository
import com.github.k1rakishou.model.repository.BookmarksRepository
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanFilterRepository
import com.github.k1rakishou.model.repository.ChanFilterWatchRepository
import com.github.k1rakishou.model.repository.ChanPostHideRepository
import com.github.k1rakishou.model.repository.ChanPostImageRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.repository.ChanSavedReplyRepository
import com.github.k1rakishou.model.repository.ChanThreadViewableInfoRepository
import com.github.k1rakishou.model.repository.CompositeCatalogRepository
import com.github.k1rakishou.model.repository.DatabaseMetaRepository
import com.github.k1rakishou.model.repository.HistoryNavigationRepository
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository
import com.github.k1rakishou.model.repository.SeenPostRepository
import com.github.k1rakishou.model.repository.SiteRepository
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository
import com.github.k1rakishou.model.repository.ThreadDownloadRepository
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import com.github.k1rakishou.v2.di.KurobaSettingsComponent
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class CrossModuleBridge {
  @Provides
  @Singleton
  fun provideDatabaseMetaRepository(
    modelComponent: ModelComponent
  ): DatabaseMetaRepository {
    deps("DatabaseMetaRepository")
    return modelComponent.getDatabaseMetaRepository()
  }

  @Provides
  @Singleton
  fun provideMediaServiceLinkExtraContentRepository(
    modelComponent: ModelComponent
  ): MediaServiceLinkExtraContentRepository {
    deps("MediaServiceLinkExtraContentRepository")
    return modelComponent.getMediaServiceLinkExtraContentRepository()
  }

  @Provides
  @Singleton
  fun provideSeenPostRepository(
    modelComponent: ModelComponent
  ): SeenPostRepository {
    deps("SeenPostRepository")
    return modelComponent.getSeenPostRepository()
  }

  @Provides
  @Singleton
  fun provideChanPostRepository(
    modelComponent: ModelComponent
  ): ChanPostRepository {
    deps("ChanPostRepository")
    return modelComponent.getChanPostRepository()
  }

  @Provides
  @Singleton
  fun provideHistoryNavigationRepository(
    modelComponent: ModelComponent
  ): HistoryNavigationRepository {
    deps("HistoryNavigationRepository")
    return modelComponent.getHistoryNavigationRepository()
  }

  @Provides
  @Singleton
  fun provideBookmarksRepository(
    modelComponent: ModelComponent
  ): BookmarksRepository {
    deps("BookmarksRepository")
    return modelComponent.getBookmarksRepository()
  }

  @Provides
  @Singleton
  fun provideChanThreadViewableInfoRepository(
    modelComponent: ModelComponent
  ): ChanThreadViewableInfoRepository {
    deps("ChanThreadViewableInfoRepository")
    return modelComponent.getChanThreadViewableInfoRepository()
  }

  @Provides
  @Singleton
  fun provideSiteRepository(
    modelComponent: ModelComponent
  ): SiteRepository {
    deps("SiteRepository")
    return modelComponent.getSiteRepository()
  }

  @Provides
  @Singleton
  fun provideBoardRepository(
    modelComponent: ModelComponent
  ): BoardRepository {
    deps("BoardRepository")
    return modelComponent.getBoardRepository()
  }

  @Provides
  @Singleton
  fun provideChanSavedReplyRepository(
    modelComponent: ModelComponent
  ): ChanSavedReplyRepository {
    deps("ChanSavedReplyRepository")
    return modelComponent.getChanSavedReplyRepository()
  }

  @Provides
  @Singleton
  fun provideChanPostHideRepository(
    modelComponent: ModelComponent
  ): ChanPostHideRepository {
    deps("ChanPostHideRepository")
    return modelComponent.getChanPostHideRepository()
  }

  @Provides
  @Singleton
  fun provideChanFilterRepository(
    modelComponent: ModelComponent
  ): ChanFilterRepository {
    deps("ChanFilterRepository")
    return modelComponent.getChanFilterRepository()
  }

  @Provides
  @Singleton
  fun provideThreadBookmarkGroupRepository(
    modelComponent: ModelComponent
  ): ThreadBookmarkGroupRepository {
    deps("ThreadBookmarkGroupRepository")
    return modelComponent.getThreadBookmarkGroupRepository()
  }

  @Provides
  @Singleton
  fun provideChanCatalogSnapshotRepository(
    modelComponent: ModelComponent
  ): ChanCatalogSnapshotRepository {
    deps("ChanCatalogSnapshotRepository")
    return modelComponent.getChanCatalogSnapshotRepository()
  }

  @Provides
  @Singleton
  fun provideChanThreadsCache(
    modelComponent: ModelComponent
  ): ChanThreadsCache {
    deps("ChanThreadsCache")
    return modelComponent.getChanThreadsCache()
  }

  @Provides
  @Singleton
  fun provideChanFilterWatchRepository(
    modelComponent: ModelComponent
  ): ChanFilterWatchRepository {
    deps("ChanFilterWatchRepository")
    return modelComponent.getChanFilterWatchRepository()
  }

  @Provides
  @Singleton
  fun provideChanPostImageRepository(
    modelComponent: ModelComponent
  ): ChanPostImageRepository {
    deps("ChanPostImageRepository")
    return modelComponent.getChanPostImageRepository()
  }

  @Provides
  @Singleton
  fun provideImageDownloadRequestRepository(
    modelComponent: ModelComponent
  ): ImageDownloadRequestRepository {
    deps("ImageDownloadRequestRepository")
    return modelComponent.getImageDownloadRequestRepository()
  }

  @Provides
  @Singleton
  fun provideThreadDownloadRepository(
    modelComponent: ModelComponent
  ): ThreadDownloadRepository {
    deps("ThreadDownloadRepository")
    return modelComponent.getThreadDownloadRepository()
  }

  @Provides
  @Singleton
  fun provideChanCatalogSnapshotCache(
    modelComponent: ModelComponent
  ): ChanCatalogSnapshotCache {
    deps("ChanCatalogSnapshotCache")
    return modelComponent.getChanCatalogSnapshotCache()
  }

  @Provides
  @Singleton
  fun provideCompositeCatalogRepository(
    modelComponent: ModelComponent
  ): CompositeCatalogRepository {
    deps("CompositeCatalogRepository")
    return modelComponent.getCompositeCatalogRepository()
  }

  @Provides
  @Singleton
  fun provideKurobaSettingsDatabase(
    kurobaSettingsComponent: KurobaSettingsComponent
  ): KurobaSettingsDatabase {
    deps("KurobaSettingsDatabase")
    return kurobaSettingsComponent.getKurobaSettingsDatabase()
  }
}
