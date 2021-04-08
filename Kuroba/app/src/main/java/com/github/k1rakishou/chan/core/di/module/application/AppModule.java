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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Environment;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.core.AppDependenciesInitializer;
import com.github.k1rakishou.chan.core.base.okhttp.CoilOkHttpClient;
import com.github.k1rakishou.chan.core.cache.CacheHandler;
import com.github.k1rakishou.chan.core.diagnostics.AnrSupervisor;
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
import com.github.k1rakishou.chan.core.manager.watcher.BookmarkWatcherCoordinator;
import com.github.k1rakishou.chan.core.manager.watcher.FilterWatcherCoordinator;
import com.github.k1rakishou.chan.core.site.SiteResolver;
import com.github.k1rakishou.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder;
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2;
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2ServiceDelegate;
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.fsaf.FileChooser;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository;
import com.google.gson.Gson;

import java.io.File;

import javax.inject.Singleton;

import coil.ImageLoader;
import coil.request.CachePolicy;
import dagger.Module;
import dagger.Provides;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.LazyThreadSafetyMode;
import kotlinx.coroutines.CoroutineScope;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getAvailableSpaceInBytes;
import static com.github.k1rakishou.common.AndroidUtils.getAppContext;

@Module
public class AppModule {
    public static final String DI_TAG = "Dependency Injection";

    public static Lazy<File> getCacheDir() {
        return LazyKt.lazy(LazyThreadSafetyMode.SYNCHRONIZED, () -> {
            File cacheDir;
            File externalStorage = getAppContext().getExternalCacheDir();

            // See also res/xml/filepaths.xml for the fileprovider.
            if (isExternalStorageOk(externalStorage)) {
                cacheDir = externalStorage;
            } else {
                cacheDir = getAppContext().getCacheDir();
            }

            long spaceInBytes = getAvailableSpaceInBytes(cacheDir);
            Logger.d(DI_TAG, "Available space for cache dir: " + spaceInBytes +
                    " bytes, cacheDirPath = " + cacheDir.getAbsolutePath());

            return cacheDir;
        });
    }

    @Provides
    @Singleton
    public AppDependenciesInitializer provideAppDependenciesInitializer(
            SiteManager siteManager,
            BoardManager boardManager,
            BookmarksManager bookmarksManager,
            HistoryNavigationManager historyNavigationManager,
            BookmarkWatcherCoordinator bookmarkWatcherCoordinator,
            FilterWatcherCoordinator filterWatcherCoordinator,
            ArchivesManager archivesManager,
            ChanFilterManager chanFilterManager,
            ThreadBookmarkGroupManager threadBookmarkGroupManager
    ) {
        return new AppDependenciesInitializer(
                siteManager,
                boardManager,
                bookmarksManager,
                historyNavigationManager,
                bookmarkWatcherCoordinator,
                filterWatcherCoordinator,
                archivesManager,
                chanFilterManager,
                threadBookmarkGroupManager
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
        return new ImageLoader.Builder(applicationContext)
                .allowHardware(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                // Coil's caching system relies on OkHttp's caching system which is not suitable for
                // us (It doesn't handle 404 responses how we want). So we have to use our own disk
                // caching system.
                .diskCachePolicy(CachePolicy.DISABLED)
                .callFactory(coilOkHttpClient.okHttpClient())
                .build();
    }

    @Provides
    @Singleton
    public ImageLoaderV2 provideImageLoaderV2(
            CoroutineScope appScope,
            ImageLoader coilImageLoader,
            ReplyManager replyManager,
            ThemeEngine themeEngine,
            AppConstants appConstants,
            CacheHandler cacheHandler,
            FileManager fileManager,
            SiteResolver siteResolver,
            CoilOkHttpClient coilOkHttpClient
    ) {
        return new ImageLoaderV2(
                ChanSettings.verboseLogs.get(),
                appScope,
                appConstants,
                coilImageLoader,
                replyManager,
                themeEngine,
                cacheHandler,
                fileManager,
                siteResolver,
                coilOkHttpClient
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
        return new CaptchaHolder();
    }

    @Provides
    @Singleton
    public FileChooser provideFileChooser(Context appContext) {
        return new FileChooser(appContext);
    }

    @Provides
    @Singleton
    public Android10GesturesExclusionZonesHolder provideAndroid10GesturesHolder(
            Gson gson
    ) {
        return new Android10GesturesExclusionZonesHolder(gson);
    }

    @Provides
    @Singleton
    public AnrSupervisor provideAnrSupervisor(ReportManager reportManager) {
        return new AnrSupervisor(reportManager);
    }

    private static boolean isExternalStorageOk(File externalStorage) {
        return externalStorage != null
                && !Environment.isExternalStorageRemovable(externalStorage)
                && Environment.getExternalStorageState(externalStorage).equals(Environment.MEDIA_MOUNTED)
                && hasExternalStoragePermission();
    }

    private static boolean hasExternalStoragePermission() {
        int perm = getAppContext().checkCallingOrSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return perm == PackageManager.PERMISSION_GRANTED;
    }
}
