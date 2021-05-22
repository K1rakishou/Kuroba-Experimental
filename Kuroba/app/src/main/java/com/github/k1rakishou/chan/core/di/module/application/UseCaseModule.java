package com.github.k1rakishou.chan.core.di.module.application;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild;

import android.content.Context;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.helper.FilterEngine;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.BookmarksManager;
import com.github.k1rakishou.chan.core.manager.ChanFilterManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadManager;
import com.github.k1rakishou.chan.core.manager.PostFilterManager;
import com.github.k1rakishou.chan.core.manager.PostHideManager;
import com.github.k1rakishou.chan.core.manager.SavedReplyManager;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.site.parser.ReplyParser;
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser;
import com.github.k1rakishou.chan.core.usecase.BookmarkFilterWatchableThreadsUseCase;
import com.github.k1rakishou.chan.core.usecase.CreateBoardManuallyUseCase;
import com.github.k1rakishou.chan.core.usecase.DownloadThemeJsonFilesUseCase;
import com.github.k1rakishou.chan.core.usecase.ExportBackupFileUseCase;
import com.github.k1rakishou.chan.core.usecase.ExtractPostMapInfoHolderUseCase;
import com.github.k1rakishou.chan.core.usecase.FetchThreadBookmarkInfoUseCase;
import com.github.k1rakishou.chan.core.usecase.FilterOutHiddenImagesUseCase;
import com.github.k1rakishou.chan.core.usecase.GlobalSearchUseCase;
import com.github.k1rakishou.chan.core.usecase.ImportBackupFileUseCase;
import com.github.k1rakishou.chan.core.usecase.KurobaSettingsImportUseCase;
import com.github.k1rakishou.chan.core.usecase.ParsePostRepliesUseCase;
import com.github.k1rakishou.chan.core.usecase.TwoCaptchaCheckBalanceUseCase;
import com.github.k1rakishou.chan.features.posting.solvers.two_captcha.TwoCaptchaSolver;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.model.repository.ChanFilterWatchRepository;
import com.github.k1rakishou.model.repository.ChanPostRepository;
import com.github.k1rakishou.model.repository.DatabaseMetaRepository;
import com.google.gson.Gson;

import javax.inject.Singleton;

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
            ProxiedOkHttpClient okHttpClient,
            SiteManager siteManager,
            BookmarksManager bookmarksManager,
            AppConstants appConstants

    ) {
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
            ReplyParser replyParser,
            SiteManager siteManager,
            SavedReplyManager savedReplyManager
    ) {
        return new ParsePostRepliesUseCase(
                appScope,
                replyParser,
                siteManager,
                savedReplyManager
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
            SimpleCommentParser simpleCommentParser
    ) {
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
            ProxiedOkHttpClient proxiedOkHttpClient,
            SimpleCommentParser simpleCommentParser,
            FilterEngine filterEngine,
            ChanPostRepository chanPostRepository,
            ChanFilterWatchRepository chanFilterWatchRepository
    ) {
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
        return new CreateBoardManuallyUseCase(
                siteManager,
                proxiedOkHttpClient
        );
    }

    @Provides
    @Singleton
    public TwoCaptchaCheckBalanceUseCase provideTwoCaptchaCheckBalanceUseCase(
            TwoCaptchaSolver twoCaptchaSolver
    ) {
        return new TwoCaptchaCheckBalanceUseCase(twoCaptchaSolver);
    }

    @Provides
    @Singleton
    public DownloadThemeJsonFilesUseCase provideDownloadThemeJsonFilesUseCase(
            RealProxiedOkHttpClient proxiedOkHttpClient,
            Gson gson,
            ThemeEngine themeEngine
    ) {
        return new DownloadThemeJsonFilesUseCase(
                proxiedOkHttpClient,
                gson,
                themeEngine
        );
    }

}
