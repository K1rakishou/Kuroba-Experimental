package com.github.k1rakishou.chan.core.di.module.application;

import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.model.di.ModelComponent;
import com.github.k1rakishou.model.repository.BoardRepository;
import com.github.k1rakishou.model.repository.BookmarksRepository;
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository;
import com.github.k1rakishou.model.repository.ChanFilterRepository;
import com.github.k1rakishou.model.repository.ChanFilterWatchRepository;
import com.github.k1rakishou.model.repository.ChanPostHideRepository;
import com.github.k1rakishou.model.repository.ChanPostImageRepository;
import com.github.k1rakishou.model.repository.ChanPostRepository;
import com.github.k1rakishou.model.repository.ChanSavedReplyRepository;
import com.github.k1rakishou.model.repository.ChanThreadViewableInfoRepository;
import com.github.k1rakishou.model.repository.CompositeCatalogRepository;
import com.github.k1rakishou.model.repository.DatabaseMetaRepository;
import com.github.k1rakishou.model.repository.HistoryNavigationRepository;
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository;
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository;
import com.github.k1rakishou.model.repository.SeenPostRepository;
import com.github.k1rakishou.model.repository.SiteRepository;
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository;
import com.github.k1rakishou.model.repository.ThreadDownloadRepository;
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache;
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class RoomDatabaseModule {

    @Provides
    @Singleton
    public DatabaseMetaRepository provideDatabaseMetaRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("DatabaseMetaRepository");
        return modelComponent.getDatabaseMetaRepository();
    }

    @Provides
    @Singleton
    public MediaServiceLinkExtraContentRepository provideMediaServiceLinkExtraContentRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("MediaServiceLinkExtraContentRepository");
        return modelComponent.getMediaServiceLinkExtraContentRepository();
    }

    @Provides
    @Singleton
    public SeenPostRepository provideSeenPostRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("SeenPostRepository");
        return modelComponent.getSeenPostRepository();
    }

    @Provides
    @Singleton
    public ChanPostRepository provideChanPostRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("ChanPostRepository");
        return modelComponent.getChanPostRepository();
    }

    @Provides
    @Singleton
    public HistoryNavigationRepository provideHistoryNavigationRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("HistoryNavigationRepository");
        return modelComponent.getHistoryNavigationRepository();
    }

    @Provides
    @Singleton
    public BookmarksRepository provideBookmarksRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("BookmarksRepository");
        return modelComponent.getBookmarksRepository();
    }

    @Provides
    @Singleton
    public ChanThreadViewableInfoRepository provideChanThreadViewableInfoRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("ChanThreadViewableInfoRepository");
        return modelComponent.getChanThreadViewableInfoRepository();
    }

    @Provides
    @Singleton
    public SiteRepository provideSiteRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("SiteRepository");
        return modelComponent.getSiteRepository();
    }

    @Provides
    @Singleton
    public BoardRepository provideBoardRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("BoardRepository");
        return modelComponent.getBoardRepository();
    }

    @Provides
    @Singleton
    public ChanSavedReplyRepository provideChanSavedReplyRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("ChanSavedReplyRepository");
        return modelComponent.getChanSavedReplyRepository();
    }

    @Provides
    @Singleton
    public ChanPostHideRepository provideChanPostHideRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("ChanPostHideRepository");
        return modelComponent.getChanPostHideRepository();
    }

    @Provides
    @Singleton
    public ChanFilterRepository provideChanFilterRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("ChanFilterRepository");
        return modelComponent.getChanFilterRepository();
    }

    @Provides
    @Singleton
    public ThreadBookmarkGroupRepository provideThreadBookmarkGroupRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("ThreadBookmarkGroupRepository");
        return modelComponent.getThreadBookmarkGroupRepository();
    }

    @Provides
    @Singleton
    public ChanCatalogSnapshotRepository provideChanCatalogSnapshotRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("ChanCatalogSnapshotRepository");
        return modelComponent.getChanCatalogSnapshotRepository();
    }

    @Provides
    @Singleton
    public ChanThreadsCache provideChanThreadsCache(
            ModelComponent modelComponent
    ) {
        Logger.deps("ChanThreadsCache");
        return modelComponent.getChanThreadsCache();
    }

    @Provides
    @Singleton
    public ChanFilterWatchRepository provideChanFilterWatchRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("ChanFilterWatchRepository");
        return modelComponent.getChanFilterWatchRepository();
    }

    @Provides
    @Singleton
    public ChanPostImageRepository provideChanPostImageRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("ChanPostImageRepository");
        return modelComponent.getChanPostImageRepository();
    }

    @Provides
    @Singleton
    public ImageDownloadRequestRepository provideImageDownloadRequestRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("ImageDownloadRequestRepository");
        return modelComponent.getImageDownloadRequestRepository();
    }

    @Provides
    @Singleton
    public ThreadDownloadRepository provideThreadDownloadRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("ThreadDownloadRepository");
        return modelComponent.getThreadDownloadRepository();
    }

    @Provides
    @Singleton
    public ChanCatalogSnapshotCache provideChanCatalogSnapshotCache(
            ModelComponent modelComponent
    ) {
        Logger.deps("ChanCatalogSnapshotCache");
        return modelComponent.getChanCatalogSnapshotCache();
    }

    @Provides
    @Singleton
    public CompositeCatalogRepository provideCompositeCatalogRepository(
            ModelComponent modelComponent
    ) {
        Logger.deps("CompositeCatalogRepository");
        return modelComponent.getCompositeCatalogRepository();
    }

}
