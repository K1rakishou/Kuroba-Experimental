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
import com.github.k1rakishou.chan.core.base.okhttp.CoilOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.HttpLoggingInterceptorLazy;
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.RealDownloaderOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.cache.CacheHandler;
import com.github.k1rakishou.chan.core.cache.downloader.ChunkedMediaDownloader;
import com.github.k1rakishou.chan.core.cache.downloader.ChunkedMediaDownloaderImpl;
import com.github.k1rakishou.chan.core.helper.ProxyStorage;
import com.github.k1rakishou.chan.core.manager.FirewallBypassManager;
import com.github.k1rakishou.chan.core.site.SiteResolver;
import com.github.k1rakishou.chan.core.site.http.HttpCallManager;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.common.dns.DnsOverHttpsSelectorFactory;
import com.github.k1rakishou.common.dns.NormalDnsSelectorFactory;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.google.gson.Gson;

import javax.inject.Singleton;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import kotlinx.coroutines.CoroutineScope;

@Module
public class NetModule {

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
        Logger.deps("ProxyStorage");
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
    public CacheHandler provideCacheHandler(AppConstants appConstants) {
        Logger.deps("CacheHandler");

        return new CacheHandler(
                ChanSettings.prefetchMedia.get(),
                appConstants
        );
    }

    @Provides
    @Singleton
    public ChunkedMediaDownloader provideChunkedMediaDownloaderImpl(
            AppConstants appConstants,
            FileManager fileManager,
            SiteResolver siteResolver,
            Lazy<CacheHandler> cacheHandler,
            Lazy<RealDownloaderOkHttpClient> realDownloaderOkHttpClient,
            ConnectivityManager connectivityManager
    ) {
        Logger.deps("ChunkedMediaDownloader");

        return new ChunkedMediaDownloaderImpl(
                appConstants,
                fileManager,
                siteResolver,
                cacheHandler,
                realDownloaderOkHttpClient,
                connectivityManager
        );
    }

    @Provides
    @Singleton
    public HttpCallManager provideHttpCallManager(
            Lazy<ProxiedOkHttpClient> okHttpClient,
            AppConstants appConstants
    ) {
        Logger.deps("HttpCallManager");

        return new HttpCallManager(okHttpClient, appConstants);
    }

    /**
     * This okHttpClient is for posting.
     */
    @Provides
    @Singleton
    public ProxiedOkHttpClient provideProxiedOkHttpClient(
            NormalDnsSelectorFactory normalDnsSelectorFactory,
            DnsOverHttpsSelectorFactory dnsOverHttpsSelectorFactory,
            ProxyStorage proxyStorage,
            HttpLoggingInterceptorLazy httpLoggingInterceptorLazy,
            SiteResolver siteResolver,
            FirewallBypassManager firewallBypassManager
    ) {
        Logger.deps("RealProxiedOkHttpClient");

        return new RealProxiedOkHttpClient(
                normalDnsSelectorFactory,
                dnsOverHttpsSelectorFactory,
                proxyStorage,
                httpLoggingInterceptorLazy,
                siteResolver,
                firewallBypassManager
        );
    }

    /**
     * This okHttpClient is for Coil image loading library
     */
    @Provides
    @Singleton
    public CoilOkHttpClient provideCoilOkHttpClient(
            Context applicationContext,
            NormalDnsSelectorFactory normalDnsSelectorFactory,
            DnsOverHttpsSelectorFactory dnsOverHttpsSelectorFactory,
            ProxyStorage proxyStorage,
            HttpLoggingInterceptorLazy httpLoggingInterceptorLazy,
            SiteResolver siteResolver,
            FirewallBypassManager firewallBypassManager
    ) {
        Logger.deps("CoilOkHttpClient");

        return new CoilOkHttpClient(
                applicationContext,
                normalDnsSelectorFactory,
                dnsOverHttpsSelectorFactory,
                proxyStorage,
                httpLoggingInterceptorLazy,
                siteResolver,
                firewallBypassManager
        );
    }

    /**
     * This okHttpClient is for images/file/apk updates/ downloading, prefetching, etc.
     */
    @Provides
    @Singleton
    public RealDownloaderOkHttpClient provideDownloaderOkHttpClient(
            NormalDnsSelectorFactory normalDnsSelectorFactory,
            DnsOverHttpsSelectorFactory dnsOverHttpsSelectorFactory,
            ProxyStorage proxyStorage,
            HttpLoggingInterceptorLazy httpLoggingInterceptorLazy,
            SiteResolver siteResolver,
            FirewallBypassManager firewallBypassManager
    ) {
        Logger.deps("RealDownloaderOkHttpClient");

        return new RealDownloaderOkHttpClient(
                normalDnsSelectorFactory,
                dnsOverHttpsSelectorFactory,
                proxyStorage,
                httpLoggingInterceptorLazy,
                siteResolver,
                firewallBypassManager
        );
    }
}
