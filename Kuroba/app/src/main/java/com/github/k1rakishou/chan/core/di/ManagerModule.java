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

import android.content.Context;

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.image.ImageLoaderV2;
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader;
import com.github.k1rakishou.chan.core.loader.impl.InlinedFileInfoLoader;
import com.github.k1rakishou.chan.core.loader.impl.PostExtraContentLoader;
import com.github.k1rakishou.chan.core.loader.impl.PrefetchLoader;
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager;
import com.github.k1rakishou.chan.core.manager.ArchivesManager;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.BookmarksManager;
import com.github.k1rakishou.chan.core.manager.BottomNavBarVisibilityStateManager;
import com.github.k1rakishou.chan.core.manager.ChanFilterManager;
import com.github.k1rakishou.chan.core.manager.ChanLoaderManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager;
import com.github.k1rakishou.chan.core.manager.ControllerNavigationManager;
import com.github.k1rakishou.chan.core.manager.FilterEngine;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager;
import com.github.k1rakishou.chan.core.manager.LastPageNotificationsHelper;
import com.github.k1rakishou.chan.core.manager.LastViewedPostNoInfoHolder;
import com.github.k1rakishou.chan.core.manager.LocalSearchManager;
import com.github.k1rakishou.chan.core.manager.OnDemandContentLoaderManager;
import com.github.k1rakishou.chan.core.manager.PageRequestManager;
import com.github.k1rakishou.chan.core.manager.PostFilterManager;
import com.github.k1rakishou.chan.core.manager.PostHideManager;
import com.github.k1rakishou.chan.core.manager.PrefetchImageDownloadIndicatorManager;
import com.github.k1rakishou.chan.core.manager.ReplyManager;
import com.github.k1rakishou.chan.core.manager.ReplyNotificationsHelper;
import com.github.k1rakishou.chan.core.manager.ReportManager;
import com.github.k1rakishou.chan.core.manager.SavedReplyManager;
import com.github.k1rakishou.chan.core.manager.SeenPostsManager;
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.settings.ChanSettings;
import com.github.k1rakishou.chan.core.site.ParserRepository;
import com.github.k1rakishou.chan.core.site.SiteRegistry;
import com.github.k1rakishou.chan.core.site.parser.MockReplyManager;
import com.github.k1rakishou.chan.core.site.parser.ReplyParser;
import com.github.k1rakishou.chan.core.usecase.FetchThreadBookmarkInfoUseCase;
import com.github.k1rakishou.chan.core.usecase.ParsePostRepliesUseCase;
import com.github.k1rakishou.chan.features.bookmarks.watcher.BookmarkForegroundWatcher;
import com.github.k1rakishou.chan.features.bookmarks.watcher.BookmarkWatcherCoordinator;
import com.github.k1rakishou.chan.features.bookmarks.watcher.BookmarkWatcherDelegate;
import com.github.k1rakishou.chan.ui.settings.base_directory.SavedFilesBaseDirectory;
import com.github.k1rakishou.chan.utils.AndroidUtils;
import com.github.k1rakishou.chan.utils.Logger;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.feather2.Provides;
import com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.manager.base_directory.DirectoryManager;
import com.github.k1rakishou.model.repository.BoardRepository;
import com.github.k1rakishou.model.repository.BookmarksRepository;
import com.github.k1rakishou.model.repository.ChanFilterRepository;
import com.github.k1rakishou.model.repository.ChanPostHideRepository;
import com.github.k1rakishou.model.repository.ChanPostRepository;
import com.github.k1rakishou.model.repository.ChanSavedReplyRepository;
import com.github.k1rakishou.model.repository.ChanThreadViewableInfoRepository;
import com.github.k1rakishou.model.repository.HistoryNavigationRepository;
import com.github.k1rakishou.model.repository.SeenPostRepository;
import com.github.k1rakishou.model.repository.SiteRepository;
import com.google.gson.Gson;

import java.io.File;
import java.util.HashSet;
import java.util.concurrent.Executor;

import javax.inject.Named;
import javax.inject.Singleton;

import io.reactivex.schedulers.Schedulers;
import kotlinx.coroutines.CoroutineScope;

import static com.github.k1rakishou.chan.core.di.AppModule.getCacheDir;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getFlavorType;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getNotificationManager;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getNotificationManagerCompat;
import static com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy.ReplaceBadSymbols;
import static com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy.ThrowAnException;

