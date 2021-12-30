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
import com.github.k1rakishou.chan.core.AppDependenciesInitializer;
import com.github.k1rakishou.chan.core.base.okhttp.CoilOkHttpClient;
import com.github.k1rakishou.chan.core.cache.CacheHandler;
import com.github.k1rakishou.chan.core.cache.FileCacheV2;
import com.github.k1rakishou.chan.core.diagnostics.AnrSupervisor;
import com.github.k1rakishou.chan.core.helper.ImageLoaderFileManagerWrapper;
import com.github.k1rakishou.chan.core.image.ImageLoaderV2;
import com.github.k1rakishou.chan.core.manager.ArchivesManager;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.BookmarksManager;
import com.github.k1rakishou.chan.core.manager.ChanFilterManager;
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager;
import com.github.k1rakishou.chan.core.manager.ReplyManager;
import com.github.k1rakishou.chan.core.manager.ReportManager;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager;
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager;
import com.github.k1rakishou.chan.core.manager.watcher.BookmarkWatcherCoordinator;
import com.github.k1rakishou.chan.core.manager.watcher.FilterWatcherCoordinator;
import com.github.k1rakishou.chan.core.site.SiteResolver;
import com.github.k1rakishou.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder;
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2;
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2ServiceDelegate;
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingCoordinator;
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository;
import com.google.gson.Gson;

import javax.inject.Singleton;

import coil.ImageLoader;
import coil.request.CachePolicy;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import kotlinx.coroutines.CoroutineScope;

@Module
public class AppModule {

    @Provides
    @Singleton
    public AppDependenciesInitializer provideAppDependenciesInitializer(
            SiteManager siteManager,
            BoardManager boardManager,
            BookmarksManager bookmarksManager,
            ThreadBookmarkGroupManager threadBookmarkGroupManager,
            HistoryNavigationManager historyNavigationManager,
            BookmarkWatcherCoordinator bookmarkWatcherCoordinator,
            FilterWatcherCoordinator filterWatcherCoordinator,
            ArchivesManager archivesManager,
            ChanFilterManager chanFilterManager,
            ThreadDownloadingCoordinator threadDownloadingCoordinator
    ) {
        Logger.deps("AppDependenciesInitializer");

        return new AppDependenciesInitializer(
                siteManager,
                boardManager,
                bookmarksManager,
                threadBookmarkGroupManager,
                historyNavigationManager,
                bookmarkWatcherCoordinator,
                filterWatcherCoordinator,
                archivesManager,
                chanFilterManager,
                threadDownloadingCoordinator
        );
    }

    @Provides
    @Singleton
    public ConnectivityManager provideConnectivityManager(Context appContext) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            throw new NullPointerException("What's working in this ROM: You tell me ;) "
                    + "\nWhat doesn't work: Connectivity fucking manager");
        }

        return connectivityManager;
    }

    @Provides
    @Singleton
    public ImageLoader provideCoilImageLoader(
            Context applicationContext,
            CoilOkHttpClient coilOkHttpClient
    ) {
        boolean isLowRamDevice = ChanSettings.isLowRamDevice();
        boolean allowHardware = !isLowRamDevice;
        boolean allowRgb565 = isLowRamDevice;
        double availableMemoryPercentage = getDefaultAvailableMemoryPercentage();
        Logger.deps("ImageLoader(), availableMemoryPercentage=" + availableMemoryPercentage +
                ", isLowRamDevice=" + isLowRamDevice + ", allowHardware=" + allowHardware +
                ", allowRgb565=" + allowRgb565);

        return new ImageLoader.Builder(applicationContext)
                .allowHardware(allowHardware)
                .allowRgb565(allowRgb565)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                // Coil's caching system relies on OkHttp's caching system which is not suitable for
                // us (It doesn't handle 404 responses how we want). So we have to use our own disk
                // caching system.
                .diskCachePolicy(CachePolicy.DISABLED)
                .callFactory(coilOkHttpClient.okHttpClient())
                .availableMemoryPercentage(availableMemoryPercentage)
                .build();
    }

    private double getDefaultAvailableMemoryPercentage() {
        double defaultMemoryPercentage = 0.1;

        if (ChanSettings.isLowRamDevice()) {
            defaultMemoryPercentage = defaultMemoryPercentage / 2.0;
        }

        return defaultMemoryPercentage;
    }

    @Provides
    @Singleton
    public ImageLoaderV2 provideImageLoaderV2(
            CoroutineScope appScope,
            Lazy<ImageLoader> coilImageLoader,
            Lazy<ReplyManager> replyManager,
            Lazy<ThemeEngine> themeEngine,
            Lazy<CacheHandler> cacheHandler,
            Lazy<FileCacheV2> fileCacheV2,
            Lazy<ImageLoaderFileManagerWrapper> imageLoaderFileManagerWrapper,
            Lazy<SiteResolver> siteResolver,
            Lazy<CoilOkHttpClient> coilOkHttpClient,
            Lazy<ThreadDownloadManager> threadDownloadManager
    ) {
        Logger.deps("ImageLoaderV2");

        return new ImageLoaderV2(
                ChanSettings.verboseLogs.get(),
                appScope,
                coilImageLoader,
                replyManager,
                themeEngine,
                cacheHandler,
                fileCacheV2,
                imageLoaderFileManagerWrapper,
                siteResolver,
                coilOkHttpClient,
                threadDownloadManager
        );
    }

    @Provides
    @Singleton
    public ImageSaverV2 provideImageSaverV2(
            Context appContext,
            CoroutineScope appScope,
            Gson gson,
            FileManager fileManager,
            ImageDownloadRequestRepository imageDownloadRequestRepository,
            ImageSaverV2ServiceDelegate imageSaverV2ServiceDelegate
    ) {
        Logger.deps("ImageSaverV2");

        return new ImageSaverV2(
                ChanSettings.verboseLogs.get(),
                appContext,
                appScope,
                gson,
                fileManager,
                imageDownloadRequestRepository,
                imageSaverV2ServiceDelegate
        );
    }

    @Provides
    @Singleton
    public CaptchaHolder provideCaptchaHolder() {
        Logger.deps("CaptchaHolder");

        return new CaptchaHolder();
    }

    @Provides
    @Singleton
    public Android10GesturesExclusionZonesHolder provideAndroid10GesturesHolder(
            Gson gson
    ) {
        Logger.deps("Android10GesturesExclusionZonesHolder");

        return new Android10GesturesExclusionZonesHolder(gson);
    }

    @Provides
    @Singleton
    public AnrSupervisor provideAnrSupervisor(dagger.Lazy<ReportManager> reportManager) {
        Logger.deps("AnrSupervisor");

        return new AnrSupervisor(reportManager);
    }
}
