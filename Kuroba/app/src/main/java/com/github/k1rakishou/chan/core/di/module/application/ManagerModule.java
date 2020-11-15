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

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.helper.DialogFactory;
import com.github.k1rakishou.chan.core.helper.FilterEngine;
import com.github.k1rakishou.chan.core.helper.LastPageNotificationsHelper;
import com.github.k1rakishou.chan.core.helper.LastViewedPostNoInfoHolder;
import com.github.k1rakishou.chan.core.helper.PostHideHelper;
import com.github.k1rakishou.chan.core.helper.ReplyNotificationsHelper;
import com.github.k1rakishou.chan.core.image.ImageLoaderV2;
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader;
import com.github.k1rakishou.chan.core.loader.impl.Chan4CloudFlareImagePreloader;
import com.github.k1rakishou.chan.core.loader.impl.InlinedFileInfoLoader;
import com.github.k1rakishou.chan.core.loader.impl.PostExtraContentLoader;
import com.github.k1rakishou.chan.core.loader.impl.PrefetchLoader;
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager;
import com.github.k1rakishou.chan.core.manager.ArchivesManager;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.BookmarksManager;
import com.github.k1rakishou.chan.core.manager.BottomNavBarVisibilityStateManager;
import com.github.k1rakishou.chan.core.manager.Chan4CloudFlareImagePreloaderManager;
import com.github.k1rakishou.chan.core.manager.ChanFilterManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager;
import com.github.k1rakishou.chan.core.manager.ControllerNavigationManager;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager;
import com.github.k1rakishou.chan.core.manager.LocalSearchManager;
import com.github.k1rakishou.chan.core.manager.OnDemandContentLoaderManager;
import com.github.k1rakishou.chan.core.manager.PageRequestManager;
import com.github.k1rakishou.chan.core.manager.PostFilterManager;
import com.github.k1rakishou.chan.core.manager.PostHideManager;
import com.github.k1rakishou.chan.core.manager.PrefetchImageDownloadIndicatorManager;
import com.github.k1rakishou.chan.core.manager.ReplyManager;
import com.github.k1rakishou.chan.core.manager.ReportManager;
import com.github.k1rakishou.chan.core.manager.SavedReplyManager;
import com.github.k1rakishou.chan.core.manager.SeenPostsManager;
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager;
import com.github.k1rakishou.chan.core.manager.ThreadFollowHistoryManager;
import com.github.k1rakishou.chan.core.site.ParserRepository;
import com.github.k1rakishou.chan.core.site.SiteRegistry;
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator;
import com.github.k1rakishou.chan.core.site.parser.MockReplyManager;
import com.github.k1rakishou.chan.core.site.parser.ReplyParser;
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser;
import com.github.k1rakishou.chan.core.usecase.FetchThreadBookmarkInfoUseCase;
import com.github.k1rakishou.chan.core.usecase.ParsePostRepliesUseCase;
import com.github.k1rakishou.chan.features.bookmarks.watcher.BookmarkForegroundWatcher;
import com.github.k1rakishou.chan.features.bookmarks.watcher.BookmarkWatcherCoordinator;
import com.github.k1rakishou.chan.features.bookmarks.watcher.BookmarkWatcherDelegate;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_themes.ThemeEngine;
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
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository;
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache;
import com.google.gson.Gson;

import java.io.File;
import java.util.HashSet;
import java.util.concurrent.Executor;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.reactivex.schedulers.Schedulers;
import kotlinx.coroutines.CoroutineScope;

import static com.github.k1rakishou.chan.core.di.module.application.AppModule.getCacheDir;
import static com.github.k1rakishou.common.AndroidUtils.getNotificationManager;
import static com.github.k1rakishou.common.AndroidUtils.getNotificationManagerCompat;

@Module
public class ManagerModule {
    private static final String CRASH_LOGS_DIR_NAME = "crashlogs";

