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
package com.github.adamantcheese.chan.core.di;

import android.content.Context;

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.image.ImageLoaderV2;
import com.github.adamantcheese.chan.core.interactors.FetchThreadBookmarkInfoUseCase;
import com.github.adamantcheese.chan.core.interactors.ParsePostRepliesUseCase;
import com.github.adamantcheese.chan.core.loader.OnDemandContentLoader;
import com.github.adamantcheese.chan.core.loader.impl.InlinedFileInfoLoader;
import com.github.adamantcheese.chan.core.loader.impl.PostExtraContentLoader;
import com.github.adamantcheese.chan.core.loader.impl.PrefetchLoader;
import com.github.adamantcheese.chan.core.manager.ApplicationVisibilityManager;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.manager.BookmarksManager;
import com.github.adamantcheese.chan.core.manager.ChanLoaderManager;
import com.github.adamantcheese.chan.core.manager.ControllerNavigationManager;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.manager.GlobalWindowInsetsManager;
import com.github.adamantcheese.chan.core.manager.HistoryNavigationManager;
import com.github.adamantcheese.chan.core.manager.LastPageNotificationsHelper;
import com.github.adamantcheese.chan.core.manager.LastViewedPostNoInfoHolder;
import com.github.adamantcheese.chan.core.manager.OnDemandContentLoaderManager;
import com.github.adamantcheese.chan.core.manager.PageRequestManager;
import com.github.adamantcheese.chan.core.manager.PostFilterManager;
import com.github.adamantcheese.chan.core.manager.PrefetchImageDownloadIndicatorManager;
import com.github.adamantcheese.chan.core.manager.ReplyManager;
import com.github.adamantcheese.chan.core.manager.ReplyNotificationsHelper;
import com.github.adamantcheese.chan.core.manager.ReplyViewStateManager;
import com.github.adamantcheese.chan.core.manager.ReportManager;
import com.github.adamantcheese.chan.core.manager.SeenPostsManager;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.ParserRepository;
import com.github.adamantcheese.chan.core.site.parser.MockReplyManager;
import com.github.adamantcheese.chan.core.site.parser.ReplyParser;
import com.github.adamantcheese.chan.features.bookmarks.watcher.BookmarkForegroundWatcher;
import com.github.adamantcheese.chan.features.bookmarks.watcher.BookmarkWatcherController;
import com.github.adamantcheese.chan.features.bookmarks.watcher.BookmarkWatcherDelegate;
import com.github.adamantcheese.chan.ui.settings.base_directory.SavedFilesBaseDirectory;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.common.AppConstants;
import com.github.adamantcheese.model.repository.BookmarksRepository;
import com.github.adamantcheese.model.repository.ChanPostRepository;
import com.github.adamantcheese.model.repository.HistoryNavigationRepository;
import com.github.adamantcheese.model.repository.SeenPostRepository;
import com.github.adamantcheese.model.repository.ThirdPartyArchiveInfoRepository;
import com.github.k1rakishou.feather2.Provides;
import com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.manager.base_directory.DirectoryManager;
import com.google.gson.Gson;

import java.io.File;
import java.util.HashSet;
import java.util.concurrent.Executor;

import javax.inject.Named;
import javax.inject.Singleton;

import io.reactivex.schedulers.Schedulers;
import kotlinx.coroutines.CoroutineScope;

import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getFlavorType;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getNotificationManager;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getNotificationManagerCompat;
import static com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy.ReplaceBadSymbols;
import static com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy.ThrowAnException;

public class ManagerModule {
    private static final String CRASH_LOGS_DIR_NAME = "crashlogs";

    @Provides
    @Singleton
    public BoardManager provideBoardManager(BoardRepository boardRepository) {
        Logger.d(AppModule.DI_TAG, "Board manager");
        return new BoardManager(boardRepository);
    }

    @Provides
    @Singleton
    public FilterEngine provideFilterEngine(DatabaseManager databaseManager) {
        Logger.d(AppModule.DI_TAG, "Filter engine");
        return new FilterEngine(databaseManager);
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
            DatabaseManager databaseManager,
            SiteRepository siteRepository
    ) {
        Logger.d(AppModule.DI_TAG, "Page request manager");
        return new PageRequestManager(
                databaseManager,
                databaseManager.getDatabaseBoardManager(),
                siteRepository
        );
    }

