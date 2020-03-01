package com.github.adamantcheese.chan.core.di;

import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.loader.impl.PostExtraContentLoader;
import com.github.adamantcheese.chan.core.loader.impl.PrefetchLoader;

import org.codejargon.feather.Provides;

import javax.inject.Singleton;

public class LoaderModule {

    @Provides
    @Singleton
    public PrefetchLoader providePrefetchLoader(FileCacheV2 fileCacheV2) {
        return new PrefetchLoader(fileCacheV2);
    }

    @Provides
    @Singleton
    public PostExtraContentLoader providePostExtraContentLoader(NetModule.ProxiedOkHttpClient okHttpClient) {
        return new PostExtraContentLoader(okHttpClient);
    }

}
