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

import static com.github.k1rakishou.chan.core.di.module.application.AppModule.getCacheDir;
import static com.github.k1rakishou.common.AndroidUtils.getNotificationManager;
import static com.github.k1rakishou.common.AndroidUtils.getNotificationManagerCompat;

import android.content.Context;

import androidx.core.app.NotificationManagerCompat;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.RealDownloaderOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.cache.CacheHandler;
import com.github.k1rakishou.chan.core.helper.FilterEngine;
import com.github.k1rakishou.chan.core.helper.ImageSaverFileManagerWrapper;
import com.github.k1rakishou.chan.core.helper.LastPageNotificationsHelper;
import com.github.k1rakishou.chan.core.helper.LastViewedPostNoInfoHolder;
import com.github.k1rakishou.chan.core.helper.PostHideHelper;
import com.github.k1rakishou.chan.core.helper.ReplyNotificationsHelper;
import com.github.k1rakishou.chan.core.helper.ThreadDownloaderFileManagerWrapper;
import com.github.k1rakishou.chan.core.image.ImageLoaderV2;
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader;
import com.github.k1rakishou.chan.core.loader.impl.Chan4CloudFlareImagePreloader;
import com.github.k1rakishou.chan.core.loader.impl.PostExtraContentLoader;
import com.github.k1rakishou.chan.core.loader.impl.PrefetchLoader;
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager;
import com.github.k1rakishou.chan.core.manager.ArchivesManager;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.BookmarksManager;
import com.github.k1rakishou.chan.core.manager.Chan4CloudFlareImagePreloaderManager;
import com.github.k1rakishou.chan.core.manager.ChanFilterManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager;
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager;
import com.github.k1rakishou.chan.core.manager.OnDemandContentLoaderManager;
import com.github.k1rakishou.chan.core.manager.PageRequestManager;
import com.github.k1rakishou.chan.core.manager.PostFilterManager;
import com.github.k1rakishou.chan.core.manager.PostHideManager;
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager;
import com.github.k1rakishou.chan.core.manager.PrefetchStateManager;
import com.github.k1rakishou.chan.core.manager.ReplyManager;
import com.github.k1rakishou.chan.core.manager.ReportManager;
import com.github.k1rakishou.chan.core.manager.SavedReplyManager;
import com.github.k1rakishou.chan.core.manager.SeenPostsManager;
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager;
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager;
import com.github.k1rakishou.chan.core.manager.watcher.BookmarkForegroundWatcher;
import com.github.k1rakishou.chan.core.manager.watcher.BookmarkWatcherCoordinator;
import com.github.k1rakishou.chan.core.manager.watcher.BookmarkWatcherDelegate;
import com.github.k1rakishou.chan.core.manager.watcher.FilterWatcherCoordinator;
import com.github.k1rakishou.chan.core.manager.watcher.FilterWatcherDelegate;
import com.github.k1rakishou.chan.core.site.ParserRepository;
import com.github.k1rakishou.chan.core.site.SiteRegistry;
import com.github.k1rakishou.chan.core.site.SiteResolver;
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator;
import com.github.k1rakishou.chan.core.site.parser.ReplyParser;
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser;
import com.github.k1rakishou.chan.core.usecase.BookmarkFilterWatchableThreadsUseCase;
import com.github.k1rakishou.chan.core.usecase.CatalogDataPreloadUseCase;
import com.github.k1rakishou.chan.core.usecase.FetchThreadBookmarkInfoUseCase;
import com.github.k1rakishou.chan.core.usecase.ParsePostRepliesUseCase;
import com.github.k1rakishou.chan.core.usecase.ThreadDataPreloadUseCase;
import com.github.k1rakishou.chan.core.usecase.ThreadDownloaderPersistPostsInDatabaseUseCase;
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2ServiceDelegate;
import com.github.k1rakishou.chan.features.posting.LastReplyRepository;
import com.github.k1rakishou.chan.features.posting.PostingServiceDelegate;
import com.github.k1rakishou.chan.features.posting.solvers.two_captcha.TwoCaptchaSolver;
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadProgressNotifier;
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingCoordinator;
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingDelegate;
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.model.repository.BoardRepository;
import com.github.k1rakishou.model.repository.BookmarksRepository;
import com.github.k1rakishou.model.repository.ChanFilterRepository;
import com.github.k1rakishou.model.repository.ChanFilterWatchRepository;
import com.github.k1rakishou.model.repository.ChanPostHideRepository;
import com.github.k1rakishou.model.repository.ChanPostImageRepository;
import com.github.k1rakishou.model.repository.ChanPostRepository;
import com.github.k1rakishou.model.repository.ChanSavedReplyRepository;
import com.github.k1rakishou.model.repository.ChanThreadViewableInfoRepository;
import com.github.k1rakishou.model.repository.HistoryNavigationRepository;
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository;
import com.github.k1rakishou.model.repository.SeenPostRepository;
import com.github.k1rakishou.model.repository.SiteRepository;
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository;
import com.github.k1rakishou.model.repository.ThreadDownloadRepository;
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

