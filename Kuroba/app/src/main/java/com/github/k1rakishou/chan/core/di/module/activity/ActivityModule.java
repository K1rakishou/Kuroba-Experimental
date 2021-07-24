package com.github.k1rakishou.chan.core.di.module.activity;

import androidx.appcompat.app.AppCompatActivity;

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.cache.CacheHandler;
import com.github.k1rakishou.chan.core.cache.FileCacheV2;
import com.github.k1rakishou.chan.core.di.scope.PerActivity;
import com.github.k1rakishou.chan.core.helper.DialogFactory;
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper;
import com.github.k1rakishou.chan.core.helper.ThumbnailLongtapOptionsHelper;
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.BookmarksManager;
import com.github.k1rakishou.chan.core.manager.BottomNavBarVisibilityStateManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager;
import com.github.k1rakishou.chan.core.manager.ControllerNavigationManager;
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager;
import com.github.k1rakishou.chan.core.manager.GlobalViewStateManager;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager;
import com.github.k1rakishou.chan.core.manager.PostHighlightManager;
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.manager.ThreadFollowHistoryManager;
import com.github.k1rakishou.chan.core.manager.UpdateManager;
import com.github.k1rakishou.chan.core.site.SiteResolver;
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2;
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.fsaf.FileChooser;
import com.github.k1rakishou.fsaf.FileManager;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

@Module
public class ActivityModule {

    @PerActivity
    @Provides
    public UpdateManager provideUpdateManager(
            AppCompatActivity activity,
            Lazy<FileCacheV2> fileCacheV2,
            Lazy<CacheHandler> cacheHandler,
            Lazy<FileManager> fileManager,
            SettingsNotificationManager settingsNotificationManager,
            Lazy<FileChooser> fileChooser,
            Lazy<RealProxiedOkHttpClient> proxiedOkHttpClient,
            Lazy<DialogFactory> dialogFactory
    ) {
        return new UpdateManager(
                activity,
                cacheHandler,
                fileCacheV2,
                fileManager,
                settingsNotificationManager,
                fileChooser,
                proxiedOkHttpClient,
                dialogFactory
        );
    }

    @PerActivity
    @Provides
    public RuntimePermissionsHelper provideRuntimePermissionHelper(
            AppCompatActivity activity,
            DialogFactory dialogFactory
    ) {
        return new RuntimePermissionsHelper(
                activity,
                dialogFactory
        );
    }

    @PerActivity
    @Provides
    public StartActivityStartupHandlerHelper provideStartActivityStartupHandlerHelper(
            HistoryNavigationManager historyNavigationManager,
            SiteManager siteManager,
            BoardManager boardManager,
            Lazy<BookmarksManager> bookmarksManager,
            Lazy<ChanThreadViewableInfoManager> chanThreadViewableInfoManager,
            Lazy<SiteResolver> siteResolver
    ) {
        return new StartActivityStartupHandlerHelper(
                historyNavigationManager,
                siteManager,
                boardManager,
                bookmarksManager,
                chanThreadViewableInfoManager,
                siteResolver
        );
    }

    @PerActivity
    @Provides
    public GlobalWindowInsetsManager provideGlobalWindowInsetsManager() {
        return new GlobalWindowInsetsManager();
    }

    @PerActivity
    @Provides
    public ControllerNavigationManager provideControllerNavigationManager() {
        return new ControllerNavigationManager();
    }

    @PerActivity
    @Provides
    public ThreadFollowHistoryManager provideThreadFollowHistoryManager() {
        return new ThreadFollowHistoryManager();
    }

    @PerActivity
    @Provides
    public BottomNavBarVisibilityStateManager provideReplyViewStateManager() {
        return new BottomNavBarVisibilityStateManager();
    }

    @PerActivity
    @Provides
    public DialogFactory provideDialogFactory(
            ApplicationVisibilityManager applicationVisibilityManager,
            ThemeEngine themeEngine
    ) {
        return new DialogFactory(
                applicationVisibilityManager,
                themeEngine
        );
    }

    @PerActivity
    @Provides
    public GlobalViewStateManager provideViewFlagsStorage() {
        return new GlobalViewStateManager();
    }

    @PerActivity
    @Provides
    public ThumbnailLongtapOptionsHelper provideThumbnailLongtapOptionsHelper(
            GlobalWindowInsetsManager globalWindowInsetsManager,
            Lazy<ImageSaverV2> imageSaverV2
    ) {
        return new ThumbnailLongtapOptionsHelper(
                globalWindowInsetsManager,
                imageSaverV2
        );
    }

    @PerActivity
    @Provides
    public FileChooser provideFileChooser(AppCompatActivity activity) {
        return new FileChooser(activity);
    }

    @PerActivity
    @Provides
    public PostHighlightManager providePostHighlightManager(
            CurrentOpenedDescriptorStateManager currentOpenedDescriptorStateManager
    ) {
        return new PostHighlightManager(currentOpenedDescriptorStateManager);
    }

}
