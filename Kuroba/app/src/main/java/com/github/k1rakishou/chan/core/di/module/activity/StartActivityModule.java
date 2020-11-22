package com.github.k1rakishou.chan.core.di.module.activity;

import android.content.Context;

import com.github.k1rakishou.chan.StartActivity;
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.cache.FileCacheV2;
import com.github.k1rakishou.chan.core.di.scope.PerActivity;
import com.github.k1rakishou.chan.core.helper.DialogFactory;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.manager.UpdateManager;
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper;
import com.github.k1rakishou.fsaf.FileChooser;
import com.github.k1rakishou.fsaf.FileManager;

import dagger.Module;
import dagger.Provides;

@Module
public class StartActivityModule {

    @Provides
    @PerActivity
    public UpdateManager provideUpdateManager(
            Context appContext,
            FileCacheV2 fileCacheV2,
            FileManager fileManager,
            SettingsNotificationManager settingsNotificationManager,
            FileChooser fileChooser,
            ProxiedOkHttpClient proxiedOkHttpClient,
            DialogFactory dialogFactory,
            SiteManager siteManager,
            BoardManager boardManager
    ) {
        return new UpdateManager(
                appContext,
                fileCacheV2,
                fileManager,
                settingsNotificationManager,
                fileChooser,
                proxiedOkHttpClient,
                dialogFactory,
                siteManager,
                boardManager
        );
    }

    @Provides
    @PerActivity
    public RuntimePermissionsHelper provideRuntimePermissionHelper(
            StartActivity activity,
            DialogFactory dialogFactory
    ) {
        return new RuntimePermissionsHelper(
                activity,
                dialogFactory
        );
    }
}
