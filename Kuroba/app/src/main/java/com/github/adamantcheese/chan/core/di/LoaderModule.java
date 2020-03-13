package com.github.adamantcheese.chan.core.di;

import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.loader.impl.PostExtraContentLoader;
import com.github.adamantcheese.chan.core.loader.impl.PrefetchLoader;
import com.github.adamantcheese.chan.core.loader.impl.external_media_service.ExternalMediaServiceExtraInfoFetcher;
import com.github.adamantcheese.chan.core.loader.impl.external_media_service.YoutubeMediaServiceExtraInfoFetcher;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.database.repository.YoutubeLinkExtraContentRepository;

import org.codejargon.feather.Provides;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Named;
import javax.inject.Singleton;

import io.reactivex.schedulers.Schedulers;

public class LoaderModule {

    @Provides
    @Singleton
    public PrefetchLoader providePrefetchLoader(FileCacheV2 fileCacheV2) {
        Logger.d(AppModule.DI_TAG, "PrefetchLoader");

        return new PrefetchLoader(fileCacheV2);
    }

    @Provides
    @Singleton
    public YoutubeMediaServiceExtraInfoFetcher provideYoutubeMediaServiceExtraInfoFetcher(
            YoutubeLinkExtraContentRepository youtubeLinkExtraContentRepository
    ) {
        Logger.d(AppModule.DI_TAG, "YoutubeMediaServiceExtraInfoFetcher");

        return new YoutubeMediaServiceExtraInfoFetcher(youtubeLinkExtraContentRepository);
    }

    @Provides
    @Singleton
    public PostExtraContentLoader providePostExtraContentLoader(
            NetModule.ProxiedOkHttpClient okHttpClient,
            YoutubeMediaServiceExtraInfoFetcher youtubeMediaServiceExtraInfoFetcher,
            @Named(ExecutorsModule.onDemandContentLoaderExecutorName) Executor onDemandContentLoaderExecutor
    ) {
        Logger.d(AppModule.DI_TAG, "PostExtraContentLoader");

        List<ExternalMediaServiceExtraInfoFetcher> fetchers = new ArrayList<>();
        fetchers.add(youtubeMediaServiceExtraInfoFetcher);

        return new PostExtraContentLoader(
                okHttpClient,
                Schedulers.from(onDemandContentLoaderExecutor),
                fetchers
        );
    }

}
