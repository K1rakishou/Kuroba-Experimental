package com.github.k1rakishou.chan.core.di.module.activity;

import android.content.Context;

import com.github.k1rakishou.chan.StartActivity;
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.cache.FileCacheV2;
import com.github.k1rakishou.chan.core.di.module.application.AppModule;
import com.github.k1rakishou.chan.core.di.scope.PerActivity;
import com.github.k1rakishou.chan.core.helper.DialogFactory;
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.BottomNavBarVisibilityStateManager;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.manager.LocalSearchManager;
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.manager.UpdateManager;
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_themes.ThemeEngine;
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

    @Provides
    @PerActivity
    public BottomNavBarVisibilityStateManager provideReplyViewStateManager() {
        Logger.d(AppModule.DI_TAG, "ReplyViewStateManager");

        return new BottomNavBarVisibilityStateManager();
    }

    @Provides
    @PerActivity
    public GlobalWindowInsetsManager provideGlobalWindowInsetsManager() {
        Logger.d(AppModule.DI_TAG, "GlobalWindowInsetsManager");

        return new GlobalWindowInsetsManager();
    }

    @Provides
    @PerActivity
    public LocalSearchManager provideLocalSearchManager() {
        Logger.d(AppModule.DI_TAG, "LocalSearchManager");

        return new LocalSearchManager();
    }

    @Provides
    @PerActivity
    public DialogFactory provideDialogFactory(
            ApplicationVisibilityManager applicationVisibilityManager,
            ThemeEngine themeEngine
    ) {
        Logger.d(AppModule.DI_TAG, "DialogFactory");

        return new DialogFactory(
                applicationVisibilityManager,
                themeEngine
        );
    }

}
