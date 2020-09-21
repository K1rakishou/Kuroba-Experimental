package com.github.k1rakishou.chan.core.di;

import com.github.k1rakishou.feather2.Provides;
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

import javax.inject.Singleton;

public class RoomDatabaseModule {
    private ModelMainComponent modelMainComponent;

    public RoomDatabaseModule(ModelMainComponent modelMainComponent) {
        this.modelMainComponent = modelMainComponent;
    }

    @Provides
    @Singleton
    public MediaServiceLinkExtraContentRepository provideMediaServiceLinkExtraContentRepository() {
        return modelMainComponent.getMediaServiceLinkExtraContentRepository();
    }

    @Provides
    @Singleton
    public SeenPostRepository provideSeenPostRepository() {
        return modelMainComponent.getSeenPostRepository();
    }

    @Provides
    @Singleton
    public InlinedFileInfoRepository provideInlinedFileInfoRepository() {
        return modelMainComponent.getInlinedFileInfoRepository();
    }

    @Provides
    @Singleton
    public ChanPostRepository provideChanPostRepository() {
        return modelMainComponent.getChanPostRepository();
    }

    @Provides
    @Singleton
    public HistoryNavigationRepository provideHistoryNavigationRepository() {
        return modelMainComponent.getHistoryNavigationRepository();
    }

    @Provides
    @Singleton
    public BookmarksRepository provideBookmarksRepository() {
        return modelMainComponent.getBookmarksRepository();
    }

    @Provides
    @Singleton
    public ChanThreadViewableInfoRepository provideChanThreadViewableInfoRepository() {
        return modelMainComponent.getChanThreadViewableInfoRepository();
    }

    @Provides
    @Singleton
    public SiteRepository provideSiteRepository() {
        return modelMainComponent.getSiteRepository();
    }

    @Provides
    @Singleton
    public BoardRepository provideBoardRepository() {
        return modelMainComponent.getBoardRepository();
    }

    @Provides
    @Singleton
    public ChanSavedReplyRepository provideChanSavedReplyRepository() {
        return modelMainComponent.getChanSavedReplyRepository();
    }

    @Provides
    @Singleton
    public ChanPostHideRepository provideChanPostHideRepository() {
        return modelMainComponent.getChanPostHideRepository();
    }

    @Provides
    @Singleton
    public ChanFilterRepository provideChanFilterRepository() {
        return modelMainComponent.getChanFilterRepository();
    }

}
