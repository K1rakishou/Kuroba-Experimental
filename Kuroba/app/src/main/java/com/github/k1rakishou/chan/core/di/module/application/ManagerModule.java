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

import static com.github.k1rakishou.common.AndroidUtils.getNotificationManager;

import android.content.Context;

import androidx.core.app.NotificationManagerCompat;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.RealDownloaderOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.cache.CacheHandler;
import com.github.k1rakishou.chan.core.helper.FilterEngine;
import com.github.k1rakishou.chan.core.helper.FilterWatcherNotificationHelper;
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
import com.github.k1rakishou.chan.core.loader.impl.PostHighlightFilterLoader;
import com.github.k1rakishou.chan.core.loader.impl.PrefetchLoader;
import com.github.k1rakishou.chan.core.loader.impl.ThirdEyeLoader;
import com.github.k1rakishou.chan.core.manager.ApplicationCrashNotifier;
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager;
import com.github.k1rakishou.chan.core.manager.ArchivesManager;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.BookmarksManager;
import com.github.k1rakishou.chan.core.manager.CaptchaImageCache;
import com.github.k1rakishou.chan.core.manager.Chan4CloudFlareImagePreloaderManager;
import com.github.k1rakishou.chan.core.manager.ChanFilterManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager;
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager;
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager;
import com.github.k1rakishou.chan.core.manager.FirewallBypassManager;
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager;
import com.github.k1rakishou.chan.core.manager.NotificationAutoDismissManager;
import com.github.k1rakishou.chan.core.manager.OnDemandContentLoaderManager;
import com.github.k1rakishou.chan.core.manager.PageRequestManager;
import com.github.k1rakishou.chan.core.manager.PostFilterHighlightManager;
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
import com.github.k1rakishou.chan.core.manager.ThirdEyeManager;
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager;
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager;
import com.github.k1rakishou.chan.core.site.ParserRepository;
import com.github.k1rakishou.chan.core.site.SiteRegistry;
import com.github.k1rakishou.chan.core.site.SiteResolver;
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator;
import com.github.k1rakishou.chan.core.site.parser.ReplyParser;
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser;
import com.github.k1rakishou.chan.core.usecase.BookmarkFilterWatchableThreadsUseCase;
import com.github.k1rakishou.chan.core.usecase.CatalogDataPreloader;
import com.github.k1rakishou.chan.core.usecase.FetchThreadBookmarkInfoUseCase;
import com.github.k1rakishou.chan.core.usecase.GetThreadBookmarkGroupIdsUseCase;
import com.github.k1rakishou.chan.core.usecase.ParsePostRepliesUseCase;
import com.github.k1rakishou.chan.core.usecase.ThreadDataPreloader;
import com.github.k1rakishou.chan.core.usecase.ThreadDownloaderPersistPostsInDatabaseUseCase;
import com.github.k1rakishou.chan.core.watcher.BookmarkForegroundWatcher;
import com.github.k1rakishou.chan.core.watcher.BookmarkWatcherCoordinator;
import com.github.k1rakishou.chan.core.watcher.BookmarkWatcherDelegate;
import com.github.k1rakishou.chan.core.watcher.FilterWatcherCoordinator;
import com.github.k1rakishou.chan.core.watcher.FilterWatcherDelegate;
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2ServiceDelegate;
import com.github.k1rakishou.chan.features.posting.CaptchaDonation;
import com.github.k1rakishou.chan.features.posting.LastReplyRepository;
import com.github.k1rakishou.chan.features.posting.PostingServiceDelegate;
import com.github.k1rakishou.chan.features.posting.solvers.two_captcha.TwoCaptchaSolver;
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadProgressNotifier;
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingCoordinator;
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingDelegate;
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.model.repository.BoardRepository;
import com.github.k1rakishou.model.repository.BookmarksRepository;
import com.github.k1rakishou.model.repository.ChanFilterRepository;
import com.github.k1rakishou.model.repository.ChanFilterWatchRepository;
import com.github.k1rakishou.model.repository.ChanPostHideRepository;
import com.github.k1rakishou.model.repository.ChanPostImageRepository;
import com.github.k1rakishou.model.repository.ChanPostRepository;
import com.github.k1rakishou.model.repository.ChanSavedReplyRepository;
import com.github.k1rakishou.model.repository.ChanThreadViewableInfoRepository;
import com.github.k1rakishou.model.repository.CompositeCatalogRepository;
import com.github.k1rakishou.model.repository.HistoryNavigationRepository;
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository;
import com.github.k1rakishou.model.repository.SeenPostRepository;
import com.github.k1rakishou.model.repository.SiteRepository;
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository;
import com.github.k1rakishou.model.repository.ThreadDownloadRepository;
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache;
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache;
import com.google.gson.Gson;
import com.squareup.moshi.Moshi;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Singleton;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import kotlin.LazyThreadSafetyMode;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;

