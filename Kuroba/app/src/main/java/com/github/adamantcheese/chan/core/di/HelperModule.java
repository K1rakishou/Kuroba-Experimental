package com.github.adamantcheese.chan.core.di;

import com.github.adamantcheese.chan.core.helper.PostHideHelper;
import com.github.adamantcheese.chan.core.manager.PostFilterManager;
import com.github.adamantcheese.chan.core.manager.PostHideManager;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.feather2.Provides;

import javax.inject.Singleton;

public class HelperModule {

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

}
