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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Environment;

import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.chan.core.image.ImageLoaderV2;
import com.github.k1rakishou.chan.core.saver.ImageSaver;
import com.github.k1rakishou.chan.core.settings.ChanSettings;
import com.github.k1rakishou.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder;
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder;
import com.github.k1rakishou.chan.ui.theme.ThemeHelper;
import com.github.k1rakishou.chan.ui.theme_v2.MockChanThemeV2;
import com.github.k1rakishou.chan.ui.theme_v2.ThemeEngine;
import com.github.k1rakishou.chan.utils.AndroidUtils;
import com.github.k1rakishou.chan.utils.Logger;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.feather2.Provides;
import com.github.k1rakishou.fsaf.FileChooser;
import com.github.k1rakishou.fsaf.FileManager;
import com.google.gson.Gson;

import java.io.File;
import java.util.Objects;

import javax.inject.Singleton;

import coil.ImageLoader;
import coil.request.CachePolicy;
import kotlinx.coroutines.CoroutineScope;
import okhttp3.Cache;
import okhttp3.Dns;
import okhttp3.OkHttpClient;

import static com.github.k1rakishou.chan.utils.AndroidUtils.getAppContext;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getMaxScreenSize;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getMinScreenSize;

public class AppModule {
    public static final String DI_TAG = "Dependency Injection";
    private static final String IMAGE_CACHE_DIR = "coil_image_cache_dir";
    private static final long ONE_MB = 1024  * 1024;
    private static final long IMAGE_CACHE_MAX_SIZE = 160 * ONE_MB;

    private final Context applicationContext;
    private final CoroutineScope applicationCoroutineScope;
    private final Dns okHttpDns;
    private final Chan.OkHttpProtocols okHttpProtocols;
    private final AppConstants appConstants;

    public AppModule(
            Context applicationContext,
            CoroutineScope applicationCoroutineScope,
            Dns dns,
            Chan.OkHttpProtocols protocols,
            AppConstants appConstants
    ) {
        Objects.requireNonNull(applicationContext);
        Objects.requireNonNull(applicationCoroutineScope);
        Objects.requireNonNull(dns);
        Objects.requireNonNull(protocols);
        Objects.requireNonNull(appConstants);

        this.applicationContext = applicationContext;
        this.applicationCoroutineScope = applicationCoroutineScope;
        this.okHttpDns = dns;
        this.okHttpProtocols = protocols;
        this.appConstants = appConstants;
    }

    public static File getCacheDir() {
        File cacheDir;
        File externalStorage = getAppContext().getExternalCacheDir();

        // See also res/xml/filepaths.xml for the fileprovider.
        if (isExternalStorageOk(externalStorage)) {
            cacheDir = externalStorage;
        } else {
            cacheDir = getAppContext().getCacheDir();
        }

        long spaceInBytes = AndroidUtils.getAvailableSpaceInBytes(cacheDir);
        Logger.d(DI_TAG, "Available space for cache dir: " + spaceInBytes +
                " bytes, cacheDirPath = " + cacheDir.getAbsolutePath());

        return cacheDir;
    }

    @Provides
    @Singleton
    public Context provideApplicationContext() {
        Logger.d(DI_TAG, "App Context");
        return applicationContext;
    }

    @Provides
    @Singleton
    public CoroutineScope proviceApplicationCoroutineScope() {
        Logger.d(DI_TAG, "App CoroutineScope");
        return applicationCoroutineScope;
    }

    @Provides
    @Singleton
    public Dns provideOkHttpDns() {
        return okHttpDns;
    }

    @Provides
    @Singleton
    public Chan.OkHttpProtocols provideOkHttpProtocols() {
        return okHttpProtocols;
    }

    @Provides
    @Singleton
    public AppConstants provideAppConstants() {
        return appConstants;
    }

    @Provides
    @Singleton
    public ConnectivityManager provideConnectivityManager() {
        Logger.d(DI_TAG, "Connectivity Manager");

        ConnectivityManager connectivityManager =
                (ConnectivityManager) applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            throw new NullPointerException("What's working in this ROM: You tell me ;) "
                    + "\nWhat doesn't work: Connectivity fucking manager");
        }

        return connectivityManager;
    }

    @Provides
    @Singleton
    public ImageLoader provideCoilImageLoader(Context applicationContext) {
        Logger.d(DI_TAG, "Coil Image loader");

        File imageCacheDir = new File(applicationContext.getCacheDir(), IMAGE_CACHE_DIR);
        if (!imageCacheDir.exists() && !imageCacheDir.mkdirs()) {
            throw new IllegalStateException("mkdirs failed to create " + imageCacheDir.getAbsolutePath());
        }

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .cache(new Cache(imageCacheDir, IMAGE_CACHE_MAX_SIZE))
                .build();

        return new ImageLoader.Builder(applicationContext)
                .allowHardware(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .callFactory(okHttpClient)
                .build();
    }

    @Provides
    @Singleton
    public ImageLoaderV2 provideImageLoaderV2(ImageLoader coilImageLoader, ThemeHelper themeHelper) {
        Logger.d(DI_TAG, "Image loader v2");
        return new ImageLoaderV2(
                coilImageLoader,
                ChanSettings.verboseLogs.get(),
                themeHelper
        );
    }

    @Provides
    @Singleton
    public ThemeHelper provideThemeHelper() {
        Logger.d(DI_TAG, "Theme helper");
        return new ThemeHelper();
    }

    @Provides
    @Singleton
    public ThemeEngine provideThemeEngine() {
        Logger.d(DI_TAG, "Theme helper");

        MockChanThemeV2 mockChanThemeV2 = new MockChanThemeV2(
                "Test theme",
                false,
                0xFF03DAC6,
                0xFF121212,
                Color.RED,
                Color.BLUE,
                Color.GRAY,
                Color.WHITE
        );

        return new ThemeEngine(mockChanThemeV2);
    }

    @Provides
    @Singleton
    public ImageSaver provideImageSaver(FileManager fileManager) {
        Logger.d(DI_TAG, "Image saver");
        return new ImageSaver(fileManager);
    }

    @Provides
    @Singleton
    public CaptchaHolder provideCaptchaHolder() {
        Logger.d(DI_TAG, "Captcha holder");
        return new CaptchaHolder();
    }

    @Provides
    @Singleton
    public FileChooser provideFileChooser() {
        return new FileChooser(applicationContext);
    }

    @Provides
    @Singleton
    public Android10GesturesExclusionZonesHolder provideAndroid10GesturesHolder(Gson gson) {
        Logger.d(DI_TAG, "Android10GesturesExclusionZonesHolder");

        return new Android10GesturesExclusionZonesHolder(gson, getMinScreenSize(), getMaxScreenSize());
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
