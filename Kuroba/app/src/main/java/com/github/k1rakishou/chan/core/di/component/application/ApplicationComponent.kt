package com.github.k1rakishou.chan.core.di.component.application

import android.content.Context
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.Chan.OkHttpProtocols
import com.github.k1rakishou.chan.core.base.okhttp.CloudFlareHandlerInterceptor
import com.github.k1rakishou.chan.core.di.ApplicationDependencies
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.application.AppModule
import com.github.k1rakishou.chan.core.di.module.application.HelperModule
import com.github.k1rakishou.chan.core.di.module.application.JsonParserModule
import com.github.k1rakishou.chan.core.di.module.application.LoaderModule
import com.github.k1rakishou.chan.core.di.module.application.ManagerModule
import com.github.k1rakishou.chan.core.di.module.application.NetModule
import com.github.k1rakishou.chan.core.di.module.application.ParserModule
import com.github.k1rakishou.chan.core.di.module.application.RepositoryModule
import com.github.k1rakishou.chan.core.di.module.application.RoomDatabaseModule
import com.github.k1rakishou.chan.core.di.module.application.SiteModule
import com.github.k1rakishou.chan.core.di.module.application.UseCaseModule
import com.github.k1rakishou.chan.core.helper.ImageLoaderFileManagerWrapper
import com.github.k1rakishou.chan.core.helper.ImageSaverFileManagerWrapper
import com.github.k1rakishou.chan.core.helper.ThreadDownloaderFileManagerWrapper
import com.github.k1rakishou.chan.core.receiver.ImageSaverBroadcastReceiver
import com.github.k1rakishou.chan.core.receiver.PostingServiceBroadcastReceiver
import com.github.k1rakishou.chan.core.receiver.ReplyNotificationDeleteIntentBroadcastReceiver
import com.github.k1rakishou.chan.core.site.SiteBase
import com.github.k1rakishou.chan.core.site.sites.CompositeCatalogSite
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanSite
import com.github.k1rakishou.chan.core.watcher.BookmarkBackgroundWatcherWorker
import com.github.k1rakishou.chan.core.watcher.FilterWatcherWorker
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2Service
import com.github.k1rakishou.chan.features.posting.PostingService
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingWorker
import com.github.k1rakishou.chan.ui.widget.SnackbarWrapper
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.dns.DnsOverHttpsSelectorFactory
import com.github.k1rakishou.common.dns.NormalDnsSelectorFactory
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.di.ModelComponent
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Singleton
@Component(
  modules = [
    AppModule::class,
    JsonParserModule::class,
    HelperModule::class,
    LoaderModule::class,
    ManagerModule::class,
    NetModule::class,
    ParserModule::class,
    RepositoryModule::class,
    RoomDatabaseModule::class,
    SiteModule::class,
    UseCaseModule::class
  ]
)
interface ApplicationComponent : ApplicationDependencies {
  fun activityComponentBuilder(): ActivityComponent.Builder
  fun viewModelComponentBuilder(): ViewModelComponent.Builder

  fun inject(application: Chan)
  fun inject(bookmarkBackgroundWatcherWorker: BookmarkBackgroundWatcherWorker)
  fun inject(filterWatcherWorker: FilterWatcherWorker)
  fun inject(threadDownloadingWorker: ThreadDownloadingWorker)
  fun inject(compositeCatalogSite: CompositeCatalogSite)
  fun inject(snackbarWrapper: SnackbarWrapper)
  fun inject(replyNotificationDeleteIntentBroadcastReceiver: ReplyNotificationDeleteIntentBroadcastReceiver)
  fun inject(cloudFlareHandlerInterceptor: CloudFlareHandlerInterceptor)
  fun inject(imageSaverV2Service: ImageSaverV2Service)
  fun inject(postingService: PostingService)
  fun inject(imageSaverBroadcastReceiver: ImageSaverBroadcastReceiver)
  fun inject(postingServiceBroadcastReceiver: PostingServiceBroadcastReceiver)
  fun inject(siteBase: SiteBase)
  fun inject(lynxchanSite: LynxchanSite)

  @Component.Builder
  interface Builder {
    @BindsInstance
    fun appContext(application: Context): Builder
    @BindsInstance
    fun application(application: Chan): Builder
    @BindsInstance
    fun themeEngine(themeEngine: ThemeEngine): Builder
    @BindsInstance
    fun fileManager(fileManager: FileManager): Builder
    @BindsInstance
    fun imageSaverFileManagerWrapper(imageSaverFileManagerWrapper: ImageSaverFileManagerWrapper): Builder
    @BindsInstance
    fun threadDownloaderFileManagerWrapper(threadDownloaderFileManagerWrapper: ThreadDownloaderFileManagerWrapper): Builder
    @BindsInstance
    fun imageLoaderFileManagerWrapper(imageLoaderFileManagerWrapper: ImageLoaderFileManagerWrapper): Builder
    @BindsInstance
    fun applicationCoroutineScope(applicationCoroutineScope: CoroutineScope): Builder
    @BindsInstance
    fun normalDnsSelectorFactory(normalDnsSelectorFactory: NormalDnsSelectorFactory): Builder
    @BindsInstance
    fun dnsOverHttpsSelectorFactory(dnsOverHttpsSelectorFactory: DnsOverHttpsSelectorFactory): Builder
    @BindsInstance
    fun okHttpProtocols(okHttpProtocols: OkHttpProtocols): Builder
    @BindsInstance
    fun appConstants(appConstants: AppConstants): Builder
    @BindsInstance
    fun modelMainComponent(modelComponent: ModelComponent): Builder
    @BindsInstance
    fun appModule(appModule: AppModule): Builder
    @BindsInstance
    fun gsonModule(jsonParserModule: JsonParserModule): Builder
    @BindsInstance
    fun loaderModule(loaderModule: LoaderModule): Builder
    @BindsInstance
    fun managerModule(managerModule: ManagerModule): Builder
    @BindsInstance
    fun netModule(netModule: NetModule): Builder
    @BindsInstance
    fun parserModule(parserModule: ParserModule): Builder
    @BindsInstance
    fun repositoryModule(repositoryModule: RepositoryModule): Builder
    @BindsInstance
    fun roomDatabaseModule(roomDatabaseModule: RoomDatabaseModule): Builder
    @BindsInstance
    fun siteModule(siteModule: SiteModule): Builder
    @BindsInstance
    fun useCaseModule(useCaseModule: UseCaseModule): Builder

    fun build(): ApplicationComponent
  }
}