    @Provides
    @Singleton
    public SiteManager provideSiteManager(
            CoroutineScope appScope,
            SiteRepository siteRepository
    ) {
        Logger.d(AppModule.DI_TAG, "Site manager");

        return new SiteManager(
                appScope,
                AppModuleAndroidUtils.isDevBuild(),
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
                AppModuleAndroidUtils.isDevBuild(),
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
                gson,
                appContext,
                appScope,
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
                okHttpClient.okHttpClient(),
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
            Chan4CloudFlareImagePreloader сhan4CloudFlareImagePreloader,
            @Named(ExecutorsModule.onDemandContentLoaderExecutorName) Executor onDemandContentLoaderExecutor
    ) {
        Logger.d(AppModule.DI_TAG, "OnDemandContentLoaderManager");

        HashSet<OnDemandContentLoader> loaders = new HashSet<>();
        loaders.add(сhan4CloudFlareImagePreloader);
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
                AppModuleAndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
                appScope,
                applicationVisibilityManager,
                archivesManager,
                bookmarksRepository,
                SiteRegistry.INSTANCE
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
                AppModuleAndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
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
            Context appContext,
            AppConstants appConstants,
            BookmarksManager bookmarksManager,
            ArchivesManager archivesManager,
            BookmarkWatcherDelegate bookmarkWatcherDelegate,
            ApplicationVisibilityManager applicationVisibilityManager
    ) {
        Logger.d(AppModule.DI_TAG, "BookmarkForegroundWatcher");

        return new BookmarkForegroundWatcher(
                ChanSettings.verboseLogs.get(),
                appScope,
                appContext,
                appConstants,
                bookmarksManager,
                archivesManager,
                bookmarkWatcherDelegate,
                applicationVisibilityManager
        );
    }

    @Provides
    @Singleton
    public BookmarkWatcherCoordinator provideBookmarkWatcherController(
            Context appContext,
            CoroutineScope appScope,
            AppConstants appConstants,
            BookmarksManager bookmarksManager,
            BookmarkForegroundWatcher bookmarkForegroundWatcher
    ) {
        Logger.d(AppModule.DI_TAG, "BookmarkWatcherController");

        return new BookmarkWatcherCoordinator(
                ChanSettings.verboseLogs.get(),
                appContext,
                appScope,
                appConstants,
                bookmarksManager,
                bookmarkForegroundWatcher
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
            ImageLoaderV2 imageLoaderV2,
            ThemeEngine themeEngine,
            SimpleCommentParser simpleCommentParser
    ) {
        Logger.d(AppModule.DI_TAG, "ReplyNotificationsHelper");

        return new ReplyNotificationsHelper(
                AppModuleAndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
                appContext,
                appScope,
                getNotificationManagerCompat(),
                getNotificationManager(),
                bookmarksManager,
                chanPostRepository,
                imageLoaderV2,
                themeEngine,
                simpleCommentParser
        );
    }

    @Provides
    @Singleton
    public LastPageNotificationsHelper provideLastPageNotificationsHelper(
            Context appContext,
            PageRequestManager pageRequestManager,
            BookmarksManager bookmarksManager,
            ThemeEngine themeEngine
    ) {
        Logger.d(AppModule.DI_TAG, "LastPageNotificationsHelper");

        return new LastPageNotificationsHelper(
                AppModuleAndroidUtils.isDevBuild(),
                appContext,
                getNotificationManagerCompat(),
                pageRequestManager,
                bookmarksManager,
                themeEngine
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

    @Singleton
    @Provides
    public PostHideHelper providePostHideHelper(
            PostHideManager postHideManager,
            PostFilterManager postFilterManager
    ) {
        Logger.d(AppModule.DI_TAG, "PostHideHelper");

        return new PostHideHelper(
                postHideManager,
                postFilterManager
        );
    }

    @Singleton
    @Provides
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

    @Singleton
    @Provides
    public ThreadBookmarkGroupManager provideThreadBookmarkGroupEntryManager(
            CoroutineScope appScope,
            ThreadBookmarkGroupRepository threadBookmarkGroupEntryRepository,
            BookmarksManager bookmarksManager
    ) {
        Logger.d(AppModule.DI_TAG, "ThreadBookmarkGroupEntryManager");

        return new ThreadBookmarkGroupManager(
                appScope,
                ChanSettings.verboseLogs.get(),
                threadBookmarkGroupEntryRepository,
                bookmarksManager
        );
    }

    @Singleton
    @Provides
    public Chan4CloudFlareImagePreloaderManager provideChan4CloudFlareImagePreloaderManager(
            CoroutineScope appScope,
            RealProxiedOkHttpClient realProxiedOkHttpClient,
            ChanThreadManager chanThreadManager
    ) {
        Logger.d(AppModule.DI_TAG, "Chan4CloudFlareImagePreloaderManager");

        return new Chan4CloudFlareImagePreloaderManager(
                appScope,
                ChanSettings.verboseLogs.get(),
                realProxiedOkHttpClient,
                chanThreadManager
        );
    }

    @Singleton
    @Provides
    public ChanThreadManager provideChanThreadManager(
            SiteManager siteManager,
            BookmarksManager bookmarksManager,
            PostFilterManager postFilterManager,
            SavedReplyManager savedReplyManager,
            ChanThreadsCache chanThreadsCache,
            ChanPostRepository chanPostRepository,
            ChanThreadLoaderCoordinator chanThreadLoaderCoordinator
    ) {
        Logger.d(AppModule.DI_TAG, "ChanThreadManager");

        return new ChanThreadManager(
                siteManager,
                bookmarksManager,
                postFilterManager,
                savedReplyManager,
                chanThreadsCache,
                chanPostRepository,
                chanThreadLoaderCoordinator
        );
    }

    @Singleton
    @Provides
    public ThreadFollowHistoryManager provideThreadFollowHistoryManager() {
        Logger.d(AppModule.DI_TAG, "ThreadFollowHistoryManager");

        return new ThreadFollowHistoryManager();
    }

}
