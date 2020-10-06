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
package com.github.k1rakishou.chan.core.di;

import android.net.ConnectivityManager;

import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.cache.CacheHandler;
import com.github.k1rakishou.chan.core.cache.FileCacheV2;
import com.github.k1rakishou.chan.core.cache.stream.WebmStreamingSource;
import com.github.k1rakishou.chan.core.settings.ChanSettings;
import com.github.k1rakishou.chan.core.site.SiteResolver;
import com.github.k1rakishou.chan.core.site.http.HttpCallManager;
import com.github.k1rakishou.chan.utils.Logger;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.feather2.Provides;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.RawFile;

import java.io.File;

import javax.inject.Named;
import javax.inject.Singleton;

import okhttp3.Dns;
import okhttp3.OkHttpClient;

import static com.github.k1rakishou.chan.core.di.AppModule.getCacheDir;
import static java.util.concurrent.TimeUnit.SECONDS;

public class NetModule {
    public static final String DOWNLOADER_OKHTTP_CLIENT_NAME = "downloader_okhttp_client";
    private static final String FILE_CACHE_DIR = "filecache";
    private static final String FILE_CHUNKS_CACHE_DIR = "file_chunks_cache";

    @Provides
    @Singleton
    public CacheHandler provideCacheHandler(
            FileManager fileManager
    ) {
        Logger.d(AppModule.DI_TAG, "Cache handler");

        File cacheDir = getCacheDir();
        RawFile cacheDirFile = fileManager.fromRawFile(new File(cacheDir, FILE_CACHE_DIR));
        RawFile chunksCacheDirFile = fileManager.fromRawFile(new File(cacheDir, FILE_CHUNKS_CACHE_DIR));

        return new CacheHandler(fileManager, cacheDirFile, chunksCacheDirFile, ChanSettings.autoLoadThreadImages.get());
    }

    @Provides
    @Singleton
    public FileCacheV2 provideFileCacheV2(
            ConnectivityManager connectivityManager,
            FileManager fileManager,
            CacheHandler cacheHandler,
            SiteResolver siteResolver,
            @Named(DOWNLOADER_OKHTTP_CLIENT_NAME) OkHttpClient okHttpClient,
            AppConstants appConstants
    ) {
        Logger.d(AppModule.DI_TAG, "File cache V2");
        return new FileCacheV2(
                fileManager,
                cacheHandler,
                siteResolver,
                okHttpClient,
                connectivityManager,
                appConstants
        );
    }

    @Provides
    @Singleton
    public WebmStreamingSource provideWebmStreamingSource(
            FileManager fileManager,
            FileCacheV2 fileCacheV2,
            CacheHandler cacheHandler,
            AppConstants appConstants
    ) {
        Logger.d(AppModule.DI_TAG, "WebmStreamingSource");
        return new WebmStreamingSource(
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
        Logger.d(AppModule.DI_TAG, "Http call manager");
        return new HttpCallManager(okHttpClient, appConstants);
    }

    /**
     * This okHttpClient is for posting.
     */
    // TODO(FileCacheV2): make this @Named as well instead of using hacks
    @Provides
    @Singleton
    public ProxiedOkHttpClient provideProxiedOkHttpClient(Dns okHttpDns, Chan.OkHttpProtocols okHttpProtocols) {
        Logger.d(AppModule.DI_TAG, "ProxiedOkHTTP client");
        return new RealProxiedOkHttpClient(okHttpDns, okHttpProtocols);
    }

    /**
     * This okHttpClient is for images/file/apk updates/ downloading, prefetching, etc.
     */
    @Provides
    @Singleton
    @Named(DOWNLOADER_OKHTTP_CLIENT_NAME)
    public OkHttpClient provideOkHttpClient(Dns okHttpDns, Chan.OkHttpProtocols okHttpProtocols) {
        Logger.d(AppModule.DI_TAG, "DownloaderOkHttp client");

        return new OkHttpClient.Builder()
                .readTimeout(5, SECONDS)
                .writeTimeout(5, SECONDS)
                .protocols(okHttpProtocols.getProtocols())
                .dns(okHttpDns)
                .build();
    }
}
