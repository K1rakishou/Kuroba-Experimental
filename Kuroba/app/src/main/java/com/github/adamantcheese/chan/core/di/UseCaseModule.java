package com.github.adamantcheese.chan.core.di;

import com.github.adamantcheese.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.manager.BookmarksManager;
import com.github.adamantcheese.chan.core.manager.ChanFilterManager;
import com.github.adamantcheese.chan.core.manager.PostHideManager;
import com.github.adamantcheese.chan.core.manager.SavedReplyManager;
import com.github.adamantcheese.chan.core.manager.SiteManager;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.parser.ReplyParser;
import com.github.adamantcheese.chan.core.site.parser.search.Chan4SearchPostParser;
import com.github.adamantcheese.chan.core.usecase.ExtractPostMapInfoHolderUseCase;
import com.github.adamantcheese.chan.core.usecase.FetchThreadBookmarkInfoUseCase;
import com.github.adamantcheese.chan.core.usecase.GlobalSearchUseCase;
import com.github.adamantcheese.chan.core.usecase.KurobaSettingsImportUseCase;
import com.github.adamantcheese.chan.core.usecase.ParsePostRepliesUseCase;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.model.repository.ChanPostRepository;
import com.github.k1rakishou.feather2.Provides;
import com.github.k1rakishou.fsaf.FileManager;

import javax.inject.Singleton;

import kotlinx.coroutines.CoroutineScope;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getFlavorType;

public class UseCaseModule {

    @Singleton
    @Provides
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

    @Singleton
    @Provides
    public FetchThreadBookmarkInfoUseCase provideFetchThreadBookmarkInfoUseCase(
            CoroutineScope appScope,
            ProxiedOkHttpClient okHttpClient,
            SiteManager siteManager,
            BookmarksManager bookmarksManager

    ) {
        Logger.d(AppModule.DI_TAG, "FetchThreadBookmarkInfoUseCase");

        return new FetchThreadBookmarkInfoUseCase(
                getFlavorType() == AndroidUtils.FlavorType.Dev,
                ChanSettings.verboseLogs.get(),
                appScope,
                okHttpClient,
                siteManager,
                bookmarksManager
        );
    }

    @Singleton
    @Provides
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

    @Singleton
    @Provides
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

    @Singleton
    @Provides
    public GlobalSearchUseCase provideGlobalSearchUseCase(
            SiteManager siteManager,
            ThemeHelper themeHelper
    ) {
        Logger.d(AppModule.DI_TAG, "GlobalSearchUseCase");

        return new GlobalSearchUseCase(
                siteManager,
                themeHelper,
                new Chan4SearchPostParser()
        );
    }

}