@Module
public class ManagerModule {
    private static final String CRASH_LOGS_DIR_NAME = "crashlogs";
    private static final String ANRS_DIR_NAME = "anrs";

    @Provides
    @Singleton
    public SiteManager provideSiteManager(
            CoroutineScope appScope,
            SiteRepository siteRepository
    ) {
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
            BoardRepository boardRepository
    ) {
        return new BoardManager(
                appScope,
                AppModuleAndroidUtils.isDevBuild(),
                boardRepository
        );
    }

    @Provides
    @Singleton
    public FilterEngine provideFilterEngine(ChanFilterManager chanFilterManager) {
        return new FilterEngine(chanFilterManager);
    }

    @Provides
    @Singleton
    public ReplyManager provideReplyManager(
            AppConstants appConstants,
            Gson gson
    ) {
        return new ReplyManager(appConstants, gson);
    }

    @Provides
    @Singleton
    public PageRequestManager providePageRequestManager(
            SiteManager siteManager,
            BoardManager boardManager
    ) {
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
    public ReportManager provideReportManager(
            CoroutineScope appScope,
            Context appContext,
            ProxiedOkHttpClient okHttpClient,
            Gson gson,
            SettingsNotificationManager settingsNotificationManager
    ) {
        File cacheDir = getCacheDir().getValue();

        return new ReportManager(
                appScope,
                appContext,
                okHttpClient.okHttpClient(),
                settingsNotificationManager,
                gson,
                new File(cacheDir, CRASH_LOGS_DIR_NAME),
                new File(cacheDir, ANRS_DIR_NAME)
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
            Chan4CloudFlareImagePreloader chan4CloudFlareImagePreloader,
            ChanThreadManager chanThreadManager,
            @Named(ExecutorsModule.onDemandContentLoaderExecutorName) Executor onDemandContentLoaderExecutor
    ) {
        HashSet<OnDemandContentLoader> loaders = new HashSet<>();
        loaders.add(chan4CloudFlareImagePreloader);
        loaders.add(prefetchLoader);
        loaders.add(postExtraContentLoader);

        return new OnDemandContentLoaderManager(
                Schedulers.from(onDemandContentLoaderExecutor),
                loaders,
                chanThreadManager
        );
    }

    @Provides
    @Singleton
    public SeenPostsManager provideSeenPostsManager(
            CoroutineScope appScope,
            ChanThreadsCache chanThreadsCache,
            SeenPostRepository seenPostRepository
    ) {
        return new SeenPostsManager(
                appScope,
                ChanSettings.verboseLogs.get(),
                chanThreadsCache,
                seenPostRepository
        );
    }

    @Provides
    @Singleton
    public PrefetchStateManager providePrefetchIndicatorAnimationManager() {
        return new PrefetchStateManager();
    }

    @Provides
    @Singleton
    public ApplicationVisibilityManager provideApplicationVisibilityManager() {
        return new ApplicationVisibilityManager();
    }

    @Provides
    @Singleton
    public HistoryNavigationManager provideHistoryNavigationManager(
            CoroutineScope appScope,
            HistoryNavigationRepository historyNavigationRepository,
            ApplicationVisibilityManager applicationVisibilityManager
    ) {
        return new HistoryNavigationManager(
                appScope,
                historyNavigationRepository,
                applicationVisibilityManager
        );
    }

    @Provides
    @Singleton
    public PostFilterManager providePostFilterManager(
            CoroutineScope appScope,
            ChanThreadsCache chanThreadsCache
    ) {
        return new PostFilterManager(
                ChanSettings.verboseLogs.get(),
                appScope,
                chanThreadsCache
        );
    }

    @Provides
    @Singleton
    public BookmarksManager provideBookmarksManager(
            CoroutineScope appScope,
            ApplicationVisibilityManager applicationVisibilityManager,
            ArchivesManager archivesManager,
            BookmarksRepository bookmarksRepository
    ) {
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
            CoroutineScope appScope,
            ChanThreadsCache chanThreadsCache
    ) {
        return new ChanThreadViewableInfoManager(
                ChanSettings.verboseLogs.get(),
                appScope,
                chanThreadViewableInfoRepository,
                chanThreadsCache
        );
    }

    @Provides
    @Singleton
    public SavedReplyManager provideSavedReplyManager(
            ChanThreadsCache chanThreadsCache,
            ChanSavedReplyRepository chanSavedReplyRepository
    ) {
        return new SavedReplyManager(
                ChanSettings.verboseLogs.get(),
                chanThreadsCache,
                chanSavedReplyRepository
        );
    }

    @Provides
    @Singleton
    public PostHideManager providePostHideManager(
        ChanPostHideRepository chanPostHideRepository,
        CoroutineScope appScope,
        ChanThreadsCache chanThreadsCache
    ) {
        return new PostHideManager(
                ChanSettings.verboseLogs.get(),
                appScope,
                chanPostHideRepository,
                chanThreadsCache
        );
    }

    @Provides
    @Singleton
    public ChanFilterManager provideChanFilterManager(
            ChanFilterRepository chanFilterRepository,
            ChanPostRepository chanPostRepository,
            ChanFilterWatchRepository chanFilterWatchRepository,
            CoroutineScope appScope,
            PostFilterManager postFilterManager
    ) {
        return new ChanFilterManager(
                AppModuleAndroidUtils.isDevBuild(),
                appScope,
                chanFilterRepository,
                chanPostRepository,
                chanFilterWatchRepository,
                postFilterManager
        );
    }

    @Singleton
    @Provides
    public PostHideHelper providePostHideHelper(
            PostHideManager postHideManager,
            PostFilterManager postFilterManager
    ) {
        return new PostHideHelper(
                postHideManager,
                postFilterManager
        );
    }

    @Singleton
    @Provides
    public ThreadBookmarkGroupManager provideThreadBookmarkGroupEntryManager(
            CoroutineScope appScope,
            ThreadBookmarkGroupRepository threadBookmarkGroupEntryRepository,
            BookmarksManager bookmarksManager
    ) {
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
            ChanThreadsCache chanThreadsCache
    ) {
        return new Chan4CloudFlareImagePreloaderManager(
                appScope,
                ChanSettings.verboseLogs.get(),
                realProxiedOkHttpClient,
                chanThreadsCache
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
            ChanThreadLoaderCoordinator chanThreadLoaderCoordinator,
            ThreadDataPreloadUseCase threadDataPreloadUseCase,
            CatalogDataPreloadUseCase catalogDataPreloadUseCase
    ) {
        return new ChanThreadManager(
                ChanSettings.verboseLogs.get(),
                siteManager,
                bookmarksManager,
                postFilterManager,
                savedReplyManager,
                chanThreadsCache,
                chanPostRepository,
                chanThreadLoaderCoordinator,
                threadDataPreloadUseCase,
                catalogDataPreloadUseCase
        );
    }

    @Singleton
    @Provides
    public PostingLimitationsInfoManager providePostingLimitationsInfoManager(
            SiteManager siteManager
    ) {
        return new PostingLimitationsInfoManager(siteManager);
    }

    @Singleton
    @Provides
    public FilterWatcherCoordinator provideFilterWatcherCoordinator(
            Context appContext,
            CoroutineScope appScope,
            AppConstants appConstants,
            ChanFilterManager chanFilterManager
    ) {
        return new FilterWatcherCoordinator(
                ChanSettings.verboseLogs.get(),
                appContext,
                appScope,
                appConstants,
                chanFilterManager
        );
    }

    @Singleton
    @Provides
    public FilterWatcherDelegate provideFilterWatcherDelegate(
            CoroutineScope appScope,
            BoardManager boardManager,
            BookmarksManager bookmarksManager,
            ChanFilterManager chanFilterManager,
            ChanPostRepository chanPostRepository,
            SiteManager siteManager,
            BookmarkFilterWatchableThreadsUseCase bookmarkFilterWatchableThreadsUseCase
    ) {
        return new FilterWatcherDelegate(
                AppModuleAndroidUtils.isDevBuild(),
                appScope,
                boardManager,
                bookmarksManager,
                chanFilterManager,
                chanPostRepository,
                siteManager,
                bookmarkFilterWatchableThreadsUseCase
        );
    }

    @Singleton
    @Provides
    public ImageSaverV2ServiceDelegate provideImageSaverV2Delegate(
            Context appContext,
            CoroutineScope appScope,
            AppConstants appConstants,
            CacheHandler cacheHandler,
            RealDownloaderOkHttpClient downloaderOkHttpClient,
            ImageSaverFileManagerWrapper imageSaverFileManagerWrapper,
            SiteResolver siteResolver,
            ChanPostImageRepository chanPostImageRepository,
            ImageDownloadRequestRepository imageDownloadRequestRepository,
            ChanThreadManager chanThreadManager,
            ThreadDownloadManager threadDownloadManager
    ) {
        return new ImageSaverV2ServiceDelegate(
                ChanSettings.verboseLogs.get(),
                appScope,
                appConstants,
                cacheHandler,
                downloaderOkHttpClient,
                NotificationManagerCompat.from(appContext),
                imageSaverFileManagerWrapper,
                siteResolver,
                chanPostImageRepository,
                imageDownloadRequestRepository,
                chanThreadManager,
                threadDownloadManager
        );
    }

    @Singleton
    @Provides
    public TwoCaptchaSolver provideTwoCaptchaSolver(
            Gson gson,
            SiteManager siteManager,
            ProxiedOkHttpClient proxiedOkHttpClient
    ) {
        return new TwoCaptchaSolver(
                AppModuleAndroidUtils.isDevBuild(),
                gson,
                siteManager,
                proxiedOkHttpClient
        );
    }

    @Singleton
    @Provides
    public PostingServiceDelegate providePostingServiceDelegate(
            CoroutineScope appScope,
            AppConstants appConstants,
            ReplyManager replyManager,
            SiteManager siteManager,
            BoardManager boardManager,
            BookmarksManager bookmarksManager,
            SavedReplyManager savedReplyManager,
            ChanThreadManager chanThreadManager,
            LastReplyRepository lastReplyRepository,
            ChanPostRepository chanPostRepository,
            TwoCaptchaSolver twoCaptchaSolver,
            CaptchaHolder captchaHolder
    ) {
        return new PostingServiceDelegate(
                appScope,
                appConstants,
                replyManager,
                siteManager,
                boardManager,
                bookmarksManager,
                savedReplyManager,
                chanThreadManager,
                lastReplyRepository,
                chanPostRepository,
                twoCaptchaSolver,
                captchaHolder
        );
    }

    @Singleton
    @Provides
    public ThreadDownloadManager provideThreadDownloadManager(
            AppConstants appConstants,
            CoroutineScope appScope,
            ThreadDownloaderFileManagerWrapper threadDownloaderFileManagerWrapper,
            ThreadDownloadRepository threadDownloadRepository,
            ChanPostRepository chanPostRepository
    ) {
        return new ThreadDownloadManager(
                appConstants,
                appScope,
                threadDownloaderFileManagerWrapper,
                threadDownloadRepository,
                chanPostRepository
        );
    }

    @Singleton
    @Provides
    public ThreadDownloadingCoordinator provideThreadDownloadingCoordinator(
            Context appContext,
            CoroutineScope appScope,
            AppConstants appConstants,
            ThreadDownloadManager threadDownloadManager
    ) {
        return new ThreadDownloadingCoordinator(
                appContext,
                appScope,
                appConstants,
                threadDownloadManager
        );
    }

    @Singleton
    @Provides
    public ThreadDownloadingDelegate provideThreadDownloadingDelegate(
            AppConstants appConstants,
            RealDownloaderOkHttpClient realDownloaderOkHttpClient,
            SiteManager siteManager,
            SiteResolver siteResolver,
            ThreadDownloadManager threadDownloadManager,
            ChanPostRepository chanPostRepository,
            ChanPostImageRepository chanPostImageRepository,
            ThreadDownloaderFileManagerWrapper threadDownloaderFileManagerWrapper,
            ThreadDownloadProgressNotifier threadDownloadProgressNotifier,
            ThreadDownloaderPersistPostsInDatabaseUseCase threadDownloaderPersistPostsInDatabaseUseCase
    ) {
        return new ThreadDownloadingDelegate(
                appConstants,
                realDownloaderOkHttpClient,
                siteManager,
                siteResolver,
                threadDownloadManager,
                chanPostRepository,
                chanPostImageRepository,
                threadDownloaderFileManagerWrapper,
                threadDownloadProgressNotifier,
                threadDownloaderPersistPostsInDatabaseUseCase
        );
    }

}
