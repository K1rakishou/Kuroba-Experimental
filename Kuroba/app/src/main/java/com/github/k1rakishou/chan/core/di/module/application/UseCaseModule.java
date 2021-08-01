package com.github.k1rakishou.chan.core.di.module.application;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild;

import android.content.Context;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier;
import com.github.k1rakishou.chan.core.helper.FilterEngine;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.BookmarksManager;
import com.github.k1rakishou.chan.core.manager.ChanFilterManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager;
import com.github.k1rakishou.chan.core.manager.PostFilterManager;
import com.github.k1rakishou.chan.core.manager.PostHideManager;
import com.github.k1rakishou.chan.core.manager.SavedReplyManager;
import com.github.k1rakishou.chan.core.manager.SeenPostsManager;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator;
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsV1UseCase;
import com.github.k1rakishou.chan.core.site.parser.ReplyParser;
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser;
import com.github.k1rakishou.chan.core.usecase.BookmarkFilterWatchableThreadsUseCase;
import com.github.k1rakishou.chan.core.usecase.CatalogDataPreloadUseCase;
import com.github.k1rakishou.chan.core.usecase.CreateBoardManuallyUseCase;
import com.github.k1rakishou.chan.core.usecase.DownloadThemeJsonFilesUseCase;
import com.github.k1rakishou.chan.core.usecase.ExportBackupFileUseCase;
import com.github.k1rakishou.chan.core.usecase.ExportDownloadedThreadAsHtmlUseCase;
import com.github.k1rakishou.chan.core.usecase.ExtractPostMapInfoHolderUseCase;
import com.github.k1rakishou.chan.core.usecase.FetchThreadBookmarkInfoUseCase;
import com.github.k1rakishou.chan.core.usecase.FilterOutHiddenImagesUseCase;
import com.github.k1rakishou.chan.core.usecase.GlobalSearchUseCase;
import com.github.k1rakishou.chan.core.usecase.ImportBackupFileUseCase;
import com.github.k1rakishou.chan.core.usecase.KurobaSettingsImportUseCase;
import com.github.k1rakishou.chan.core.usecase.ParsePostRepliesUseCase;
import com.github.k1rakishou.chan.core.usecase.SearxImageSearchUseCase;
import com.github.k1rakishou.chan.core.usecase.ThreadDataPreloadUseCase;
import com.github.k1rakishou.chan.core.usecase.ThreadDownloaderPersistPostsInDatabaseUseCase;
import com.github.k1rakishou.chan.core.usecase.TwoCaptchaCheckBalanceUseCase;
import com.github.k1rakishou.chan.features.posting.solvers.two_captcha.TwoCaptchaSolver;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository;
import com.github.k1rakishou.model.repository.ChanFilterWatchRepository;
import com.github.k1rakishou.model.repository.ChanPostRepository;
import com.github.k1rakishou.model.repository.ChanSavedReplyRepository;
import com.github.k1rakishou.model.repository.DatabaseMetaRepository;
import com.google.gson.Gson;
import com.squareup.moshi.Moshi;

import javax.inject.Singleton;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import kotlinx.coroutines.CoroutineScope;

@Module
public class UseCaseModule {

    @Provides
    @Singleton
    public ExtractPostMapInfoHolderUseCase provideExtractReplyPostsPositionsFromPostsListUseCase(
            SavedReplyManager savedReplyManager,
            SiteManager siteManager,
            ChanThreadManager chanThreadManager
    ) {
        Logger.deps("ExtractPostMapInfoHolderUseCase");
        return new ExtractPostMapInfoHolderUseCase(
                savedReplyManager,
                siteManager,
                chanThreadManager
        );
    }

    @Provides
    @Singleton
    public FetchThreadBookmarkInfoUseCase provideFetchThreadBookmarkInfoUseCase(
            CoroutineScope appScope,
            Lazy<ProxiedOkHttpClient> okHttpClient,
            SiteManager siteManager,
            BookmarksManager bookmarksManager,
            AppConstants appConstants

    ) {
        Logger.deps("FetchThreadBookmarkInfoUseCase");
        return new FetchThreadBookmarkInfoUseCase(
                isDevBuild(),
                ChanSettings.verboseLogs.get(),
                appScope,
                okHttpClient,
                siteManager,
                bookmarksManager,
                appConstants
        );
    }

