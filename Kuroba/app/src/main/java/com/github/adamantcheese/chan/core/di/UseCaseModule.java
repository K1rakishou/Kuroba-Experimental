package com.github.adamantcheese.chan.core.di;

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.interactors.ExtractPostMapInfoHolderUseCase;
import com.github.adamantcheese.chan.core.interactors.LoadArchiveInfoListUseCase;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.common.AppConstants;
import com.github.k1rakishou.feather2.Provides;

import javax.inject.Singleton;

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
            DatabaseManager databaseManager
    ) {
        return new ExtractPostMapInfoHolderUseCase(
                databaseManager.getDatabaseSavedReplyManager()
        );
    }

}
