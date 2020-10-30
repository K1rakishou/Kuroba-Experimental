package com.github.k1rakishou.chan.core.di.module.application;

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.BookmarksManager;
import com.github.k1rakishou.chan.core.manager.ChanFilterManager;
import com.github.k1rakishou.chan.core.manager.PostHideManager;
import com.github.k1rakishou.chan.core.manager.SavedReplyManager;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.settings.ChanSettings;
import com.github.k1rakishou.chan.core.site.parser.ReplyParser;
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser;
import com.github.k1rakishou.chan.core.usecase.ExtractPostMapInfoHolderUseCase;
import com.github.k1rakishou.chan.core.usecase.FetchThreadBookmarkInfoUseCase;
import com.github.k1rakishou.chan.core.usecase.GlobalSearchUseCase;
import com.github.k1rakishou.chan.core.usecase.KurobaSettingsImportUseCase;
import com.github.k1rakishou.chan.core.usecase.ParsePostRepliesUseCase;
import com.github.k1rakishou.chan.ui.theme.ThemeEngine;
import com.github.k1rakishou.chan.utils.AndroidUtils;
import com.github.k1rakishou.chan.utils.Logger;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.model.repository.ChanPostRepository;

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
            SiteManager siteManager
    ) {
        Logger.d(AppModule.DI_TAG, "ExtractPostMapInfoHolderUseCase");

        return new ExtractPostMapInfoHolderUseCase(
                savedReplyManager,
                siteManager
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
        Logger.d(AppModule.DI_TAG, "FetchThreadBookmarkInfoUseCase");

        return new FetchThreadBookmarkInfoUseCase(
                AndroidUtils.isDevBuild(),
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
        Logger.d(AppModule.DI_TAG, "ParsePostRepliesUseCase");

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
            FileManager fileManager,
            SiteManager siteManager,
            BoardManager boardManager,
            ChanFilterManager chanFilterManager,
            PostHideManager postHideManager,
            BookmarksManager bookmarksManager,
            ChanPostRepository chanPostRepository
    ) {
        Logger.d(AppModule.DI_TAG, "KurobaSettingsImportUseCase");

        return new KurobaSettingsImportUseCase(
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
        Logger.d(AppModule.DI_TAG, "GlobalSearchUseCase");

        return new GlobalSearchUseCase(
                siteManager,
                themeEngine,
                simpleCommentParser
        );
    }

}