@Module
public class ManagerModule {

    @Provides
    @Singleton
    public NotificationManagerCompat provideNotificationManagerCompat(Context applicationContext) {
        return NotificationManagerCompat.from(applicationContext);
    }

    @Provides
    @Singleton
    public SiteManager provideSiteManager(
            CoroutineScope appScope,
            Lazy<SiteRepository> siteRepository
    ) {
        Logger.deps("SiteManager");
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
            Lazy<BoardRepository> boardRepository,
            CurrentOpenedDescriptorStateManager currentOpenedDescriptorStateManager
    ) {
        Logger.deps("BoardManager");
        return new BoardManager(
                appScope,
                AppModuleAndroidUtils.isDevBuild(),
                boardRepository,
                currentOpenedDescriptorStateManager
        );
    }

    @Provides
    @Singleton
    public FilterEngine provideFilterEngine(
            ChanFilterManager chanFilterManager
    ) {
        Logger.deps("FilterEngine");
        return new FilterEngine(chanFilterManager);
    }

    @Provides
    @Singleton
    public ReplyManager provideReplyManager(
            ApplicationVisibilityManager applicationVisibilityManager,
            AppConstants appConstants,
            Moshi moshi,
            Gson gson
    ) {
        Logger.deps("ReplyManager");
        return new ReplyManager(
                applicationVisibilityManager,
                appConstants,
                moshi,
                gson
        );
    }

    @Provides
    @Singleton
    public PageRequestManager providePageRequestManager(
            SiteManager siteManager,
            BoardManager boardManager
    ) {
        Logger.deps("PageRequestManager");
        return new PageRequestManager(
                siteManager,
                boardManager
        );
    }

    @Provides
    @Singleton
    public ArchivesManager provideArchivesManager(
            Context appContext,
            Lazy<Gson> gson,
            AppConstants appConstants,
            CoroutineScope appScope
    ) {
        Logger.deps("ArchivesManager");
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
            AppConstants appConstants,
            Lazy<ProxiedOkHttpClient> okHttpClient,
            Lazy<Gson> gson
    ) {
        Logger.deps("ReportManager");

        return new ReportManager(
                appScope,
                appContext,
                okHttpClient,
                gson,
                appConstants
        );
    }

    @Provides
    @Singleton
    public SettingsNotificationManager provideSettingsNotificationManager() {
        Logger.deps("SettingsNotificationManager");
        return new SettingsNotificationManager();
    }

    @Provides
    @Singleton
    public OnDemandContentLoaderManager provideOnDemandContentLoader(
            CoroutineScope appScope,
            AppConstants appConstants,
            Lazy<PrefetchLoader> prefetchLoader,
            Lazy<PostExtraContentLoader> postExtraContentLoader,
            Lazy<Chan4CloudFlareImagePreloader> chan4CloudFlareImagePreloader,
            Lazy<PostHighlightFilterLoader> postHighlightFilterLoader,
            Lazy<ThirdEyeLoader> thirdEyeLoader,
            ChanThreadManager chanThreadManager
    ) {
        Logger.deps("OnDemandContentLoaderManager");
        kotlin.Lazy<List<OnDemandContentLoader>> loadersLazy = kotlin.LazyKt.lazy(
                LazyThreadSafetyMode.SYNCHRONIZED,
                () -> {
                    List<OnDemandContentLoader> loaders = new ArrayList<>();

                    // Order matters! Loaders at the beginning of the list will be executed first.
                    // If a loader depends on the results of another loader then add it before
                    // the other loader.
                    loaders.add(chan4CloudFlareImagePreloader.get());
                    loaders.add(prefetchLoader.get());
                    loaders.add(postExtraContentLoader.get());
                    loaders.add(postHighlightFilterLoader.get());
                    loaders.add(thirdEyeLoader.get());

                    return loaders;
                });

        return new OnDemandContentLoaderManager(
                appScope,
                appConstants,
                Dispatchers.getDefault(),
                loadersLazy,
                chanThreadManager
        );
    }