    @Provides
    @Singleton
    public ParsePostRepliesUseCase provideParsePostRepliesUseCase(
            CoroutineScope appScope,
            Lazy<ReplyParser> replyParser,
            SiteManager siteManager,
            Lazy<ChanSavedReplyRepository> chanSavedReplyRepository
    ) {
        Logger.deps("ParsePostRepliesUseCase");
        return new ParsePostRepliesUseCase(
                appScope,
                replyParser,
                siteManager,
                chanSavedReplyRepository
        );
    }

    @Provides
    @Singleton
    public KurobaSettingsImportUseCase provideKurobaSettingsImportUseCase(
            Gson gson,
            FileManager fileManager,
            SiteManager siteManager,
            BoardManager boardManager,
            ChanFilterManager chanFilterManager,
            PostHideManager postHideManager,
            BookmarksManager bookmarksManager,
            ChanPostRepository chanPostRepository
    ) {
        Logger.deps("KurobaSettingsImportUseCase");
        return new KurobaSettingsImportUseCase(
                gson,
                fileManager,
                siteManager,
                boardManager,
                chanFilterManager,
                postHideManager,
                bookmarksManager,
                chanPostRepository
        );
    }

    @Provides
    @Singleton
    public GlobalSearchUseCase provideGlobalSearchUseCase(
            SiteManager siteManager,
            ThemeEngine themeEngine,
            Lazy<SimpleCommentParser> simpleCommentParser
    ) {
        Logger.deps("GlobalSearchUseCase");
        return new GlobalSearchUseCase(
                siteManager,
                themeEngine,
                simpleCommentParser
        );
    }

    @Provides
    @Singleton
    public FilterOutHiddenImagesUseCase provideFilterOutHiddenImagesUseCase(
            PostHideManager postHideManager,
            PostFilterManager postFilterManager
    ) {
        Logger.deps("FilterOutHiddenImagesUseCase");
        return new FilterOutHiddenImagesUseCase(
                postHideManager,
                postFilterManager
        );
    }

    @Provides
    @Singleton
    public BookmarkFilterWatchableThreadsUseCase provideBookmarkFilterWatchableThreadsUseCase(
            AppConstants appConstants,
            CoroutineScope appScope,
            BoardManager boardManager,
            BookmarksManager bookmarksManager,
            ChanFilterManager chanFilterManager,
            SiteManager siteManager,
            Lazy<ProxiedOkHttpClient> proxiedOkHttpClient,
            Lazy<SimpleCommentParser> simpleCommentParser,
            FilterEngine filterEngine,
            ChanPostRepository chanPostRepository,
            ChanFilterWatchRepository chanFilterWatchRepository
    ) {
        Logger.deps("BookmarkFilterWatchableThreadsUseCase");
        return new BookmarkFilterWatchableThreadsUseCase(
                ChanSettings.verboseLogs.get(),
                appConstants,
                boardManager,
                bookmarksManager,
                chanFilterManager,
                siteManager,
                appScope,
                proxiedOkHttpClient,
                simpleCommentParser,
                filterEngine,
                chanPostRepository,
                chanFilterWatchRepository
        );
    }

    @Provides
    @Singleton
    public ExportBackupFileUseCase provideExportBackupFileUseCase(
            Context appContext,
            DatabaseMetaRepository databaseMetaRepository,
            FileManager fileManager
    ) {
        Logger.deps("ExportBackupFileUseCase");
        return new ExportBackupFileUseCase(
                appContext,
                databaseMetaRepository,
                fileManager
        );
    }

    @Provides
    @Singleton
    public ImportBackupFileUseCase provideImportBackupFileUseCase(
            Context appContext,
            FileManager fileManager
    ) {
        Logger.deps("ImportBackupFileUseCase");
        return new ImportBackupFileUseCase(
                appContext,
                fileManager
        );
    }

    @Provides
    @Singleton
    public CreateBoardManuallyUseCase provideCreateBoardManuallyUseCase(
            SiteManager siteManager,
            RealProxiedOkHttpClient proxiedOkHttpClient
    ) {
        Logger.deps("CreateBoardManuallyUseCase");
        return new CreateBoardManuallyUseCase(
                siteManager,
                proxiedOkHttpClient
        );
    }