public class ManagerModule {
    private static final String CRASH_LOGS_DIR_NAME = "crashlogs";

    @Provides
    @Singleton
    public SiteManager provideSiteManager(CoroutineScope appScope, SiteRepository siteRepository) {
        Logger.d(AppModule.DI_TAG, "Site manager");

        return new SiteManager(
                appScope,
                AndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
                siteRepository,
                SiteRegistry.INSTANCE
        );
    }

    @Provides
    @Singleton
    public BoardManager provideBoardManager(
            CoroutineScope appScope,
            SiteRepository siteRepository,
            BoardRepository boardRepository
    ) {
        Logger.d(AppModule.DI_TAG, "Board manager");
        return new BoardManager(
                appScope,
                AndroidUtils.isDevBuild(),
                siteRepository,
                boardRepository
        );
    }

    @Provides
    @Singleton
    public FilterEngine provideFilterEngine(ChanFilterManager chanFilterManager) {
        Logger.d(AppModule.DI_TAG, "Filter engine");
        return new FilterEngine(chanFilterManager);
    }

    @Provides
    @Singleton
    public ReplyManager provideReplyManager(Context applicationContext) {
        Logger.d(AppModule.DI_TAG, "Reply manager");
        return new ReplyManager(applicationContext);
    }

    @Provides
    @Singleton
    public ChanLoaderManager provideChanLoaderFactory() {
        Logger.d(AppModule.DI_TAG, "Chan loader factory");
        return new ChanLoaderManager();
    }

    @Provides
    @Singleton
    public PageRequestManager providePageRequestManager(
            SiteManager siteManager,
            BoardManager boardManager
    ) {
        Logger.d(AppModule.DI_TAG, "Page request manager");
        return new PageRequestManager(
                siteManager,
                boardManager
        );
    }

    @Provides
    @Singleton
    public ArchivesManager provideArchivesManager(
            Context appContext,
            Gson gson,
            AppConstants appConstants,
            CoroutineScope appScope
    ) {
        Logger.d(AppModule.DI_TAG, "Archives manager");
        return new ArchivesManager(
                appContext,
                appScope,
                gson,
                appConstants,
                ChanSettings.verboseLogs.get()
        );
    }

    @Provides
    @Singleton
    public MockReplyManager provideMockReplyManager() {
        Logger.d(AppModule.DI_TAG, "Mock reply manager");
        return new MockReplyManager();
    }

    @Provides
    @Singleton
    public ReportManager provideReportManager(
            ProxiedOkHttpClient okHttpClient,
            Gson gson,
            SettingsNotificationManager settingsNotificationManager
    ) {
        Logger.d(AppModule.DI_TAG, "Report manager");
        File cacheDir = getCacheDir();

        return new ReportManager(
                okHttpClient.getProxiedClient(),
                settingsNotificationManager,
                gson,
                new File(cacheDir, CRASH_LOGS_DIR_NAME)
        );
    }

    @Provides
    @Singleton
    public SettingsNotificationManager provideSettingsNotificationManager() {
        return new SettingsNotificationManager();
    }

    @Provides
    @Singleton
    public OnDemandContentLoaderManager provideOnDemandContentLoader(
            PrefetchLoader prefetchLoader,
            PostExtraContentLoader postExtraContentLoader,
            InlinedFileInfoLoader inlinedFileInfoLoader,
            @Named(ExecutorsModule.onDemandContentLoaderExecutorName) Executor onDemandContentLoaderExecutor
    ) {
        Logger.d(AppModule.DI_TAG, "OnDemandContentLoaderManager");

        HashSet<OnDemandContentLoader> loaders = new HashSet<>();
        loaders.add(prefetchLoader);
        loaders.add(postExtraContentLoader);
        loaders.add(inlinedFileInfoLoader);

        return new OnDemandContentLoaderManager(
                Schedulers.from(onDemandContentLoaderExecutor),
                loaders
        );
    }

    @Provides
    @Singleton
    public SeenPostsManager provideSeenPostsManager(
            CoroutineScope appScope,
            SeenPostRepository seenPostRepository
    ) {
        Logger.d(AppModule.DI_TAG, "SeenPostsManager");

        return new SeenPostsManager(
                appScope,
                ChanSettings.verboseLogs.get(),
                seenPostRepository
        );
    }

    @Provides
    @Singleton
    public PrefetchImageDownloadIndicatorManager providePrefetchIndicatorAnimationManager() {
        Logger.d(AppModule.DI_TAG, "PrefetchIndicatorAnimationManager");

        return new PrefetchImageDownloadIndicatorManager();
    }