    @Provides
    @Singleton
    public SeenPostsManager provideSeenPostsManager(
            CoroutineScope appScope,
            ChanThreadsCache chanThreadsCache,
            ChanCatalogSnapshotCache chanCatalogSnapshotCache,
            SeenPostRepository seenPostRepository
    ) {
        Logger.deps("SeenPostsManager");
        return new SeenPostsManager(
                appScope,
                ChanSettings.verboseLogs.get(),
                chanThreadsCache,
                chanCatalogSnapshotCache,
                seenPostRepository
        );
    }

    @Provides
    @Singleton
    public PrefetchStateManager providePrefetchStateManager() {
        Logger.deps("PrefetchStateManager");
        return new PrefetchStateManager();
    }

    @Provides
    @Singleton
    public ApplicationVisibilityManager provideApplicationVisibilityManager() {
        Logger.deps("ApplicationVisibilityManager");
        return new ApplicationVisibilityManager();
    }

    @Provides
    @Singleton
    public HistoryNavigationManager provideHistoryNavigationManager(
            CoroutineScope appScope,
            Lazy<HistoryNavigationRepository> historyNavigationRepository,
            Lazy<ApplicationVisibilityManager> applicationVisibilityManager,
            Lazy<CurrentOpenedDescriptorStateManager> currentOpenedDescriptorStateManager
    ) {
        Logger.deps("HistoryNavigationManager");
        return new HistoryNavigationManager(
                appScope,
                historyNavigationRepository,
                applicationVisibilityManager,
                currentOpenedDescriptorStateManager
        );
    }

    @Provides
    @Singleton
    public PostFilterManager providePostFilterManager(
            CoroutineScope appScope,
            ChanThreadsCache chanThreadsCache
    ) {
        Logger.deps("PostFilterManager");
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
            Lazy<ApplicationVisibilityManager> applicationVisibilityManager,
            Lazy<ArchivesManager> archivesManager,
            Lazy<BookmarksRepository> bookmarksRepository,
            Lazy<CurrentOpenedDescriptorStateManager> currentOpenedDescriptorStateManager
    ) {
        Logger.deps("BookmarksManager");
        return new BookmarksManager(
                AppModuleAndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
                appScope,
                applicationVisibilityManager,
                archivesManager,
                bookmarksRepository,
                SiteRegistry.INSTANCE,
                currentOpenedDescriptorStateManager
        );
    }

    @Provides
    @Singleton
    public ReplyParser provideReplyParser(
            SiteManager siteManager,
            ParserRepository parserRepository
    ) {
        Logger.deps("ReplyParser");
        return new ReplyParser(
                siteManager,
                parserRepository
        );
    }

    @Provides
    @Singleton
    public BookmarkWatcherDelegate provideFetchThreadBookmarkInfoUseCase(
            BookmarksManager bookmarksManager,
            ArchivesManager archivesManager,
            SiteManager siteManager,
            LastViewedPostNoInfoHolder lastViewedPostNoInfoHolder,
            Lazy<FetchThreadBookmarkInfoUseCase> fetchThreadBookmarkInfoUseCase,
            Lazy<ParsePostRepliesUseCase> parsePostRepliesUseCase,
            Lazy<ReplyNotificationsHelper> replyNotificationsHelper,
            Lazy<LastPageNotificationsHelper> lastPageNotificationsHelper,
            CurrentOpenedDescriptorStateManager currentOpenedDescriptorStateManager
    ) {
        Logger.deps("BookmarkWatcherDelegate");
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
                lastPageNotificationsHelper,
                currentOpenedDescriptorStateManager
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
            Lazy<BookmarkWatcherDelegate> bookmarkWatcherDelegate,
            ApplicationVisibilityManager applicationVisibilityManager,
            CurrentOpenedDescriptorStateManager currentOpenedDescriptorStateManager
    ) {
        Logger.deps("BookmarkForegroundWatcher");
        return new BookmarkForegroundWatcher(
                ChanSettings.verboseLogs.get(),
                appScope,
                appContext,
                appConstants,
                bookmarksManager,
                archivesManager,
                bookmarkWatcherDelegate,
                applicationVisibilityManager,
                currentOpenedDescriptorStateManager
        );
    }

