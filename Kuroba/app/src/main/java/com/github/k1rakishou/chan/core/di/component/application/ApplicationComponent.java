package com.github.k1rakishou.chan.core.di.component.application;

import android.content.Context;

import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.chan.core.base.okhttp.CloudFlareHandlerInterceptor;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.core.di.module.application.AppModule;
import com.github.k1rakishou.chan.core.di.module.application.ExecutorsModule;
import com.github.k1rakishou.chan.core.di.module.application.GsonModule;
import com.github.k1rakishou.chan.core.di.module.application.HelperModule;
import com.github.k1rakishou.chan.core.di.module.application.LoaderModule;
import com.github.k1rakishou.chan.core.di.module.application.ManagerModule;
import com.github.k1rakishou.chan.core.di.module.application.NetModule;
import com.github.k1rakishou.chan.core.di.module.application.ParserModule;
import com.github.k1rakishou.chan.core.di.module.application.RepositoryModule;
import com.github.k1rakishou.chan.core.di.module.application.RoomDatabaseModule;
import com.github.k1rakishou.chan.core.di.module.application.SiteModule;
import com.github.k1rakishou.chan.core.di.module.application.UseCaseModule;
import com.github.k1rakishou.chan.core.helper.ImageSaverFileManagerWrapper;
import com.github.k1rakishou.chan.core.manager.watcher.BookmarkBackgroundWatcherWorker;
import com.github.k1rakishou.chan.core.manager.watcher.FilterWatcherWorker;
import com.github.k1rakishou.chan.core.receiver.ImageSaverBroadcastReceiver;
import com.github.k1rakishou.chan.core.receiver.ReplyNotificationDeleteIntentBroadcastReceiver;
import com.github.k1rakishou.chan.core.site.SiteBase;
import com.github.k1rakishou.chan.features.filter_watches.FilterWatchesPresenter;
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2Service;
import com.github.k1rakishou.chan.ui.widget.SnackbarWrapper;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.model.di.ModelComponent;

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
        HelperModule.class,
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
    ActivityComponent.Builder activityComponentBuilder();

    void inject(Chan application);
    void inject(BookmarkBackgroundWatcherWorker bookmarkBackgroundWatcherWorker);
    void inject(FilterWatcherWorker filterWatcherWorker);
    void inject(SiteBase siteBase);
    void inject(SnackbarWrapper snackbarWrapper);
    void inject(ReplyNotificationDeleteIntentBroadcastReceiver replyNotificationDeleteIntentBroadcastReceiver);
    void inject(CloudFlareHandlerInterceptor cloudFlareHandlerInterceptor);
    void inject(FilterWatchesPresenter filterWatchesPresenter);
    void inject(ImageSaverV2Service imageSaverV2Service);
    void inject(ImageSaverBroadcastReceiver cancelImagesDownloadBroadcastReceiver);

    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder appContext(Context application);
        @BindsInstance
        Builder application(Chan application);
        @BindsInstance
        Builder themeEngine(ThemeEngine themeEngine);
        @BindsInstance
        Builder fileManager(FileManager fileManager);
        @BindsInstance
        Builder imageSaverFileManagerWrapper(ImageSaverFileManagerWrapper imageSaverFileManagerWrapper);
        @BindsInstance
        Builder applicationCoroutineScope(CoroutineScope applicationCoroutineScope);
        @BindsInstance
        Builder okHttpDns(Dns okHttpDns);
        @BindsInstance
        Builder okHttpProtocols(Chan.OkHttpProtocols okHttpProtocols);
        @BindsInstance
        Builder appConstants(AppConstants appConstants);
        @BindsInstance
        Builder modelMainComponent(ModelComponent modelComponent);
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