    @Provides
    @Singleton
    public ArchivesManager provideArchivesManager(
            Context appContext,
            ThirdPartyArchiveInfoRepository thirdPartyArchiveInfoRepository,
            Gson gson,
            AppConstants appConstants,
            CoroutineScope appScope
    ) {
        Logger.d(AppModule.DI_TAG, "Archives manager");
        return new ArchivesManager(
                appContext,
                appScope,
                thirdPartyArchiveInfoRepository,
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
            NetModule.ProxiedOkHttpClient okHttpClient,
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
    public SeenPostsManager provideSeenPostsManager(SeenPostRepository seenPostRepository) {
        Logger.d(AppModule.DI_TAG, "SeenPostsManager");

        return new SeenPostsManager(seenPostRepository);
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
        if (getFlavorType() != AndroidUtils.FlavorType.Release) {
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
    public ReplyViewStateManager provideReplyViewStateManager() {
        Logger.d(AppModule.DI_TAG, "ReplyViewStateManager");

        return new ReplyViewStateManager();
    }

    @Provides
    @Singleton
    public BookmarksManager provideBookmarksManager(
            CoroutineScope appScope,
            ApplicationVisibilityManager applicationVisibilityManager,
            BookmarksRepository bookmarksRepository
    ) {
        Logger.d(AppModule.DI_TAG, "BookmarksManager");

        return new BookmarksManager(
                getFlavorType() == AndroidUtils.FlavorType.Dev,
                appScope,
                applicationVisibilityManager,
                bookmarksRepository
        );
    }

    @Provides
    @Singleton
    public ReplyParser provideReplyParser(
            SiteRepository siteRepository,
            ParserRepository parserRepository
    ) {
        Logger.d(AppModule.DI_TAG, "ReplyParser");

        return new ReplyParser(
                siteRepository,
                parserRepository
        );
    }

    @Provides
    @Singleton
    public BookmarkWatcherDelegate provideBookmarkWatcherDelegate(
            CoroutineScope appScope,
            BookmarksManager bookmarksManager,
            SiteRepository siteRepository,
            DatabaseManager databaseManager,
            LastViewedPostNoInfoHolder lastViewedPostNoInfoHolder,
            FetchThreadBookmarkInfoUseCase fetchThreadBookmarkInfoUseCase,
            ParsePostRepliesUseCase parsePostRepliesUseCase,
            ReplyNotificationsHelper replyNotificationsHelper,
            LastPageNotificationsHelper lastPageNotificationsHelper
    ) {
        Logger.d(AppModule.DI_TAG, "BookmarkWatcherDelegate");

        return new BookmarkWatcherDelegate(
                getFlavorType() == AndroidUtils.FlavorType.Dev,
                appScope,
                bookmarksManager,
                siteRepository,
                databaseManager.getDatabaseSavedReplyManager(),
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
            BookmarkWatcherDelegate bookmarkWatcherDelegate
    ) {
        Logger.d(AppModule.DI_TAG, "BookmarkForegroundWatcher");

        return new BookmarkForegroundWatcher(
                getFlavorType() == AndroidUtils.FlavorType.Dev,
                appScope,
                bookmarksManager,
                bookmarkWatcherDelegate
        );
    }

    @Provides
    @Singleton
    public BookmarkWatcherController provideBookmarkWatcherController(
            Context appContext,
            CoroutineScope appScope,
            BookmarksManager bookmarksManager,
            BookmarkForegroundWatcher bookmarkForegroundWatcher,
            ApplicationVisibilityManager applicationVisibilityManager
    ) {
        Logger.d(AppModule.DI_TAG, "BookmarkWatcherController");

        return new BookmarkWatcherController(
                getFlavorType() == AndroidUtils.FlavorType.Dev,
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
                getFlavorType() == AndroidUtils.FlavorType.Dev,
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
                getFlavorType() == AndroidUtils.FlavorType.Dev,
                appContext,
                getNotificationManagerCompat(),
                pageRequestManager,
                bookmarksManager
        );
    }
}
