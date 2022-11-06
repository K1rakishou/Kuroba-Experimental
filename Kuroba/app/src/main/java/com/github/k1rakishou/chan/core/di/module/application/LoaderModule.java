package com.github.k1rakishou.chan.core.di.module.application;

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.cache.CacheHandler;
import com.github.k1rakishou.chan.core.cache.FileCacheV2;
import com.github.k1rakishou.chan.core.helper.FilterEngine;
import com.github.k1rakishou.chan.core.loader.impl.Chan4CloudFlareImagePreloader;
import com.github.k1rakishou.chan.core.loader.impl.PostExtraContentLoader;
import com.github.k1rakishou.chan.core.loader.impl.PostHighlightFilterLoader;
import com.github.k1rakishou.chan.core.loader.impl.PrefetchLoader;
import com.github.k1rakishou.chan.core.loader.impl.ThirdEyeLoader;
import com.github.k1rakishou.chan.core.loader.impl.external_media_service.ExternalMediaServiceExtraInfoFetcher;
import com.github.k1rakishou.chan.core.loader.impl.external_media_service.SoundCloudMediaServiceExtraInfoFetcher;
import com.github.k1rakishou.chan.core.loader.impl.external_media_service.StreamableMediaServiceExtraInfoFetcher;
import com.github.k1rakishou.chan.core.loader.impl.external_media_service.YoutubeMediaServiceExtraInfoFetcher;
import com.github.k1rakishou.chan.core.manager.ArchivesManager;
import com.github.k1rakishou.chan.core.manager.Chan4CloudFlareImagePreloaderManager;
import com.github.k1rakishou.chan.core.manager.ChanFilterManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadManager;
import com.github.k1rakishou.chan.core.manager.PostFilterHighlightManager;
import com.github.k1rakishou.chan.core.manager.PostFilterManager;
import com.github.k1rakishou.chan.core.manager.PrefetchStateManager;
import com.github.k1rakishou.chan.core.manager.ThirdEyeManager;
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Singleton;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

@Module
public class LoaderModule {

    @Provides
    @Singleton
    public PrefetchLoader providePrefetchLoader(
            Lazy<FileCacheV2> fileCacheV2,
            Lazy<CacheHandler> cacheHandler,
            PrefetchStateManager prefetchStateManager,
            Lazy<ChanThreadManager> chanThreadManager,
            Lazy<ArchivesManager> archivesManager,
            Lazy<ThreadDownloadManager> threadDownloadManager
    ) {
        Logger.deps("PrefetchLoader");

        return new PrefetchLoader(
                fileCacheV2,
                cacheHandler,
                chanThreadManager,
                archivesManager,
                prefetchStateManager,
                threadDownloadManager
        );
    }

    @Provides
    @Singleton
    public ThirdEyeLoader provideThirdEyeLoader(
            AppConstants appConstants,
            Lazy<ThirdEyeManager> thirdEyeManager,
            Lazy<ChanThreadManager> chanThreadManager,
            Lazy<ProxiedOkHttpClient> proxiedOkHttpClient
    ) {
        Logger.deps("ThirdEyeLoader");

        return new ThirdEyeLoader(
                appConstants,
                thirdEyeManager,
                chanThreadManager,
                proxiedOkHttpClient
        );
    }

    @Provides
    @Singleton
    public Chan4CloudFlareImagePreloader provideChan4CloudFlareImagePreloader(
            Chan4CloudFlareImagePreloaderManager chan4CloudFlareImagePreloaderManager
    ) {
        Logger.deps("Chan4CloudFlareImagePreloader");

        return new Chan4CloudFlareImagePreloader(
                chan4CloudFlareImagePreloaderManager
        );
    }

    @Provides
    @Singleton
    public PostHighlightFilterLoader providePostHighlightFilterLoader(
            ChanFilterManager chanFilterManager,
            FilterEngine filterEngine,
            PostFilterManager postFilterManager,
            ChanThreadManager chanThreadManager,
            PostFilterHighlightManager postFilterHighlightManager
    ) {
        Logger.deps("PostHighlightFilterLoader");

        return new PostHighlightFilterLoader(
                chanFilterManager,
                filterEngine,
                postFilterManager,
                chanThreadManager,
                postFilterHighlightManager
        );
    }

    @Provides
    @Singleton
    public YoutubeMediaServiceExtraInfoFetcher provideYoutubeMediaServiceExtraInfoFetcher(
            MediaServiceLinkExtraContentRepository mediaServiceLinkExtraContentRepository
    ) {
        Logger.deps("YoutubeMediaServiceExtraInfoFetcher");

        return new YoutubeMediaServiceExtraInfoFetcher(mediaServiceLinkExtraContentRepository);
    }

    @Provides
    @Singleton
    public SoundCloudMediaServiceExtraInfoFetcher provideSoundCloudMediaServiceExtraInfoFetcher(
            MediaServiceLinkExtraContentRepository mediaServiceLinkExtraContentRepository
    ) {
        Logger.deps("SoundCloudMediaServiceExtraInfoFetcher");

        return new SoundCloudMediaServiceExtraInfoFetcher(mediaServiceLinkExtraContentRepository);
    }

    @Provides
    @Singleton
    public StreamableMediaServiceExtraInfoFetcher provideStreamableMediaServiceExtraInfoFetcher(
            MediaServiceLinkExtraContentRepository mediaServiceLinkExtraContentRepository
    ) {
        Logger.deps("StreamableMediaServiceExtraInfoFetcher");

        return new StreamableMediaServiceExtraInfoFetcher(mediaServiceLinkExtraContentRepository);
    }

    @Provides
    @Singleton
    public PostExtraContentLoader providePostExtraContentLoader(
            YoutubeMediaServiceExtraInfoFetcher youtubeMediaServiceExtraInfoFetcher,
            SoundCloudMediaServiceExtraInfoFetcher soundCloudMediaServiceExtraInfoFetcher,
            StreamableMediaServiceExtraInfoFetcher streamableMediaServiceExtraInfoFetcher,
            ChanThreadManager chanThreadManager
    ) {
        Logger.deps("PostExtraContentLoader");

        List<ExternalMediaServiceExtraInfoFetcher> fetchers = new ArrayList<>();
        fetchers.add(youtubeMediaServiceExtraInfoFetcher);
        fetchers.add(soundCloudMediaServiceExtraInfoFetcher);
        fetchers.add(streamableMediaServiceExtraInfoFetcher);

        return new PostExtraContentLoader(
                chanThreadManager,
                fetchers
        );
    }

}
