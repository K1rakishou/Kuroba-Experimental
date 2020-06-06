package com.github.adamantcheese.chan.core.di;

import com.github.adamantcheese.chan.core.interactors.LoadArchiveInfoListInteractor;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.common.AppConstants;
import com.github.k1rakishou.feather2.Provides;

import javax.inject.Singleton;

public class InteractorsModule {

    @Singleton
    @Provides
    public LoadArchiveInfoListInteractor provideLoadArchiveInfoListInteractor(
            AppConstants appConstants,
            ArchivesManager archivesManager
    ) {
        Logger.d(AppModule.DI_TAG, "LoadArchiveInfoListInteractor");

        return new LoadArchiveInfoListInteractor(appConstants, archivesManager);
    }

}