    @Provides
    @Singleton
    public TwoCaptchaCheckBalanceUseCase provideTwoCaptchaCheckBalanceUseCase(
            Lazy<TwoCaptchaSolver> twoCaptchaSolver
    ) {
        Logger.deps("TwoCaptchaCheckBalanceUseCase");
        return new TwoCaptchaCheckBalanceUseCase(twoCaptchaSolver);
    }

    @Provides
    @Singleton
    public DownloadThemeJsonFilesUseCase provideDownloadThemeJsonFilesUseCase(
            RealProxiedOkHttpClient proxiedOkHttpClient,
            Gson gson,
            ThemeEngine themeEngine
    ) {
        Logger.deps("DownloadThemeJsonFilesUseCase");
        return new DownloadThemeJsonFilesUseCase(
                proxiedOkHttpClient,
                gson,
                themeEngine
        );
    }

    @Provides
    @Singleton
    public ExportDownloadedThreadAsHtmlUseCase provideExportDownloadedThreadAsHtmlUseCase(
            Context appContext,
            AppConstants appConstants,
            FileManager fileManager,
            ChanPostRepository chanPostRepository
    ) {
        Logger.deps("ExportDownloadedThreadAsHtmlUseCase");
        return new ExportDownloadedThreadAsHtmlUseCase(
                appContext,
                appConstants,
                fileManager,
                chanPostRepository
        );
    }

    @Provides
    @Singleton
    public ThreadDownloaderPersistPostsInDatabaseUseCase provideThreadDownloaderPersistPostsInDatabaseUseCase(
            SiteManager siteManager,
            Lazy<ChanThreadLoaderCoordinator> chanThreadLoaderCoordinator,
            ParsePostsV1UseCase parsePostsV1UseCase,
            ChanPostRepository chanPostRepository,
            RealProxiedOkHttpClient proxiedOkHttpClient
    ) {
        Logger.deps("ThreadDownloaderPersistPostsInDatabaseUseCase");
        return new ThreadDownloaderPersistPostsInDatabaseUseCase(
                siteManager,
                chanThreadLoaderCoordinator,
                parsePostsV1UseCase,
                chanPostRepository,
                proxiedOkHttpClient
        );
    }

    @Provides
    @Singleton
    public ParsePostsV1UseCase provideParsePostsV1UseCase(
            ChanPostRepository chanPostRepository,
            FilterEngine filterEngine,
            PostFilterManager postFilterManager,
            SavedReplyManager savedReplyManager,
            BoardManager boardManager,
            ChanLoadProgressNotifier chanLoadProgressNotifier
    ) {
        Logger.deps("ParsePostsV1UseCase");
        return new ParsePostsV1UseCase(
                ChanSettings.verboseLogs.get(),
                chanPostRepository,
                filterEngine,
                postFilterManager,
                savedReplyManager,
                boardManager,
                chanLoadProgressNotifier
        );
    }

    @Provides
    @Singleton
    public SearxImageSearchUseCase provideSearxImageSearchUseCase(
            RealProxiedOkHttpClient proxiedOkHttpClient,
            Moshi moshi
    ) {
        Logger.deps("SearxImageSearchUseCase");
        return new SearxImageSearchUseCase(
                proxiedOkHttpClient,
                moshi
        );
    }

    @Provides
    @Singleton
    public ThreadDataPreloadUseCase provideThreadDataPreloadUseCase(
            Lazy<SeenPostsManager> seenPostsManager,
            Lazy<ChanThreadViewableInfoManager> chanThreadViewableInfoManager,
            Lazy<SavedReplyManager> savedReplyManager,
            Lazy<PostHideManager> postHideManager,
            Lazy<ChanPostRepository> chanPostRepository
    ) {
        Logger.deps("ThreadDataPreloadUseCase");
        return new ThreadDataPreloadUseCase(
                seenPostsManager,
                chanThreadViewableInfoManager,
                savedReplyManager,
                postHideManager,
                chanPostRepository
        );
    }

    @Provides
    @Singleton
    public CatalogDataPreloadUseCase provideCatalogDataPreloadUseCase(
            BoardManager boardManager,
            PostHideManager postHideManager,
            ChanCatalogSnapshotRepository chanCatalogSnapshotRepository
    ) {
        Logger.deps("CatalogDataPreloadUseCase");
        return new CatalogDataPreloadUseCase(
                boardManager,
                postHideManager,
                chanCatalogSnapshotRepository
        );
    }

}
