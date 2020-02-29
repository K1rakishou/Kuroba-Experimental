package com.github.adamantcheese.chan.core.di;

import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.manager.loader.PrefetchLoader;

import org.codejargon.feather.Provides;

import javax.inject.Singleton;

public class LoaderModule {

    @Provides
    @Singleton
    public PrefetchLoader providePrefetchLoader(FileCacheV2 fileCacheV2) {
        return new PrefetchLoader(fileCacheV2);
    }

}
