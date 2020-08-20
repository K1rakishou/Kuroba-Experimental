package com.github.adamantcheese.chan.core.di;

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.manager.BookmarksManager;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.parser.ReplyParser;
import com.github.adamantcheese.chan.core.usecase.ExtractPostMapInfoHolderUseCase;
import com.github.adamantcheese.chan.core.usecase.FetchThreadBookmarkInfoUseCase;
import com.github.adamantcheese.chan.core.usecase.LoadArchiveInfoListUseCase;
import com.github.adamantcheese.chan.core.usecase.ParsePostRepliesUseCase;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.common.AppConstants;
import com.github.k1rakishou.feather2.Provides;

import javax.inject.Singleton;

import kotlinx.coroutines.CoroutineScope;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getFlavorType;

public class UseCaseModule {

    @Singleton
    @Provides
    public LoadArchiveInfoListUseCase provideLoadArchiveInfoListUseCase(
            AppConstants appConstants,
            ArchivesManager archivesManager
    ) {
        Logger.d(AppModule.DI_TAG, "LoadArchiveInfoListUseCase");

        return new LoadArchiveInfoListUseCase(appConstants, archivesManager);
    }

    @Singleton
    @Provides
    public ExtractPostMapInfoHolderUseCase provideExtractReplyPostsPositionsFromPostsListUseCase(
            DatabaseManager databaseManager,
            SiteRepository siteRepository
    ) {
        Logger.d(AppModule.DI_TAG, "ExtractPostMapInfoHolderUseCase");

        return new ExtractPostMapInfoHolderUseCase(
                databaseManager.getDatabaseSavedReplyManager(),
                siteRepository
        );
    }

    @Singleton
    @Provides
    public FetchThreadBookmarkInfoUseCase provideFetchThreadBookmarkInfoUseCase(
            CoroutineScope appScope,
            NetModule.ProxiedOkHttpClient okHttpClient,
            SiteRepository siteRepository,
            BookmarksManager bookmarksManager

    ) {
        Logger.d(AppModule.DI_TAG, "FetchThreadBookmarkInfoUseCase");

        return new FetchThreadBookmarkInfoUseCase(
                getFlavorType() == AndroidUtils.FlavorType.Dev,
                ChanSettings.verboseLogs.get(),
                appScope,
                okHttpClient,
                siteRepository,
                bookmarksManager
        );
    }

    @Singleton
    @Provides
    public ParsePostRepliesUseCase provideParsePostRepliesUseCase(
            CoroutineScope appScope,
            ReplyParser replyParser,
            SiteRepository siteRepository,
            DatabaseManager databaseManager
    ) {
        Logger.d(AppModule.DI_TAG, "ParsePostRepliesUseCase");

        return new ParsePostRepliesUseCase(
                appScope,
                replyParser,
                siteRepository,
                databaseManager.getDatabaseSavedReplyManager()
        );
    }

}