    @Provides
    @Singleton
    public BookmarkWatcherCoordinator provideBookmarkWatcherController(
            Context appContext,
            CoroutineScope appScope,
            AppConstants appConstants,
            Lazy<BookmarksManager> bookmarksManager,
            Lazy<BookmarkForegroundWatcher> bookmarkForegroundWatcher
    ) {
        Logger.deps("BookmarkWatcherCoordinator");
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
        Logger.deps("LastViewedPostNoInfoHolder");
        return new LastViewedPostNoInfoHolder();
    }

    @Provides
    @Singleton
    public ReplyNotificationsHelper provideReplyNotificationsHelper(
            Context appContext,
            CoroutineScope appScope,
            NotificationManagerCompat notificationManagerCompat,
            Lazy<BookmarksManager> bookmarksManager,
            Lazy<ChanPostRepository> chanPostRepository,
            Lazy<ImageLoaderV2> imageLoaderV2,
            Lazy<ThemeEngine> themeEngine,
            Lazy<SimpleCommentParser> simpleCommentParser
    ) {
        Logger.deps("ReplyNotificationsHelper");
        return new ReplyNotificationsHelper(
                AppModuleAndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
                appContext,
                appScope,
                notificationManagerCompat,
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
    public FilterWatcherNotificationHelper provideFilterWatcherNotificationHelper(
            Context appContext,
            NotificationManagerCompat notificationManagerCompat,
            Lazy<ThemeEngine> themeEngine
    ) {
        Logger.deps("FilterWatcherNotificationHelper");
        return new FilterWatcherNotificationHelper(
                appContext,
                notificationManagerCompat,
                themeEngine
        );
    }

    @Provides
    @Singleton
    public LastPageNotificationsHelper provideLastPageNotificationsHelper(
            Context appContext,
            NotificationManagerCompat notificationManagerCompat,
            Lazy<PageRequestManager> pageRequestManager,
            BookmarksManager bookmarksManager,
            ThemeEngine themeEngine,
            CurrentOpenedDescriptorStateManager currentOpenedDescriptorStateManager
    ) {
        Logger.deps("LastPageNotificationsHelper");
        return new LastPageNotificationsHelper(
                AppModuleAndroidUtils.isDevBuild(),
                appContext,
                notificationManagerCompat,
                pageRequestManager,
                bookmarksManager,
                themeEngine,
                currentOpenedDescriptorStateManager
        );
    }

    @Provides
    @Singleton
    public ChanThreadViewableInfoManager provideChanThreadViewableInfoManager(
            ChanThreadViewableInfoRepository chanThreadViewableInfoRepository,
            CoroutineScope appScope,
            ChanThreadsCache chanThreadsCache
    ) {
        Logger.deps("ChanThreadViewableInfoManager");
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
        Logger.deps("SavedReplyManager");
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
        Logger.deps("PostHideManager");
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
            CoroutineScope appScope,
            Lazy<ChanFilterRepository> chanFilterRepository,
            Lazy<ChanPostRepository> chanPostRepository,
            Lazy<ChanFilterWatchRepository> chanFilterWatchRepository,
            Lazy<PostFilterManager> postFilterManager,
            Lazy<PostFilterHighlightManager> postFilterHighlightManager
    ) {
        Logger.deps("ChanFilterManager");
        return new ChanFilterManager(
                AppModuleAndroidUtils.isDevBuild(),
                appScope,
                chanFilterRepository,
                chanPostRepository,
                chanFilterWatchRepository,
                postFilterHighlightManager,
                postFilterManager
        );
    }

    @Singleton
    @Provides
    public PostHideHelper providePostHideHelper(
            PostHideManager postHideManager,
            PostFilterManager postFilterManager
    ) {
        Logger.deps("PostHideHelper");
        return new PostHideHelper(
                postHideManager,
                postFilterManager
        );
    }

    @Singleton
    @Provides
    public ThreadBookmarkGroupManager provideThreadBookmarkGroupEntryManager(
            CoroutineScope appScope,
            Lazy<ThreadBookmarkGroupRepository> threadBookmarkGroupEntryRepository,
            Lazy<BookmarksManager> bookmarksManager,
            Lazy<GetThreadBookmarkGroupIdsUseCase> getThreadBookmarkGroupIdsUseCase
    ) {
        Logger.deps("ThreadBookmarkGroupManager");
        return new ThreadBookmarkGroupManager(
                appScope,
                ChanSettings.verboseLogs.get(),
                threadBookmarkGroupEntryRepository,
                bookmarksManager,
                getThreadBookmarkGroupIdsUseCase
        );
    }

    @Singleton
    @Provides
    public Chan4CloudFlareImagePreloaderManager provideChan4CloudFlareImagePreloaderManager(
            CoroutineScope appScope,
            RealProxiedOkHttpClient realProxiedOkHttpClient,
            ChanThreadsCache chanThreadsCache
    ) {
        Logger.deps("Chan4CloudFlareImagePreloaderManager");
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
            Lazy<SiteManager> siteManager,
            Lazy<BookmarksManager> bookmarksManager,
            Lazy<PostFilterManager> postFilterManager,
            Lazy<SavedReplyManager> savedReplyManager,
            Lazy<ChanThreadsCache> chanThreadsCache,
            Lazy<ChanPostRepository> chanPostRepository,
            Lazy<ChanThreadLoaderCoordinator> chanThreadLoaderCoordinator,
            Lazy<ThreadDataPreloader> threadDataPreloadUseCase,
            Lazy<CatalogDataPreloader> catalogDataPreloadUseCase
    ) {
        Logger.deps("ChanThreadManager");
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
        Logger.deps("PostingLimitationsInfoManager");
        return new PostingLimitationsInfoManager(siteManager);
    }

    @Singleton
    @Provides
    public FilterWatcherCoordinator provideFilterWatcherCoordinator(
            Context appContext,
            CoroutineScope appScope,
            AppConstants appConstants,
            Lazy<ChanFilterManager> chanFilterManager
    ) {
        Logger.deps("FilterWatcherCoordinator");
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
            BookmarkFilterWatchableThreadsUseCase bookmarkFilterWatchableThreadsUseCase,
            FilterWatcherNotificationHelper filterWatcherNotificationHelper
    ) {
        Logger.deps("FilterWatcherDelegate");
        return new FilterWatcherDelegate(
                AppModuleAndroidUtils.isDevBuild(),
                appScope,
                boardManager,
                bookmarksManager,
                chanFilterManager,
                chanPostRepository,
                siteManager,
                bookmarkFilterWatchableThreadsUseCase,
                filterWatcherNotificationHelper
        );
    }

    @Singleton
    @Provides
    public ImageSaverV2ServiceDelegate provideImageSaverV2Delegate(
            CoroutineScope appScope,
            AppConstants appConstants,
            Lazy<CacheHandler> cacheHandler,
            Lazy<RealDownloaderOkHttpClient> downloaderOkHttpClient,
            NotificationManagerCompat notificationManagerCompat,
            ImageSaverFileManagerWrapper imageSaverFileManagerWrapper,
            SiteResolver siteResolver,
            ChanPostImageRepository chanPostImageRepository,
            ImageDownloadRequestRepository imageDownloadRequestRepository,
            ChanThreadManager chanThreadManager,
            ThreadDownloadManager threadDownloadManager,
            NotificationAutoDismissManager notificationAutoDismissManager
    ) {
        Logger.deps("ImageSaverV2ServiceDelegate");

        return new ImageSaverV2ServiceDelegate(
                ChanSettings.verboseLogs.get(),
                appScope,
                appConstants,
                cacheHandler,
                downloaderOkHttpClient,
                notificationManagerCompat,
                imageSaverFileManagerWrapper,
                siteResolver,
                chanPostImageRepository,
                imageDownloadRequestRepository,
                chanThreadManager,
                threadDownloadManager,
                notificationAutoDismissManager
        );
    }

    @Singleton
    @Provides
    public TwoCaptchaSolver provideTwoCaptchaSolver(
            Gson gson,
            SiteManager siteManager,
            Lazy<ProxiedOkHttpClient> proxiedOkHttpClient
    ) {
        Logger.deps("TwoCaptchaSolver");
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
            Lazy<ReplyManager> replyManager,
            Lazy<SiteManager> siteManager,
            Lazy<BoardManager> boardManager,
            Lazy<BookmarksManager> bookmarksManager,
            Lazy<SavedReplyManager> savedReplyManager,
            Lazy<ChanThreadManager> chanThreadManager,
            Lazy<LastReplyRepository> lastReplyRepository,
            Lazy<ChanPostRepository> chanPostRepository,
            Lazy<TwoCaptchaSolver> twoCaptchaSolver,
            Lazy<CaptchaHolder> captchaHolder,
            Lazy<CaptchaDonation> captchaDonation
    ) {
        Logger.deps("PostingServiceDelegate");
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
                captchaHolder,
                captchaDonation
        );
    }

    @Singleton
    @Provides
    public ThreadDownloadManager provideThreadDownloadManager(
            AppConstants appConstants,
            CoroutineScope appScope,
            Lazy<ThreadDownloaderFileManagerWrapper> threadDownloaderFileManagerWrapper,
            Lazy<ThreadDownloadRepository> threadDownloadRepository,
            Lazy<ChanPostRepository> chanPostRepository
    ) {
        Logger.deps("ThreadDownloadManager");
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
            Lazy<ThreadDownloadManager> threadDownloadManager
    ) {
        Logger.deps("ThreadDownloadingCoordinator");
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
            Lazy<RealDownloaderOkHttpClient> realDownloaderOkHttpClient,
            SiteManager siteManager,
            SiteResolver siteResolver,
            ThreadDownloadManager threadDownloadManager,
            ChanPostRepository chanPostRepository,
            ChanPostImageRepository chanPostImageRepository,
            ThreadDownloaderFileManagerWrapper threadDownloaderFileManagerWrapper,
            ThreadDownloadProgressNotifier threadDownloadProgressNotifier,
            ThreadDownloaderPersistPostsInDatabaseUseCase threadDownloaderPersistPostsInDatabaseUseCase
    ) {
        Logger.deps("ThreadDownloadingDelegate");
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

    @Singleton
    @Provides
    public CurrentOpenedDescriptorStateManager provideCurrentOpenedDescriptorStateManager() {
        Logger.deps("CurrentOpenedDescriptorStateManager");
        return new CurrentOpenedDescriptorStateManager();
    }

    @Singleton
    @Provides
    public CompositeCatalogManager provideCompositeCatalogManager(
            CompositeCatalogRepository compositeCatalogRepository,
            CurrentOpenedDescriptorStateManager currentOpenedDescriptorStateManager
    ) {
        Logger.deps("CompositeCatalogManager");
        return new CompositeCatalogManager(
                compositeCatalogRepository,
                currentOpenedDescriptorStateManager
        );
    }

    @Singleton
    @Provides
    public PostFilterHighlightManager providePostFilterHighlightManager() {
        Logger.deps("PostFilterHighlightManager");
        return new PostFilterHighlightManager();
    }

    @Singleton
    @Provides
    public ThirdEyeManager provideThirdEyeManager(
            Context appContext,
            ChanThreadsCache chanThreadsCache,
            AppConstants appConstants,
            Moshi moshi,
            FileManager fileManager
    ) {
        Logger.deps("ThirdEyeManager");

        return new ThirdEyeManager(
                appContext,
                ChanSettings.verboseLogs.get(),
                appConstants,
                moshi,
                chanThreadsCache,
                fileManager
        );
    }

    @Singleton
    @Provides
    public FirewallBypassManager provideFirewallBypassManager(
            CoroutineScope appScope,
            ApplicationVisibilityManager applicationVisibilityManager
    ) {
        Logger.deps("FirewallBypassManager");

        return new FirewallBypassManager(
                appScope,
                applicationVisibilityManager
        );
    }

    @Singleton
    @Provides
    public CaptchaDonation provideCaptchaDonation(
            CoroutineScope appScope,
            CaptchaImageCache captchaImageCache,
            RealProxiedOkHttpClient proxiedOkHttpClient
    ) {
        Logger.deps("CaptchaDonation");

        return new CaptchaDonation(
                appScope,
                captchaImageCache,
                proxiedOkHttpClient
        );
    }

    @Singleton
    @Provides
    public CaptchaImageCache provideCaptchaImageCache() {
        Logger.deps("CaptchaImageCache");

        return new CaptchaImageCache();
    }

    @Singleton
    @Provides
    public ApplicationCrashNotifier provideApplicationCrashNotifier() {
        Logger.deps("ApplicationCrashNotifier");

        return new ApplicationCrashNotifier();
    }

    @Singleton
    @Provides
    public NotificationAutoDismissManager provideNotificationAutoDismissManager(
            CoroutineScope appScope,
            NotificationManagerCompat notificationManagerCompat
    ) {
        Logger.deps("NotificationAutoDismissManager");

        return new NotificationAutoDismissManager(
                appScope,
                notificationManagerCompat
        );
    }

}
