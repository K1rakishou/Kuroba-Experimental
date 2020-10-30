package com.github.k1rakishou.chan.core.di.component.application;

import android.content.Context;

import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.chan.core.di.component.activity.StartActivityComponent;
import com.github.k1rakishou.chan.core.di.module.application.AppModule;
import com.github.k1rakishou.chan.core.di.module.application.ExecutorsModule;
import com.github.k1rakishou.chan.core.di.module.application.GsonModule;
import com.github.k1rakishou.chan.core.di.module.application.LoaderModule;
import com.github.k1rakishou.chan.core.di.module.application.ManagerModule;
import com.github.k1rakishou.chan.core.di.module.application.NetModule;
import com.github.k1rakishou.chan.core.di.module.application.ParserModule;
import com.github.k1rakishou.chan.core.di.module.application.RepositoryModule;
import com.github.k1rakishou.chan.core.di.module.application.RoomDatabaseModule;
import com.github.k1rakishou.chan.core.di.module.application.SiteModule;
import com.github.k1rakishou.chan.core.di.module.application.UseCaseModule;
import com.github.k1rakishou.chan.core.site.SiteBase;
import com.github.k1rakishou.chan.features.bookmarks.watcher.BookmarkBackgroundWatcherWorker;
import com.github.k1rakishou.chan.ui.theme.ThemeEngine;
import com.github.k1rakishou.chan.ui.widget.SnackbarWrapper;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.model.di.ModelMainComponent;

import org.jetbrains.annotations.NotNull;

import javax.inject.Singleton;

import dagger.BindsInstance;
import dagger.Component;
import kotlinx.coroutines.CoroutineScope;
import okhttp3.Dns;

@Singleton
@Component(modules = {
        AppModule.class,
        ExecutorsModule.class,
        GsonModule.class,
        LoaderModule.class,
        ManagerModule.class,
        NetModule.class,
        ParserModule.class,
        RepositoryModule.class,
        RoomDatabaseModule.class,
        SiteModule.class,
        UseCaseModule.class
})
public interface ApplicationComponent {
    Chan getApplication();
    ThemeEngine getThemeEngine();
    StartActivityComponent.Builder activityComponentBuilder();

    void inject(@NotNull Chan application);
    void inject(@NotNull BookmarkBackgroundWatcherWorker bookmarkBackgroundWatcherWorker);
    void inject(@NotNull SiteBase siteBase);
    void inject(@NotNull SnackbarWrapper snackbarWrapper);

    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder appContext(Context application);
        @BindsInstance
        Builder application(Chan application);
        @BindsInstance
        Builder applicationCoroutineScope(CoroutineScope applicationCoroutineScope);
        @BindsInstance
        Builder okHttpDns(Dns okHttpDns);
        @BindsInstance
        Builder okHttpProtocols(Chan.OkHttpProtocols okHttpProtocols);
        @BindsInstance
        Builder appConstants(AppConstants appConstants);
        @BindsInstance
        Builder modelMainComponent(ModelMainComponent modelMainComponent);
        @BindsInstance
        Builder appModule(AppModule appModule);
        @BindsInstance
        Builder executorsModule(ExecutorsModule executorsModule);
        @BindsInstance
        Builder gsonModule(GsonModule gsonModule);
        @BindsInstance
        Builder loaderModule(LoaderModule loaderModule);
        @BindsInstance
        Builder managerModule(ManagerModule managerModule);
        @BindsInstance
        Builder netModule(NetModule netModule);
        @BindsInstance
        Builder parserModule(ParserModule parserModule);
        @BindsInstance
        Builder repositoryModule(RepositoryModule repositoryModule);
        @BindsInstance
        Builder roomDatabaseModule(RoomDatabaseModule roomDatabaseModule);
        @BindsInstance
        Builder siteModule(SiteModule siteModule);
        @BindsInstance
        Builder useCaseModule(UseCaseModule useCaseModule);

        ApplicationComponent build();
    }
}
