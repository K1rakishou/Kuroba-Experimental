package com.github.k1rakishou.chan.core.di.module.activity

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.downloader.ChunkedMediaDownloader
import com.github.k1rakishou.chan.core.di.scope.PerActivity
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.KurobaSystemNotifications
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.helper.ThumbnailLongtapOptionsHelper
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.PostHighlightManager
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadFollowHistoryManager
import com.github.k1rakishou.chan.core.manager.UpdateManager
import com.github.k1rakishou.chan.core.repository.ImportExportRepository
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.usecase.InstallMpvNativeLibrariesFromGithubUseCase
import com.github.k1rakishou.chan.core.usecase.InstallMpvNativeLibrariesFromLocalDirectoryUseCase
import com.github.k1rakishou.chan.features.download.media.ImageSaverV2
import com.github.k1rakishou.chan.features.download.thread.ThreadDownloadingDelegate
import com.github.k1rakishou.chan.features.reply.data.PostFormattingButtonsFactory
import com.github.k1rakishou.chan.features.settings.AppSettingsGraph
import com.github.k1rakishou.chan.features.settings.SettingsScreenKey
import com.github.k1rakishou.chan.features.settings.screen.AppearanceSettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.BehaviourSettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.CachingSettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.CaptchaSolversSettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.DatabaseSummarySettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.DeveloperSettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.ExperimentalSettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.ImportExportSettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.MainSettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.MediaSettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.PluginsSettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.SecuritySettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.SettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.SiteSettingsScreenBuilder
import com.github.k1rakishou.chan.features.settings.screen.WatcherSettingsScreenBuilder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.helper.AppSettingsUpdateAppRefreshHelper
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_logger.Logger.deps
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository
import com.github.k1rakishou.model.repository.SeenPostRepository
import com.github.k1rakishou.v2.KurobaSettings
import dagger.Lazy
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope

@Module
class ActivityModule {

  @PerActivity
  @Provides
  fun provideUpdateManager(
    kurobaSettings: KurobaSettings,
    activity: AppCompatActivity,
    cacheHandler: Lazy<CacheHandler>,
    settingsNotificationManager: SettingsNotificationManager,
    kurobaSystemNotifications: KurobaSystemNotifications,
    proxiedOkHttpClient: Lazy<ProxiedOkHttpClient>,
    dialogFactory: Lazy<DialogFactory>
  ): UpdateManager {
    Logger.deps("UpdateManager")
    return UpdateManager(
      activity,
      kurobaSettings,
      cacheHandler,
      settingsNotificationManager,
      kurobaSystemNotifications,
      proxiedOkHttpClient,
      dialogFactory
    )
  }

  @PerActivity
  @Provides
  fun provideRuntimePermissionHelper(
    activity: AppCompatActivity,
    dialogFactory: DialogFactory
  ): RuntimePermissionsHelper {
    Logger.deps("RuntimePermissionsHelper")
    return RuntimePermissionsHelper(
      activity,
      dialogFactory
    )
  }

  @PerActivity
  @Provides
  fun provideStartActivityStartupHandlerHelper(
    kurobaSettings: KurobaSettings,
    historyNavigationManager: Lazy<HistoryNavigationManager>,
    siteManager: Lazy<SiteManager>,
    boardManager: Lazy<BoardManager>,
    bookmarksManager: Lazy<BookmarksManager>,
    chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>,
    siteResolver: Lazy<SiteResolver>,
    compositeCatalogManager: Lazy<CompositeCatalogManager>,
    notificationManagerCompat: NotificationManagerCompat
  ): StartActivityStartupHandlerHelper {
    Logger.deps("StartActivityStartupHandlerHelper")
    return StartActivityStartupHandlerHelper(
      kurobaSettings,
      historyNavigationManager,
      siteManager,
      boardManager,
      bookmarksManager,
      chanThreadViewableInfoManager,
      siteResolver,
      compositeCatalogManager,
      notificationManagerCompat
    )
  }

