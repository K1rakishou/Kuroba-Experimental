package com.github.adamantcheese.chan.core.di;

import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.loader.impl.InlinedFileInfoLoader;
import com.github.adamantcheese.chan.core.loader.impl.PostExtraContentLoader;
import com.github.adamantcheese.chan.core.loader.impl.PrefetchLoader;
import com.github.adamantcheese.chan.core.loader.impl.external_media_service.ExternalMediaServiceExtraInfoFetcher;
import com.github.adamantcheese.chan.core.loader.impl.external_media_service.YoutubeMediaServiceExtraInfoFetcher;
import com.github.adamantcheese.chan.core.manager.PrefetchImageDownloadIndicatorManager;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.model.repository.InlinedFileInfoRepository;
import com.github.adamantcheese.model.repository.MediaServiceLinkExtraContentRepository;
import com.github.k1rakishou.feather2.Provides;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Named;
import javax.inject.Singleton;

import io.reactivex.schedulers.Schedulers;

public class LoaderModule {

    @Provides
    @Singleton
    public PrefetchLoader providePrefetchLoader(
            FileCacheV2 fileCacheV2,
            CacheHandler cacheHandler,
            PrefetchImageDownloadIndicatorManager prefetchImageDownloadIndicatorManager,
            @Named(ExecutorsModule.onDemandContentLoaderExecutorName) Executor onDemandContentLoaderExecutor
    ) {
        Logger.d(AppModule.DI_TAG, "PrefetchLoader");

        return new PrefetchLoader(
                Schedulers.from(onDemandContentLoaderExecutor),
                fileCacheV2,
                cacheHandler,
                prefetchImageDownloadIndicatorManager
        );
    }

    @Provides
    @Singleton
    public InlinedFileInfoLoader provideInlinedFileInfoLoader(
            InlinedFileInfoRepository inlinedFileInfoRepository,
            @Named(ExecutorsModule.onDemandContentLoaderExecutorName) Executor onDemandContentLoaderExecutor
    ) {
        Logger.d(AppModule.DI_TAG, "InlinedFileInfoLoader");

        return new InlinedFileInfoLoader(
                Schedulers.from(onDemandContentLoaderExecutor),
                inlinedFileInfoRepository
        );
    }

    @Provides
    @Singleton
    public YoutubeMediaServiceExtraInfoFetcher provideYoutubeMediaServiceExtraInfoFetcher(
            MediaServiceLinkExtraContentRepository mediaServiceLinkExtraContentRepository
    ) {
        Logger.d(AppModule.DI_TAG, "YoutubeMediaServiceExtraInfoFetcher");

        return new YoutubeMediaServiceExtraInfoFetcher(mediaServiceLinkExtraContentRepository);
    }

    @Provides
    @Singleton
    public PostExtraContentLoader providePostExtraContentLoader(
            YoutubeMediaServiceExtraInfoFetcher youtubeMediaServiceExtraInfoFetcher,
            @Named(ExecutorsModule.onDemandContentLoaderExecutorName) Executor onDemandContentLoaderExecutor
    ) {
        Logger.d(AppModule.DI_TAG, "PostExtraContentLoader");

        List<ExternalMediaServiceExtraInfoFetcher> fetchers = new ArrayList<>();
        fetchers.add(youtubeMediaServiceExtraInfoFetcher);

        return new PostExtraContentLoader(
                Schedulers.from(onDemandContentLoaderExecutor),
                fetchers
        );
    }

}
