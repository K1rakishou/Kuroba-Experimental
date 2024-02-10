package com.github.k1rakishou.chan.core.di.module.activity;

import androidx.appcompat.app.AppCompatActivity;

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.cache.CacheHandler;
import com.github.k1rakishou.chan.core.di.scope.PerActivity;
import com.github.k1rakishou.chan.core.helper.DialogFactory;
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper;
import com.github.k1rakishou.chan.core.helper.ThumbnailLongtapOptionsHelper;
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.BookmarksManager;
import com.github.k1rakishou.chan.core.manager.BottomNavBarVisibilityStateManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager;
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager;
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
import com.github.k1rakishou.core_logger.Logger;
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
            Lazy<CacheHandler> cacheHandler,
            Lazy<FileManager> fileManager,
            SettingsNotificationManager settingsNotificationManager,
            Lazy<FileChooser> fileChooser,
            Lazy<RealProxiedOkHttpClient> proxiedOkHttpClient,
            Lazy<DialogFactory> dialogFactory
    ) {
        Logger.deps("UpdateManager");
        return new UpdateManager(
                activity,
                cacheHandler,
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
        Logger.deps("RuntimePermissionsHelper");
        return new RuntimePermissionsHelper(
                activity,
                dialogFactory
        );
    }

    @PerActivity
    @Provides
    public StartActivityStartupHandlerHelper provideStartActivityStartupHandlerHelper(
            Lazy<HistoryNavigationManager> historyNavigationManager,
            Lazy<SiteManager> siteManager,
            Lazy<BoardManager> boardManager,
            Lazy<BookmarksManager> bookmarksManager,
            Lazy<ChanThreadViewableInfoManager> chanThreadViewableInfoManager,
            Lazy<SiteResolver> siteResolver,
            Lazy<CompositeCatalogManager> compositeCatalogManager
    ) {
        Logger.deps("StartActivityStartupHandlerHelper");
        return new StartActivityStartupHandlerHelper(
                historyNavigationManager,
                siteManager,
                boardManager,
                bookmarksManager,
                chanThreadViewableInfoManager,
                siteResolver,
                compositeCatalogManager
        );
    }

    @PerActivity
    @Provides
    public GlobalWindowInsetsManager provideGlobalWindowInsetsManager() {
        Logger.deps("GlobalWindowInsetsManager");
        return new GlobalWindowInsetsManager();
    }

    @PerActivity
    @Provides
    public ControllerNavigationManager provideControllerNavigationManager() {
        Logger.deps("ControllerNavigationManager");
        return new ControllerNavigationManager();
    }

    @PerActivity
    @Provides
    public ThreadFollowHistoryManager provideThreadFollowHistoryManager() {
        Logger.deps("ThreadFollowHistoryManager");
        return new ThreadFollowHistoryManager();
    }

    @PerActivity
    @Provides
    public BottomNavBarVisibilityStateManager provideReplyViewStateManager() {
        Logger.deps("BottomNavBarVisibilityStateManager");
        return new BottomNavBarVisibilityStateManager();
    }

    @PerActivity
    @Provides
    public DialogFactory provideDialogFactory(
            Lazy<ApplicationVisibilityManager> applicationVisibilityManager,
            Lazy<ThemeEngine> themeEngine
    ) {
        Logger.deps("DialogFactory");
        return new DialogFactory(
                applicationVisibilityManager,
                themeEngine
        );
    }

    @PerActivity
    @Provides
    public GlobalViewStateManager provideGlobalViewStateManager() {
        Logger.deps("GlobalViewStateManager");
        return new GlobalViewStateManager();
    }

    @PerActivity
    @Provides
    public ThumbnailLongtapOptionsHelper provideThumbnailLongtapOptionsHelper(
            GlobalWindowInsetsManager globalWindowInsetsManager,
            Lazy<ImageSaverV2> imageSaverV2
    ) {
        Logger.deps("ThumbnailLongtapOptionsHelper");
        return new ThumbnailLongtapOptionsHelper(
                globalWindowInsetsManager,
                imageSaverV2
        );
    }

    @PerActivity
    @Provides
    public FileChooser provideFileChooser(AppCompatActivity activity) {
        Logger.deps("FileChooser");
        return new FileChooser(activity);
    }

    @PerActivity
    @Provides
    public PostHighlightManager providePostHighlightManager(
            CurrentOpenedDescriptorStateManager currentOpenedDescriptorStateManager
    ) {
        Logger.deps("PostHighlightManager");
        return new PostHighlightManager(currentOpenedDescriptorStateManager);
    }

}