  @PerActivity
  @Provides
  fun provideGlobalWindowInsetsManager(): GlobalWindowInsetsManager {
    Logger.deps("GlobalWindowInsetsManager")
    return GlobalWindowInsetsManager()
  }

  @PerActivity
  @Provides
  fun provideThreadFollowHistoryManager(): ThreadFollowHistoryManager {
    Logger.deps("ThreadFollowHistoryManager")
    return ThreadFollowHistoryManager()
  }

  @PerActivity
  @Provides
  fun provideDialogFactory(
    applicationVisibilityManager: Lazy<ApplicationVisibilityManager>,
    themeEngine: Lazy<ThemeEngine>
  ): DialogFactory {
    Logger.deps("DialogFactory")
    return DialogFactory(
      applicationVisibilityManager,
      themeEngine
    )
  }

  @PerActivity
  @Provides
  fun provideThumbnailLongtapOptionsHelper(
    kurobaSettings: KurobaSettings,
    globalWindowInsetsManager: GlobalWindowInsetsManager,
    imageSaverV2: Lazy<ImageSaverV2>
  ): ThumbnailLongtapOptionsHelper {
    Logger.deps("ThumbnailLongtapOptionsHelper")
    return ThumbnailLongtapOptionsHelper(
      kurobaSettings,
      globalWindowInsetsManager,
      imageSaverV2
    )
  }

  @PerActivity
  @Provides
  fun provideFileChooser(activity: AppCompatActivity): FileChooser {
    Logger.deps("FileChooser")
    return FileChooser(activity)
  }

  @PerActivity
  @Provides
  fun providePostHighlightManager(
    currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
  ): PostHighlightManager {
    Logger.deps("PostHighlightManager")
    return PostHighlightManager(currentOpenedDescriptorStateManager)
  }

  @PerActivity
  @Provides
  fun providePostFormattingButtonsFactory(
    boardManagerLazy: Lazy<BoardManager>,
    themeEngineLazy: Lazy<ThemeEngine>
  ): PostFormattingButtonsFactory {
    Logger.deps("PostFormattingButtonsFactory")
    return PostFormattingButtonsFactory(boardManagerLazy, themeEngineLazy)
  }