    @Provides
    @Singleton
    public FileManager provideFileManager(Context applicationContext) {
        DirectoryManager directoryManager = new DirectoryManager(applicationContext);

        // Add new base directories here
        SavedFilesBaseDirectory savedFilesBaseDirectory = new SavedFilesBaseDirectory();

        BadPathSymbolResolutionStrategy resolutionStrategy = ReplaceBadSymbols;
        if (getFlavorType() != AndroidUtils.FlavorType.Stable) {
            resolutionStrategy = ThrowAnException;
        }

        FileManager fileManager = new FileManager(applicationContext, resolutionStrategy, directoryManager);
        fileManager.registerBaseDir(SavedFilesBaseDirectory.class, savedFilesBaseDirectory);

        return fileManager;
    }


    @Provides
    @Singleton
    public GlobalWindowInsetsManager provideGlobalWindowInsetsManager() {
        Logger.d(AppModule.DI_TAG, "GlobalWindowInsetsManager");

        return new GlobalWindowInsetsManager();
    }

    @Provides
    @Singleton
    public ApplicationVisibilityManager provideApplicationVisibilityManager() {
        Logger.d(AppModule.DI_TAG, "ApplicationVisibilityManager");

        return new ApplicationVisibilityManager();
    }

    @Provides
    @Singleton
    public HistoryNavigationManager provideHistoryNavigationManager(
            CoroutineScope appScope,
            HistoryNavigationRepository historyNavigationRepository,
            ApplicationVisibilityManager applicationVisibilityManager
    ) {
        Logger.d(AppModule.DI_TAG, "HistoryNavigationManager");

        return new HistoryNavigationManager(
                appScope,
                historyNavigationRepository,
                applicationVisibilityManager
        );
    }

    @Provides
    @Singleton
    public ControllerNavigationManager provideControllerNavigationManager() {
        Logger.d(AppModule.DI_TAG, "ControllerNavigationManager");

        return new ControllerNavigationManager();
    }

    @Provides
    @Singleton
    public PostFilterManager providePostFilterManager() {
        Logger.d(AppModule.DI_TAG, "PostFilterManager");

        return new PostFilterManager();
    }

    @Provides
    @Singleton
    public BottomNavBarVisibilityStateManager provideReplyViewStateManager() {
        Logger.d(AppModule.DI_TAG, "ReplyViewStateManager");

        return new BottomNavBarVisibilityStateManager();
    }

    @Provides
    @Singleton
    public BookmarksManager provideBookmarksManager(
            CoroutineScope appScope,
            ApplicationVisibilityManager applicationVisibilityManager,
            ArchivesManager archivesManager,
            BookmarksRepository bookmarksRepository
    ) {
        Logger.d(AppModule.DI_TAG, "BookmarksManager");

        return new BookmarksManager(
                AndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
                appScope,
                applicationVisibilityManager,
                archivesManager,
                bookmarksRepository
        );
    }

    @Provides
    @Singleton
    public ReplyParser provideReplyParser(
            SiteManager siteManager,
            ParserRepository parserRepository
    ) {
        Logger.d(AppModule.DI_TAG, "ReplyParser");

        return new ReplyParser(
                siteManager,
                parserRepository
        );
    }

    @Provides
    @Singleton
    public BookmarkWatcherDelegate provideBookmarkWatcherDelegate(
            CoroutineScope appScope,
            BookmarksManager bookmarksManager,
            ArchivesManager archivesManager,
            SiteManager siteManager,
            LastViewedPostNoInfoHolder lastViewedPostNoInfoHolder,
            FetchThreadBookmarkInfoUseCase fetchThreadBookmarkInfoUseCase,
            ParsePostRepliesUseCase parsePostRepliesUseCase,
            ReplyNotificationsHelper replyNotificationsHelper,
            LastPageNotificationsHelper lastPageNotificationsHelper
    ) {
        Logger.d(AppModule.DI_TAG, "BookmarkWatcherDelegate");

        return new BookmarkWatcherDelegate(
                AndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
                appScope,
                bookmarksManager,
                archivesManager,
                siteManager,
                lastViewedPostNoInfoHolder,
                fetchThreadBookmarkInfoUseCase,
                parsePostRepliesUseCase,
                replyNotificationsHelper,
                lastPageNotificationsHelper
        );
    }

