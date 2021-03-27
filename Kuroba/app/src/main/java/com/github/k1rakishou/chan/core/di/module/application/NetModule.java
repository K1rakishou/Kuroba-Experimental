/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.di.module.application;

import android.content.Context;
import android.net.ConnectivityManager;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.chan.core.base.okhttp.CoilOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.HttpLoggingInterceptorLazy;
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.RealDownloaderOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.cache.CacheHandler;
import com.github.k1rakishou.chan.core.cache.FileCacheV2;
import com.github.k1rakishou.chan.core.cache.stream.WebmStreamingSource;
import com.github.k1rakishou.chan.core.helper.ProxyStorage;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.site.SiteResolver;
import com.github.k1rakishou.chan.core.site.http.HttpCallManager;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.fsaf.FileManager;
import com.google.gson.Gson;

import java.io.File;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import kotlinx.coroutines.CoroutineScope;
import okhttp3.Dns;

import static com.github.k1rakishou.chan.core.di.module.application.AppModule.getCacheDir;

@Module
public class NetModule {
    private static final String FILE_CACHE_DIR = "filecache";
    private static final String FILE_CHUNKS_CACHE_DIR = "file_chunks_cache";

    @Provides
    @Singleton
    public HttpLoggingInterceptorLazy provideHttpLoggingInterceptorLazy() {
        return new HttpLoggingInterceptorLazy();
    }

    @Provides
    @Singleton
    public ProxyStorage provideProxyStorage(
            CoroutineScope appScope,
            Context appContext,
            AppConstants appConstants,
            SiteResolver siteResolver,
            Gson gson
    ) {
        return new ProxyStorage(
                appScope,
                appContext,
                appConstants,
                ChanSettings.verboseLogs.get(),
                siteResolver,
                gson
        );
    }

    @Provides
    @Singleton
    public CacheHandler provideCacheHandler() {
        File cacheDir = getCacheDir().getValue();

        return new CacheHandler(
                ChanSettings.verboseLogs.get(),
                new File(cacheDir, FILE_CACHE_DIR),
                new File(cacheDir, FILE_CHUNKS_CACHE_DIR),
                ChanSettings.prefetchMedia.get()
        );
    }

    @Provides
    @Singleton
    public FileCacheV2 provideFileCacheV2(
            ConnectivityManager connectivityManager,
            FileManager fileManager,
            CacheHandler cacheHandler,
            SiteResolver siteResolver,
            RealDownloaderOkHttpClient realDownloaderOkHttpClient,
            AppConstants appConstants
    ) {
        return new FileCacheV2(
                fileManager,
                cacheHandler,
                siteResolver,
                realDownloaderOkHttpClient,
                connectivityManager,
                appConstants
        );
    }

    @Provides
    @Singleton
    public WebmStreamingSource provideWebmStreamingSource(
            SiteManager siteManager,
            SiteResolver siteResolver,
            FileManager fileManager,
            FileCacheV2 fileCacheV2,
            CacheHandler cacheHandler,
            AppConstants appConstants
    ) {
        return new WebmStreamingSource(
                siteManager,
                siteResolver,
                fileManager,
                fileCacheV2,
                cacheHandler,
                appConstants
        );
    }

    @Provides
    @Singleton
    public HttpCallManager provideHttpCallManager(
            ProxiedOkHttpClient okHttpClient,
            AppConstants appConstants
    ) {
        return new HttpCallManager(okHttpClient, appConstants);
    }

    /**
     * This okHttpClient is for posting.
     */
    @Provides
    @Singleton
    public ProxiedOkHttpClient provideProxiedOkHttpClient(
            Dns okHttpDns,
            Chan.OkHttpProtocols okHttpProtocols,
            ProxyStorage proxyStorage,
            HttpLoggingInterceptorLazy httpLoggingInterceptorLazy,
            SiteResolver siteResolver
    ) {
        return new RealProxiedOkHttpClient(
                okHttpDns,
                okHttpProtocols,
                proxyStorage,
                httpLoggingInterceptorLazy,
                siteResolver
        );
    }

    /**
     * This okHttpClient is for Coil image loading library
     */
    @Provides
    @Singleton
    public CoilOkHttpClient provideCoilOkHttpClient(
            Context applicationContext,
            Dns okHttpDns,
            Chan.OkHttpProtocols okHttpProtocols,
            ProxyStorage proxyStorage,
            HttpLoggingInterceptorLazy httpLoggingInterceptorLazy,
            SiteResolver siteResolver
    ) {
        return new CoilOkHttpClient(
                applicationContext,
                okHttpDns,
                okHttpProtocols,
                proxyStorage,
                httpLoggingInterceptorLazy,
                siteResolver
        );
    }

    /**
     * This okHttpClient is for images/file/apk updates/ downloading, prefetching, etc.
     */
    @Provides
    @Singleton
    public RealDownloaderOkHttpClient provideDownloaderOkHttpClient(
            Dns okHttpDns,
            Chan.OkHttpProtocols okHttpProtocols,
            ProxyStorage proxyStorage,
            HttpLoggingInterceptorLazy httpLoggingInterceptorLazy,
            SiteResolver siteResolver
    ) {
        return new RealDownloaderOkHttpClient(
                okHttpDns,
                okHttpProtocols,
                proxyStorage,
                httpLoggingInterceptorLazy,
                siteResolver
        );
    }
}
