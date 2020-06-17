package com.github.adamantcheese.chan.core.di;

import com.github.adamantcheese.model.di.ModelMainComponent;
import com.github.adamantcheese.model.repository.BookmarksRepository;
import com.github.adamantcheese.model.repository.ChanPostRepository;
import com.github.adamantcheese.model.repository.HistoryNavigationRepository;
import com.github.adamantcheese.model.repository.InlinedFileInfoRepository;
import com.github.adamantcheese.model.repository.MediaServiceLinkExtraContentRepository;
import com.github.adamantcheese.model.repository.SeenPostRepository;
import com.github.adamantcheese.model.repository.ThirdPartyArchiveInfoRepository;
import com.github.k1rakishou.feather2.Provides;

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
    public ThirdPartyArchiveInfoRepository provideThirdPartyArchiveInfoRepository() {
        return modelMainComponent.getThirdPartyArchiveInfoRepository();
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
}