    @Provides
    @Singleton
    public BookmarkForegroundWatcher provideBookmarkForegroundWatcher(
            CoroutineScope appScope,
            BookmarksManager bookmarksManager,
            ArchivesManager archivesManager,
            BookmarkWatcherDelegate bookmarkWatcherDelegate
    ) {
        Logger.d(AppModule.DI_TAG, "BookmarkForegroundWatcher");

        return new BookmarkForegroundWatcher(
                AndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
                appScope,
                bookmarksManager,
                archivesManager,
                bookmarkWatcherDelegate
        );
    }

    @Provides
    @Singleton
    public BookmarkWatcherCoordinator provideBookmarkWatcherController(
            Context appContext,
            CoroutineScope appScope,
            BookmarksManager bookmarksManager,
            BookmarkForegroundWatcher bookmarkForegroundWatcher,
            ApplicationVisibilityManager applicationVisibilityManager
    ) {
        Logger.d(AppModule.DI_TAG, "BookmarkWatcherController");

        return new BookmarkWatcherCoordinator(
                AndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
                appContext,
                appScope,
                bookmarksManager,
                bookmarkForegroundWatcher,
                applicationVisibilityManager
        );
    }

    @Provides
    @Singleton
    public LastViewedPostNoInfoHolder provideLastViewedPostNoInfoHolder() {
        Logger.d(AppModule.DI_TAG, "LastViewedPostNoInfoHolder");

        return new LastViewedPostNoInfoHolder();
    }

    @Provides
    @Singleton
    public ReplyNotificationsHelper provideReplyNotificationsHelper(
            Context appContext,
            CoroutineScope appScope,
            BookmarksManager bookmarksManager,
            ChanPostRepository chanPostRepository,
            ImageLoaderV2 imageLoaderV2
    ) {
        Logger.d(AppModule.DI_TAG, "ReplyNotificationsHelper");

        return new ReplyNotificationsHelper(
                AndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
                appContext,
                appScope,
                getNotificationManagerCompat(),
                getNotificationManager(),
                bookmarksManager,
                chanPostRepository,
                imageLoaderV2
        );
    }

    @Provides
    @Singleton
    public LastPageNotificationsHelper provideLastPageNotificationsHelper(
            Context appContext,
            PageRequestManager pageRequestManager,
            BookmarksManager bookmarksManager
    ) {
        Logger.d(AppModule.DI_TAG, "LastPageNotificationsHelper");

        return new LastPageNotificationsHelper(
                AndroidUtils.isDevBuild(),
                appContext,
                getNotificationManagerCompat(),
                pageRequestManager,
                bookmarksManager
        );
    }

    @Provides
    @Singleton
    public ChanThreadViewableInfoManager provideChanThreadViewableInfoManager(
            ChanThreadViewableInfoRepository chanThreadViewableInfoRepository,
            CoroutineScope appScope
    ) {
        Logger.d(AppModule.DI_TAG, "ChanThreadViewableInfoManager");

        return new ChanThreadViewableInfoManager(
                ChanSettings.verboseLogs.get(),
                appScope,
                chanThreadViewableInfoRepository
        );
    }

    @Provides
    @Singleton
    public SavedReplyManager provideSavedReplyManager(
            ChanSavedReplyRepository chanSavedReplyRepository
    ) {
        Logger.d(AppModule.DI_TAG, "SavedReplyManager");

        return new SavedReplyManager(
                ChanSettings.verboseLogs.get(),
                chanSavedReplyRepository
        );
    }

    @Provides
    @Singleton
    public PostHideManager providePostHideManager(
        ChanPostHideRepository chanPostHideRepository,
        CoroutineScope appScope
    ) {
        Logger.d(AppModule.DI_TAG, "PostHideManager");

        return new PostHideManager(
                ChanSettings.verboseLogs.get(),
                appScope,
                chanPostHideRepository
        );
    }

    @Provides
    @Singleton
    public ChanFilterManager provideChanFilterManager(
            ChanFilterRepository chanFilterRepository,
            CoroutineScope appScope,
            PostFilterManager postFilterManager
    ) {
        Logger.d(AppModule.DI_TAG, "ChanFilterManager");

        return new ChanFilterManager(
                appScope,
                chanFilterRepository,
                postFilterManager
        );
    }

    @Provides
    @Singleton
    public LocalSearchManager provideLocalSearchManager() {
        Logger.d(AppModule.DI_TAG, "LocalSearchManager");

        return new LocalSearchManager();
    }
}
