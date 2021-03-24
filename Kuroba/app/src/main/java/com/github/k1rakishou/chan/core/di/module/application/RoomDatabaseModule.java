package com.github.k1rakishou.chan.core.di.module.application;

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
import com.github.k1rakishou.model.repository.DatabaseMetaRepository;
import com.github.k1rakishou.model.repository.HistoryNavigationRepository;
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository;
import com.github.k1rakishou.model.repository.InlinedFileInfoRepository;
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository;
import com.github.k1rakishou.model.repository.SeenPostRepository;
import com.github.k1rakishou.model.repository.SiteRepository;
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository;
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
        return modelComponent.getDatabaseMetaRepository();
    }

    @Provides
    @Singleton
    public MediaServiceLinkExtraContentRepository provideMediaServiceLinkExtraContentRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getMediaServiceLinkExtraContentRepository();
    }

    @Provides
    @Singleton
    public SeenPostRepository provideSeenPostRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getSeenPostRepository();
    }

    @Provides
    @Singleton
    public InlinedFileInfoRepository provideInlinedFileInfoRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getInlinedFileInfoRepository();
    }

    @Provides
    @Singleton
    public ChanPostRepository provideChanPostRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getChanPostRepository();
    }

    @Provides
    @Singleton
    public HistoryNavigationRepository provideHistoryNavigationRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getHistoryNavigationRepository();
    }

    @Provides
    @Singleton
    public BookmarksRepository provideBookmarksRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getBookmarksRepository();
    }

    @Provides
    @Singleton
    public ChanThreadViewableInfoRepository provideChanThreadViewableInfoRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getChanThreadViewableInfoRepository();
    }

    @Provides
    @Singleton
    public SiteRepository provideSiteRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getSiteRepository();
    }

    @Provides
    @Singleton
    public BoardRepository provideBoardRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getBoardRepository();
    }

    @Provides
    @Singleton
    public ChanSavedReplyRepository provideChanSavedReplyRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getChanSavedReplyRepository();
    }

    @Provides
    @Singleton
    public ChanPostHideRepository provideChanPostHideRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getChanPostHideRepository();
    }

    @Provides
    @Singleton
    public ChanFilterRepository provideChanFilterRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getChanFilterRepository();
    }

    @Provides
    @Singleton
    public ThreadBookmarkGroupRepository provideThreadBookmarkGroupRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getThreadBookmarkGroupRepository();
    }

    @Provides
    @Singleton
    public ChanCatalogSnapshotRepository provideChanCatalogSnapshotRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getChanCatalogSnapshotRepository();
    }

    @Provides
    @Singleton
    public ChanThreadsCache provideChanThreadsCache(
            ModelComponent modelComponent
    ) {
        return modelComponent.getChanThreadsCache();
    }

    @Provides
    @Singleton
    public ChanFilterWatchRepository provideChanFilterWatchRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getChanFilterWatchRepository();
    }

    @Provides
    @Singleton
    public ChanPostImageRepository provideChanPostImageRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getChanPostImageRepository();
    }

    @Provides
    @Singleton
    public ImageDownloadRequestRepository provideImageDownloadRequestRepository(
            ModelComponent modelComponent
    ) {
        return modelComponent.getImageDownloadRequestRepository();
    }

}
