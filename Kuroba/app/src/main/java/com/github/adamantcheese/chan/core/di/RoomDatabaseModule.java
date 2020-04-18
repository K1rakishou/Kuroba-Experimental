package com.github.adamantcheese.chan.core.di;

import com.github.adamantcheese.model.di.MainComponent;
import com.github.adamantcheese.model.repository.ArchivesRepository;
import com.github.adamantcheese.model.repository.ChanPostRepository;
import com.github.adamantcheese.model.repository.InlinedFileInfoRepository;
import com.github.adamantcheese.model.repository.MediaServiceLinkExtraContentRepository;
import com.github.adamantcheese.model.repository.SeenPostRepository;

import org.codejargon.feather.Provides;

import javax.inject.Singleton;

public class RoomDatabaseModule {
    private MainComponent mainComponent;

    public RoomDatabaseModule(MainComponent mainComponent) {
        this.mainComponent = mainComponent;
    }

    @Provides
    @Singleton
    public MediaServiceLinkExtraContentRepository provideMediaServiceLinkExtraContentRepository() {
        return mainComponent.getMediaServiceLinkExtraContentRepository();
    }

    @Provides
    @Singleton
    public SeenPostRepository provideSeenPostRepository() {
        return mainComponent.getSeenPostRepository();
    }

    @Provides
    @Singleton
    public InlinedFileInfoRepository provideInlinedFileInfoRepository() {
        return mainComponent.getInlinedFileInfoRepository();
    }

    @Provides
    @Singleton
    public ChanPostRepository provideChanPostRepository() {
        return mainComponent.getChanPostRepository();
    }

    @Provides
    @Singleton
    public ArchivesRepository provideArchivesRepository() {
        return mainComponent.getArchivesRepository();
    }
}