  @PerActivity
  @Provides
  fun provideSettingsGraph(
    appScope: CoroutineScope,
    kurobaSettings: KurobaSettings,
    appResources: AppResources,
    themeEngine: ThemeEngine,
    chanFilterManager: ChanFilterManager,
    siteManager: SiteManager,
    boardManager: BoardManager,
    compositeCatalogManager: CompositeCatalogManager,
    updateManager: UpdateManager,
    postHideManager: PostHideManager,
    appSettingsUpdateAppRefreshHelper: AppSettingsUpdateAppRefreshHelper,
    fileChooser: FileChooser,
    fileManager: FileManager,
    dialogFactory: DialogFactory,
    appRestarter: AppRestarter,
    importExportRepository: ImportExportRepository,
    threadDownloadingDelegate: ThreadDownloadingDelegate,
    proxyStorage: ProxyStorage,
    cacheHandler: CacheHandler,
    chunkedMediaDownloader: ChunkedMediaDownloader,
    appConstants: AppConstants,
    globalWindowInsetsManager: GlobalWindowInsetsManager,
    installMpvNativeLibrariesFromGithubUseCase: InstallMpvNativeLibrariesFromGithubUseCase,
    installMpvNativeLibrariesFromLocalDirectoryUseCase: InstallMpvNativeLibrariesFromLocalDirectoryUseCase,
    mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository,
    seenPostRepository: SeenPostRepository,
    chanPostRepository: ChanPostRepository,
    settingsNotificationManager: SettingsNotificationManager
  ): AppSettingsGraph {
    deps("SettingsGraph")

    val builders = linkedMapOf<SettingsScreenKey, SettingsScreenBuilder>()
    builders[SettingsScreenKey.Main] = MainSettingsScreenBuilder(
      kurobaSettings = kurobaSettings,
      appResources = appResources,
      chanFilterManager = chanFilterManager,
      siteManager = siteManager,
      updateManager = updateManager,
    )
    builders[SettingsScreenKey.Watchers] = WatcherSettingsScreenBuilder(
      kurobaSettings = kurobaSettings,
      appResources = appResources
    )
    builders[SettingsScreenKey.Appearance] = AppearanceSettingsScreenBuilder(
      kurobaSettings = kurobaSettings,
      appResources = appResources,
      themeEngine = themeEngine
    )
    builders[SettingsScreenKey.Behavior] = BehaviourSettingsScreenBuilder(
      kurobaSettings = kurobaSettings,
      appResources = appResources,
      postHideManager = postHideManager,
      appSettingsUpdateAppRefreshHelper = appSettingsUpdateAppRefreshHelper
    )
    builders[SettingsScreenKey.Media] = MediaSettingsScreenBuilder(
      kurobaSettings = kurobaSettings,
      appResources = appResources
    )
    builders[SettingsScreenKey.ImportExport] = ImportExportSettingsScreenBuilder(
      coroutineScope = appScope,
      appResources = appResources,
      fileChooser = fileChooser,
      fileManager = fileManager,
      dialogFactory = dialogFactory,
      appRestarter = appRestarter,
      importExportRepository = importExportRepository,
      threadDownloadingDelegate = threadDownloadingDelegate,
    )
    builders[SettingsScreenKey.Security] = SecuritySettingsScreenBuilder(
      appResources = appResources,
      proxyStorage = proxyStorage
    )
    builders[SettingsScreenKey.Caching] = CachingSettingsScreenBuilder(
      kurobaSettings = kurobaSettings,
      appResources = appResources,
      cacheHandler = cacheHandler,
      chunkedMediaDownloader = chunkedMediaDownloader,
      appConstants = appConstants,
      dialogFactory = dialogFactory
    )
    builders[SettingsScreenKey.Plugins] = PluginsSettingsScreenBuilder(
      coroutineScope = appScope,
      kurobaSettings = kurobaSettings,
      appResources = appResources,
      appRestarter = appRestarter,
      appConstants = appConstants,
      dialogFactory = dialogFactory,
      fileChooser = fileChooser,
      globalWindowInsetsManager = globalWindowInsetsManager,
      installMpvNativeLibrariesFromGithubUseCase = installMpvNativeLibrariesFromGithubUseCase,
      installMpvNativeLibrariesFromLocalDirectoryUseCase = installMpvNativeLibrariesFromLocalDirectoryUseCase,
    )
    builders[SettingsScreenKey.CaptchaSolvers] = CaptchaSolversSettingsScreenBuilder()
    builders[SettingsScreenKey.Experimental] = ExperimentalSettingsScreenBuilder(
      kurobaSettings = kurobaSettings,
      appResources = appResources,
      appConstants = appConstants
    )
    builders[SettingsScreenKey.Developer] = DeveloperSettingsScreenBuilder(
      kurobaSettings = kurobaSettings,
      appResources = appResources,
      themeEngine = themeEngine,
      appRestarter = appRestarter
    )
    builders[SettingsScreenKey.Database] = DatabaseSummarySettingsScreenBuilder(
      appResources = appResources,
      appConstants = appConstants,
      mediaServiceLinkExtraContentRepository = mediaServiceLinkExtraContentRepository,
      seenPostRepository = seenPostRepository,
      chanPostRepository = chanPostRepository,
    )

    val siteSettingsScreenBuilder = SiteSettingsScreenBuilder(
      appResources = appResources,
      siteManager = siteManager,
      boardManager = boardManager,
      compositeCatalogManager = compositeCatalogManager
    )

    return AppSettingsGraph(
      kurobaSettings = kurobaSettings,
      appResources = appResources,
      siteManager = siteManager,
      settingsNotificationManager = settingsNotificationManager,
      builders = builders,
      siteSettingsScreenBuilder = siteSettingsScreenBuilder
    )
  }

}
