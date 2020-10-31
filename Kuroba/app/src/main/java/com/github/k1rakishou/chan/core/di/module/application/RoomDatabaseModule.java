package com.github.k1rakishou.chan.core.di.module.application;

import com.github.k1rakishou.model.di.ModelMainComponent;
import com.github.k1rakishou.model.repository.BoardRepository;
import com.github.k1rakishou.model.repository.BookmarksRepository;
import com.github.k1rakishou.model.repository.ChanFilterRepository;
import com.github.k1rakishou.model.repository.ChanPostHideRepository;
import com.github.k1rakishou.model.repository.ChanPostRepository;
import com.github.k1rakishou.model.repository.ChanSavedReplyRepository;
import com.github.k1rakishou.model.repository.ChanThreadViewableInfoRepository;
import com.github.k1rakishou.model.repository.HistoryNavigationRepository;
import com.github.k1rakishou.model.repository.InlinedFileInfoRepository;
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository;
import com.github.k1rakishou.model.repository.SeenPostRepository;
import com.github.k1rakishou.model.repository.SiteRepository;
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class RoomDatabaseModule {
    @Provides
    @Singleton
    public MediaServiceLinkExtraContentRepository provideMediaServiceLinkExtraContentRepository(
            ModelMainComponent modelMainComponent
    ) {
        return modelMainComponent.getMediaServiceLinkExtraContentRepository();
    }

    @Provides
    @Singleton
    public SeenPostRepository provideSeenPostRepository(
            ModelMainComponent modelMainComponent
    ) {
        return modelMainComponent.getSeenPostRepository();
    }

    @Provides
    @Singleton
    public InlinedFileInfoRepository provideInlinedFileInfoRepository(
            ModelMainComponent modelMainComponent
    ) {
        return modelMainComponent.getInlinedFileInfoRepository();
    }

    @Provides
    @Singleton
    public ChanPostRepository provideChanPostRepository(
            ModelMainComponent modelMainComponent
    ) {
        return modelMainComponent.getChanPostRepository();
    }

    @Provides
    @Singleton
    public HistoryNavigationRepository provideHistoryNavigationRepository(
            ModelMainComponent modelMainComponent
    ) {
        return modelMainComponent.getHistoryNavigationRepository();
    }

    @Provides
    @Singleton
    public BookmarksRepository provideBookmarksRepository(
            ModelMainComponent modelMainComponent
    ) {
        return modelMainComponent.getBookmarksRepository();
    }

    @Provides
    @Singleton
    public ChanThreadViewableInfoRepository provideChanThreadViewableInfoRepository(
            ModelMainComponent modelMainComponent
    ) {
        return modelMainComponent.getChanThreadViewableInfoRepository();
    }

    @Provides
    @Singleton
    public SiteRepository provideSiteRepository(
            ModelMainComponent modelMainComponent
    ) {
        return modelMainComponent.getSiteRepository();
    }

    @Provides
    @Singleton
    public BoardRepository provideBoardRepository(
            ModelMainComponent modelMainComponent
    ) {
        return modelMainComponent.getBoardRepository();
    }

    @Provides
    @Singleton
    public ChanSavedReplyRepository provideChanSavedReplyRepository(
            ModelMainComponent modelMainComponent
    ) {
        return modelMainComponent.getChanSavedReplyRepository();
    }

    @Provides
    @Singleton
    public ChanPostHideRepository provideChanPostHideRepository(
            ModelMainComponent modelMainComponent
    ) {
        return modelMainComponent.getChanPostHideRepository();
    }

    @Provides
    @Singleton
    public ChanFilterRepository provideChanFilterRepository(
            ModelMainComponent modelMainComponent
    ) {
        return modelMainComponent.getChanFilterRepository();
    }

    @Provides
    @Singleton
    public ThreadBookmarkGroupRepository provideThreadBookmarkGroupRepository(
            ModelMainComponent modelMainComponent
    ) {
        return modelMainComponent.getThreadBookmarkGroupRepository();
    }

}
