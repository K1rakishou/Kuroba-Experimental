package com.github.k1rakishou.chan.core.di.module.application

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.chan.core.base.okhttp.DownloaderOkHttpClient
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.helper.FilterWatcherNotificationHelper
import com.github.k1rakishou.chan.core.helper.ImageSaverFileManagerWrapper
import com.github.k1rakishou.chan.core.helper.KurobaSystemNotifications
import com.github.k1rakishou.chan.core.helper.LastPageNotificationsHelper
import com.github.k1rakishou.chan.core.helper.LastViewedPostNoInfoHolder
import com.github.k1rakishou.chan.core.helper.ReplyNotificationsHelper
import com.github.k1rakishou.chan.core.helper.ThreadDownloaderFileManagerWrapper
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.impl.Chan4CloudFlareImagePreloader
import com.github.k1rakishou.chan.core.loader.impl.PostExtraContentLoader
import com.github.k1rakishou.chan.core.loader.impl.PostHighlightFilterLoader
import com.github.k1rakishou.chan.core.loader.impl.PrefetchLoader
import com.github.k1rakishou.chan.core.loader.impl.ThirdEyeLoader
import com.github.k1rakishou.chan.core.manager.ApplicationCrashNotifier
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.Chan4CaptchaNotifierManager
import com.github.k1rakishou.chan.core.manager.Chan4CloudFlareImagePreloaderManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.DownloadedImagesManager
import com.github.k1rakishou.chan.core.manager.FirewallBypassManager
import com.github.k1rakishou.chan.core.manager.HapticFeedbackManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.NotificationAutoDismissManager
import com.github.k1rakishou.chan.core.manager.OnDemandContentLoaderManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.PostFilterHighlightManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager
import com.github.k1rakishou.chan.core.manager.PrefetchStateManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.ReportManager
import com.github.k1rakishou.chan.core.manager.RevealedSpoilerImagesManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.manager.SeenPostsManager
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThirdEyeManager
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.manager.ThreadPostSearchManager
import com.github.k1rakishou.chan.core.manager.WebViewTaskManager
import com.github.k1rakishou.chan.core.site.ParserRepository
import com.github.k1rakishou.chan.core.site.SiteRegistry
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator
import com.github.k1rakishou.chan.core.site.parser.ReplyParser
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser
import com.github.k1rakishou.chan.core.usecase.BookmarkFilterWatchableThreadsUseCase
import com.github.k1rakishou.chan.core.usecase.CatalogDataPreloader
import com.github.k1rakishou.chan.core.usecase.FetchThreadBookmarkInfoUseCase
import com.github.k1rakishou.chan.core.usecase.GetThreadBookmarkGroupIdsUseCase
import com.github.k1rakishou.chan.core.usecase.ParsePostRepliesUseCase
import com.github.k1rakishou.chan.core.usecase.ThreadDataPreloader
import com.github.k1rakishou.chan.core.usecase.ThreadDownloaderPersistPostsInDatabaseUseCase
import com.github.k1rakishou.chan.core.watcher.BookmarkForegroundWatcher
import com.github.k1rakishou.chan.core.watcher.BookmarkWatcherCoordinator
import com.github.k1rakishou.chan.core.watcher.BookmarkWatcherDelegate
import com.github.k1rakishou.chan.core.watcher.FilterWatcherCoordinator
import com.github.k1rakishou.chan.core.watcher.FilterWatcherDelegate
import com.github.k1rakishou.chan.features.download.media.ImageSaverV2ServiceDelegate
import com.github.k1rakishou.chan.features.download.thread.ThreadDownloadProgressNotifier
import com.github.k1rakishou.chan.features.download.thread.ThreadDownloadingCoordinator
import com.github.k1rakishou.chan.features.download.thread.ThreadDownloadingDelegate
import com.github.k1rakishou.chan.features.posting.LastReplyRepository
import com.github.k1rakishou.chan.features.posting.PostingServiceDelegate
import com.github.k1rakishou.chan.features.posting.solvers.two_captcha.TwoCaptchaSolver
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarStateManager
import com.github.k1rakishou.chan.features.webview.HeadlessWebViewTaskExecutor
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.compose.snackbar.manager.SnackbarManagerFactory
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_logger.Logger.deps
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.repository.BoardRepository
import com.github.k1rakishou.model.repository.BookmarksRepository
import com.github.k1rakishou.model.repository.ChanFilterRepository
import com.github.k1rakishou.model.repository.ChanFilterWatchRepository
import com.github.k1rakishou.model.repository.ChanPostHideRepository
import com.github.k1rakishou.model.repository.ChanPostImageRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.repository.ChanSavedReplyRepository
import com.github.k1rakishou.model.repository.ChanThreadViewableInfoRepository
import com.github.k1rakishou.model.repository.CompositeCatalogRepository
import com.github.k1rakishou.model.repository.HistoryNavigationRepository
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository
import com.github.k1rakishou.model.repository.SeenPostRepository
import com.github.k1rakishou.model.repository.SiteRepository
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository
import com.github.k1rakishou.model.repository.ThreadDownloadRepository
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import com.github.k1rakishou.v2.KurobaSettings
import com.google.gson.Gson
import com.squareup.moshi.Moshi
import dagger.Lazy
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
class ManagerModule {
  @Provides
  @Singleton
  fun provideNotificationManagerCompat(applicationContext: Context): NotificationManagerCompat {
    return NotificationManagerCompat.from(applicationContext)
  }

  @Provides
  @Singleton
  fun provideSiteManager(
    appScope: CoroutineScope,
    kurobaSettings: KurobaSettings,
    siteRepository: Lazy<SiteRepository>
  ): SiteManager {
    deps("SiteManager")
    return SiteManager(
      appScope,
      AppModuleAndroidUtils.isDevBuild,
      kurobaSettings.application.verboseLogs.readBlocking(),
      siteRepository,
      SiteRegistry
    )
  }

  @Provides
  @Singleton
  fun provideBoardManager(
    appScope: CoroutineScope,
    boardRepository: Lazy<BoardRepository>,
    currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
  ): BoardManager {
    deps("BoardManager")
    return BoardManager(
      appScope,
      AppModuleAndroidUtils.isDevBuild,
      boardRepository,
      currentOpenedDescriptorStateManager
    )
  }

  @Provides
  @Singleton
  fun provideFilterEngine(
    chanFilterManager: ChanFilterManager
  ): FilterEngine {
    deps("FilterEngine")
    return FilterEngine(chanFilterManager)
  }

  @Provides
  @Singleton
  fun provideReplyManager(
    kurobaSettings: KurobaSettings,
    applicationVisibilityManager: ApplicationVisibilityManager,
    appConstants: AppConstants,
    moshi: Moshi,
    gson: Gson
  ): ReplyManager {
    deps("ReplyManager")
    return ReplyManager(
      kurobaSettings,
      applicationVisibilityManager,
      appConstants,
      moshi,
      gson
    )
  }

  @Provides
  @Singleton
  fun providePageRequestManager(
    kurobaSettings: KurobaSettings,
    siteManager: SiteManager,
    boardManager: BoardManager
  ): PageRequestManager {
    deps("PageRequestManager")
    return PageRequestManager(
      kurobaSettings,
      siteManager,
      boardManager
    )
  }

  @Provides
  @Singleton
  fun provideArchivesManager(
    appContext: Context,
    kurobaSettings: KurobaSettings,
    gson: Lazy<Gson>,
    appConstants: AppConstants,
    appScope: CoroutineScope
  ): ArchivesManager {
    deps("ArchivesManager")
    return ArchivesManager(
      gson,
      appContext,
      appScope,
      appConstants,
      kurobaSettings.application.verboseLogs.readBlocking()
    )
  }

  @Provides
  @Singleton
  fun provideReportManager(
    kurobaSettings: KurobaSettings,
    appScope: CoroutineScope,
    appContext: Context,
    appConstants: AppConstants,
    okHttpClient: Lazy<ProxiedOkHttpClient>,
    gson: Lazy<Gson>
  ): ReportManager {
    deps("ReportManager")
    return ReportManager(
      kurobaSettings,
      appScope,
      appContext,
      okHttpClient,
      gson,
      appConstants
    )
  }

  @Provides
  @Singleton
  fun provideSettingsNotificationManager(): SettingsNotificationManager {
    deps("SettingsNotificationManager")
    return SettingsNotificationManager()
  }

  @Provides
  @Singleton
  fun provideOnDemandContentLoader(
    appScope: CoroutineScope,
    appConstants: AppConstants,
    prefetchLoader: Lazy<PrefetchLoader>,
    postExtraContentLoader: Lazy<PostExtraContentLoader>,
    chan4CloudFlareImagePreloader: Lazy<Chan4CloudFlareImagePreloader>,
    postHighlightFilterLoader: Lazy<PostHighlightFilterLoader>,
    thirdEyeLoader: Lazy<ThirdEyeLoader>,
    chanThreadManager: ChanThreadManager
  ): OnDemandContentLoaderManager {
    deps("OnDemandContentLoaderManager")

    val loadersLazy = lazy<List<OnDemandContentLoader>>(
      LazyThreadSafetyMode.SYNCHRONIZED
    ) {
      val loaders = mutableListOf<OnDemandContentLoader>()

      // Order matters! Loaders at the beginning of the list will be executed first.
      // If a loader depends on the results of another loader then add it before
      // the other loader.
      loaders.add(chan4CloudFlareImagePreloader.get())
      loaders.add(prefetchLoader.get())
      loaders.add(postExtraContentLoader.get())
      loaders.add(postHighlightFilterLoader.get())
      loaders.add(thirdEyeLoader.get())

      return@lazy loaders
    }

    return OnDemandContentLoaderManager(
      scope = appScope,
      appConstants = appConstants,
      dispatcher = Dispatchers.Default,
      loadersLazy = loadersLazy,
      chanThreadManager = chanThreadManager
    )
  }

  @Provides
  @Singleton
  fun provideSeenPostsManager(
    appScope: CoroutineScope,
    kurobaSettings: KurobaSettings,
    chanThreadsCache: ChanThreadsCache,
    chanCatalogSnapshotCache: ChanCatalogSnapshotCache,
    seenPostRepository: SeenPostRepository
  ): SeenPostsManager {
    deps("SeenPostsManager")
    return SeenPostsManager(
      appScope,
      kurobaSettings,
      chanThreadsCache,
      chanCatalogSnapshotCache,
      seenPostRepository
    )
  }

  @Provides
  @Singleton
  fun providePrefetchStateManager(): PrefetchStateManager {
    deps("PrefetchStateManager")
    return PrefetchStateManager()
  }

  @Provides
  @Singleton
  fun provideApplicationVisibilityManager(): ApplicationVisibilityManager {
    deps("ApplicationVisibilityManager")
    return ApplicationVisibilityManager()
  }

  @Provides
  @Singleton
  fun provideHistoryNavigationManager(
    kurobaSettings: KurobaSettings,
    appScope: CoroutineScope,
    historyNavigationRepository: Lazy<HistoryNavigationRepository>,
    applicationVisibilityManager: Lazy<ApplicationVisibilityManager>,
    currentOpenedDescriptorStateManager: Lazy<CurrentOpenedDescriptorStateManager>
  ): HistoryNavigationManager {
    deps("HistoryNavigationManager")
    return HistoryNavigationManager(
      kurobaSettings,
      appScope,
      historyNavigationRepository,
      applicationVisibilityManager,
      currentOpenedDescriptorStateManager
    )
  }

  @Provides
  @Singleton
  fun providePostFilterManager(
    appScope: CoroutineScope,
    kurobaSettings: KurobaSettings,
    chanThreadsCache: ChanThreadsCache
  ): PostFilterManager {
    deps("PostFilterManager")
    return PostFilterManager(
      kurobaSettings.application.verboseLogs.readBlocking(),
      appScope,
      chanThreadsCache
    )
  }

  @Provides
  @Singleton
  fun provideBookmarksManager(
    appScope: CoroutineScope,
    kurobaSettings: KurobaSettings,
    applicationVisibilityManager: Lazy<ApplicationVisibilityManager>,
    archivesManager: Lazy<ArchivesManager>,
    bookmarksRepository: Lazy<BookmarksRepository>,
    currentOpenedDescriptorStateManager: Lazy<CurrentOpenedDescriptorStateManager>
  ): BookmarksManager {
    deps("BookmarksManager")
    return BookmarksManager(
      AppModuleAndroidUtils.isDevBuild,
      kurobaSettings.application.verboseLogs.readBlocking(),
      appScope,
      applicationVisibilityManager,
      archivesManager,
      bookmarksRepository,
      currentOpenedDescriptorStateManager
    )
  }

  @Provides
  @Singleton
  fun provideReplyParser(
    siteManager: SiteManager,
    parserRepository: ParserRepository
  ): ReplyParser {
    deps("ReplyParser")
    return ReplyParser(
      siteManager,
      parserRepository
    )
  }

  @Provides
  @Singleton
  fun provideFetchThreadBookmarkInfoUseCase(
    kurobaSettings: KurobaSettings,
    bookmarksManager: BookmarksManager,
    archivesManager: ArchivesManager,
    siteManager: SiteManager,
    lastViewedPostNoInfoHolder: LastViewedPostNoInfoHolder,
    fetchThreadBookmarkInfoUseCase: Lazy<FetchThreadBookmarkInfoUseCase>,
    parsePostRepliesUseCase: Lazy<ParsePostRepliesUseCase>,
    replyNotificationsHelper: Lazy<ReplyNotificationsHelper>,
    lastPageNotificationsHelper: Lazy<LastPageNotificationsHelper>,
    currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
  ): BookmarkWatcherDelegate {
    deps("BookmarkWatcherDelegate")
    return BookmarkWatcherDelegate(
      AppModuleAndroidUtils.isDevBuild,
      kurobaSettings,
      bookmarksManager,
      archivesManager,
      siteManager,
      lastViewedPostNoInfoHolder,
      fetchThreadBookmarkInfoUseCase,
      parsePostRepliesUseCase,
      replyNotificationsHelper,
      lastPageNotificationsHelper,
      currentOpenedDescriptorStateManager
    )
  }

  @Provides
  @Singleton
  fun provideBookmarkForegroundWatcher(
    appScope: CoroutineScope,
    appContext: Context,
    kurobaSettings: KurobaSettings,
    appConstants: AppConstants,
    bookmarksManager: BookmarksManager,
    archivesManager: ArchivesManager,
    bookmarkWatcherDelegate: Lazy<BookmarkWatcherDelegate>,
    applicationVisibilityManager: ApplicationVisibilityManager,
    currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
  ): BookmarkForegroundWatcher {
    deps("BookmarkForegroundWatcher")
    return BookmarkForegroundWatcher(
      kurobaSettings,
      appScope,
      appContext,
      appConstants,
      bookmarksManager,
      archivesManager,
      bookmarkWatcherDelegate,
      applicationVisibilityManager,
      currentOpenedDescriptorStateManager
    )
  }

  @Provides
  @Singleton
  fun provideBookmarkWatcherController(
    appContext: Context,
    appScope: CoroutineScope,
    kurobaSettings: KurobaSettings,
    appConstants: AppConstants,
    bookmarksManager: Lazy<BookmarksManager>,
    bookmarkForegroundWatcher: Lazy<BookmarkForegroundWatcher>
  ): BookmarkWatcherCoordinator {
    deps("BookmarkWatcherCoordinator")
    return BookmarkWatcherCoordinator(
      kurobaSettings,
      appContext,
      appScope,
      appConstants,
      bookmarksManager,
      bookmarkForegroundWatcher
    )
  }

  @Provides
  @Singleton
  fun provideLastViewedPostNoInfoHolder(): LastViewedPostNoInfoHolder {
    deps("LastViewedPostNoInfoHolder")
    return LastViewedPostNoInfoHolder()
  }

  @Provides
  @Singleton
  fun provideReplyNotificationsHelper(
    kurobaSettings: KurobaSettings,
    appContext: Context,
    appScope: CoroutineScope,
    notificationManagerCompat: NotificationManagerCompat,
    bookmarksManager: Lazy<BookmarksManager>,
    chanPostRepository: Lazy<ChanPostRepository>,
    imageLoaderDeprecated: Lazy<ImageLoaderDeprecated>,
    themeEngine: Lazy<ThemeEngine>,
    simpleCommentParser: Lazy<SimpleCommentParser>
  ): ReplyNotificationsHelper {
    deps("ReplyNotificationsHelper")
    return ReplyNotificationsHelper(
      AppModuleAndroidUtils.isDevBuild,
      kurobaSettings,
      appContext,
      appScope,
      notificationManagerCompat,
      AndroidUtils.notificationManager,
      bookmarksManager,
      chanPostRepository,
      imageLoaderDeprecated,
      themeEngine,
      simpleCommentParser
    )
  }

  @Provides
  @Singleton
  fun provideFilterWatcherNotificationHelper(
    appContext: Context,
    notificationManagerCompat: NotificationManagerCompat,
    themeEngine: Lazy<ThemeEngine>
  ): FilterWatcherNotificationHelper {
    deps("FilterWatcherNotificationHelper")
    return FilterWatcherNotificationHelper(
      appContext,
      notificationManagerCompat,
      themeEngine
    )
  }

  @Provides
  @Singleton
  fun provideLastPageNotificationsHelper(
    kurobaSettings: KurobaSettings,
    appContext: Context,
    notificationManagerCompat: NotificationManagerCompat,
    pageRequestManager: Lazy<PageRequestManager>,
    bookmarksManager: BookmarksManager,
    themeEngine: ThemeEngine,
    currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
  ): LastPageNotificationsHelper {
    deps("LastPageNotificationsHelper")
    return LastPageNotificationsHelper(
      AppModuleAndroidUtils.isDevBuild,
      kurobaSettings,
      appContext,
      notificationManagerCompat,
      pageRequestManager,
      bookmarksManager,
      themeEngine,
      currentOpenedDescriptorStateManager
    )
  }

  @Provides
  @Singleton
  fun provideChanThreadViewableInfoManager(
    kurobaSettings: KurobaSettings,
    chanThreadViewableInfoRepository: ChanThreadViewableInfoRepository,
    appScope: CoroutineScope,
    chanThreadsCache: ChanThreadsCache
  ): ChanThreadViewableInfoManager {
    deps("ChanThreadViewableInfoManager")
    return ChanThreadViewableInfoManager(
      kurobaSettings,
      appScope,
      chanThreadViewableInfoRepository,
      chanThreadsCache
    )
  }

  @Provides
  @Singleton
  fun provideSavedReplyManager(
    kurobaSettings: KurobaSettings,
    chanThreadsCache: ChanThreadsCache,
    chanSavedReplyRepository: ChanSavedReplyRepository
  ): SavedReplyManager {
    deps("SavedReplyManager")
    return SavedReplyManager(
      kurobaSettings,
      chanThreadsCache,
      chanSavedReplyRepository
    )
  }

  @Provides
  @Singleton
  fun providePostHideManager(
    kurobaSettings: KurobaSettings,
    chanPostHideRepository: ChanPostHideRepository,
    appScope: CoroutineScope,
    chanThreadsCache: ChanThreadsCache
  ): PostHideManager {
    deps("PostHideManager")
    return PostHideManager(
      kurobaSettings,
      appScope,
      chanPostHideRepository,
      chanThreadsCache
    )
  }

  @Provides
  @Singleton
  fun provideChanFilterManager(
    appScope: CoroutineScope,
    chanFilterRepository: Lazy<ChanFilterRepository>,
    chanPostRepository: Lazy<ChanPostRepository>,
    chanFilterWatchRepository: Lazy<ChanFilterWatchRepository>,
    postFilterManager: Lazy<PostFilterManager>,
    postFilterHighlightManager: Lazy<PostFilterHighlightManager>
  ): ChanFilterManager {
    deps("ChanFilterManager")
    return ChanFilterManager(
      AppModuleAndroidUtils.isDevBuild,
      appScope,
      chanFilterRepository,
      chanPostRepository,
      chanFilterWatchRepository,
      postFilterHighlightManager,
      postFilterManager
    )
  }

  @Singleton
  @Provides
  fun provideThreadBookmarkGroupEntryManager(
    kurobaSettings: KurobaSettings,
    appScope: CoroutineScope,
    threadBookmarkGroupEntryRepository: Lazy<ThreadBookmarkGroupRepository>,
    bookmarksManager: Lazy<BookmarksManager>,
    getThreadBookmarkGroupIdsUseCase: Lazy<GetThreadBookmarkGroupIdsUseCase>
  ): ThreadBookmarkGroupManager {
    deps("ThreadBookmarkGroupManager")
    return ThreadBookmarkGroupManager(
      kurobaSettings,
      appScope,
      threadBookmarkGroupEntryRepository,
      bookmarksManager,
      getThreadBookmarkGroupIdsUseCase
    )
  }

  @Singleton
  @Provides
  fun provideChan4CloudFlareImagePreloaderManager(
    appScope: CoroutineScope,
    kurobaSettings: KurobaSettings,
    proxiedOkHttpClient: ProxiedOkHttpClient,
    chanThreadsCache: ChanThreadsCache,
    prefetchStateManager: PrefetchStateManager
  ): Chan4CloudFlareImagePreloaderManager {
    deps("Chan4CloudFlareImagePreloaderManager")
    return Chan4CloudFlareImagePreloaderManager(
      appScope,
      kurobaSettings,
      proxiedOkHttpClient,
      chanThreadsCache,
      prefetchStateManager
    )
  }

  @Singleton
  @Provides
  fun provideChanThreadManager(
    siteManager: Lazy<SiteManager>,
    bookmarksManager: Lazy<BookmarksManager>,
    postFilterManager: Lazy<PostFilterManager>,
    savedReplyManager: Lazy<SavedReplyManager>,
    chanThreadsCache: Lazy<ChanThreadsCache>,
    chanPostRepository: Lazy<ChanPostRepository>,
    chanThreadLoaderCoordinator: Lazy<ChanThreadLoaderCoordinator>,
    threadDataPreloadUseCase: Lazy<ThreadDataPreloader>,
    catalogDataPreloadUseCase: Lazy<CatalogDataPreloader>
  ): ChanThreadManager {
    deps("ChanThreadManager")
    return ChanThreadManager(
      siteManager,
      bookmarksManager,
      postFilterManager,
      savedReplyManager,
      chanThreadsCache,
      chanPostRepository,
      chanThreadLoaderCoordinator,
      threadDataPreloadUseCase,
      catalogDataPreloadUseCase
    )
  }

  @Singleton
  @Provides
  fun providePostingLimitationsInfoManager(
    siteManager: SiteManager
  ): PostingLimitationsInfoManager {
    deps("PostingLimitationsInfoManager")
    return PostingLimitationsInfoManager(siteManager)
  }

  @Singleton
  @Provides
  fun provideFilterWatcherCoordinator(
    appContext: Context,
    appScope: CoroutineScope,
    kurobaSettings: KurobaSettings,
    appConstants: AppConstants,
    chanFilterManager: Lazy<ChanFilterManager>
  ): FilterWatcherCoordinator {
    deps("FilterWatcherCoordinator")
    return FilterWatcherCoordinator(
      kurobaSettings,
      appContext,
      appScope,
      appConstants,
      chanFilterManager
    )
  }

  @Singleton
  @Provides
  fun provideFilterWatcherDelegate(
    kurobaSettings: KurobaSettings,
    appScope: CoroutineScope,
    boardManager: BoardManager,
    bookmarksManager: BookmarksManager,
    chanFilterManager: ChanFilterManager,
    chanPostRepository: ChanPostRepository,
    siteManager: SiteManager,
    bookmarkFilterWatchableThreadsUseCase: BookmarkFilterWatchableThreadsUseCase,
    filterWatcherNotificationHelper: FilterWatcherNotificationHelper
  ): FilterWatcherDelegate {
    deps("FilterWatcherDelegate")
    return FilterWatcherDelegate(
      AppModuleAndroidUtils.isDevBuild,
      kurobaSettings,
      appScope,
      boardManager,
      bookmarksManager,
      chanFilterManager,
      chanPostRepository,
      siteManager,
      bookmarkFilterWatchableThreadsUseCase,
      filterWatcherNotificationHelper
    )
  }

  @Singleton
  @Provides
  fun provideImageSaverV2Delegate(
    appScope: CoroutineScope,
    appConstants: AppConstants,
    kurobaSettings: KurobaSettings,
    cacheHandler: CacheHandler,
    downloaderOkHttpClient: DownloaderOkHttpClient,
    notificationManagerCompat: NotificationManagerCompat,
    imageSaverFileManagerWrapper: ImageSaverFileManagerWrapper,
    siteResolver: SiteResolver,
    chanPostImageRepository: ChanPostImageRepository,
    imageDownloadRequestRepository: ImageDownloadRequestRepository,
    chanThreadManager: ChanThreadManager,
    threadDownloadManager: ThreadDownloadManager,
    notificationAutoDismissManager: NotificationAutoDismissManager,
    downloadedImagesManager: DownloadedImagesManager
  ): ImageSaverV2ServiceDelegate {
    deps("ImageSaverV2ServiceDelegate")
    return ImageSaverV2ServiceDelegate(
      kurobaSettings,
      appScope,
      appConstants,
      cacheHandler,
      downloaderOkHttpClient,
      notificationManagerCompat,
      imageSaverFileManagerWrapper,
      siteResolver,
      chanPostImageRepository,
      imageDownloadRequestRepository,
      chanThreadManager,
      threadDownloadManager,
      notificationAutoDismissManager,
      downloadedImagesManager
    )
  }

  @Singleton
  @Provides
  fun provideTwoCaptchaSolver(
    kurobaSettings: KurobaSettings,
    gson: Gson,
    siteManager: SiteManager,
    proxiedOkHttpClient: Lazy<ProxiedOkHttpClient>
  ): TwoCaptchaSolver {
    deps("TwoCaptchaSolver")
    return TwoCaptchaSolver(
      kurobaSettings,
      gson,
      siteManager,
      proxiedOkHttpClient
    )
  }

  @Singleton
  @Provides
  fun providePostingServiceDelegate(
    kurobaSettings: KurobaSettings,
    appScope: CoroutineScope,
    appConstants: AppConstants,
    replyManager: Lazy<ReplyManager>,
    siteManager: Lazy<SiteManager>,
    boardManager: Lazy<BoardManager>,
    bookmarksManager: Lazy<BookmarksManager>,
    savedReplyManager: Lazy<SavedReplyManager>,
    chanThreadManager: Lazy<ChanThreadManager>,
    lastReplyRepository: Lazy<LastReplyRepository>,
    chanPostRepository: Lazy<ChanPostRepository>,
    twoCaptchaSolver: Lazy<TwoCaptchaSolver>,
    captchaHolder: Lazy<CaptchaHolder>
  ): PostingServiceDelegate {
    deps("PostingServiceDelegate")
    return PostingServiceDelegate(
      kurobaSettings,
      appScope,
      appConstants,
      replyManager,
      siteManager,
      boardManager,
      bookmarksManager,
      savedReplyManager,
      chanThreadManager,
      lastReplyRepository,
      chanPostRepository,
      twoCaptchaSolver,
      captchaHolder
    )
  }

  @Singleton
  @Provides
  fun provideThreadDownloadManager(
    appConstants: AppConstants,
    appScope: CoroutineScope,
    threadDownloaderFileManagerWrapper: Lazy<ThreadDownloaderFileManagerWrapper>,
    threadDownloadRepository: Lazy<ThreadDownloadRepository>,
    chanPostRepository: Lazy<ChanPostRepository>
  ): ThreadDownloadManager {
    deps("ThreadDownloadManager")
    return ThreadDownloadManager(
      appConstants,
      appScope,
      threadDownloaderFileManagerWrapper,
      threadDownloadRepository,
      chanPostRepository
    )
  }

  @Singleton
  @Provides
  fun provideThreadDownloadingCoordinator(
    kurobaSettings: KurobaSettings,
    appContext: Context,
    appScope: CoroutineScope,
    appConstants: AppConstants,
    threadDownloadManager: Lazy<ThreadDownloadManager>
  ): ThreadDownloadingCoordinator {
    deps("ThreadDownloadingCoordinator")
    return ThreadDownloadingCoordinator(
      kurobaSettings,
      appContext,
      appScope,
      appConstants,
      threadDownloadManager
    )
  }

  @Singleton
  @Provides
  fun provideThreadDownloadingDelegate(
    kurobaSettings: KurobaSettings,
    appConstants: AppConstants,
    downloaderOkHttpClient: Lazy<DownloaderOkHttpClient>,
    siteManager: SiteManager,
    siteResolver: SiteResolver,
    threadDownloadManager: ThreadDownloadManager,
    chanPostRepository: ChanPostRepository,
    chanPostImageRepository: ChanPostImageRepository,
    threadDownloaderFileManagerWrapper: ThreadDownloaderFileManagerWrapper,
    threadDownloadProgressNotifier: ThreadDownloadProgressNotifier,
    threadDownloaderPersistPostsInDatabaseUseCase: ThreadDownloaderPersistPostsInDatabaseUseCase
  ): ThreadDownloadingDelegate {
    deps("ThreadDownloadingDelegate")
    return ThreadDownloadingDelegate(
      kurobaSettings,
      appConstants,
      downloaderOkHttpClient,
      siteManager,
      siteResolver,
      threadDownloadManager,
      chanPostRepository,
      chanPostImageRepository,
      threadDownloaderFileManagerWrapper,
      threadDownloadProgressNotifier,
      threadDownloaderPersistPostsInDatabaseUseCase
    )
  }

  @Singleton
  @Provides
  fun provideCurrentOpenedDescriptorStateManager(
    kurobaSettings: KurobaSettings,
  ): CurrentOpenedDescriptorStateManager {
    deps("CurrentOpenedDescriptorStateManager")
    return CurrentOpenedDescriptorStateManager(
      kurobaSettings
    )
  }

  @Singleton
  @Provides
  fun provideCompositeCatalogManager(
    compositeCatalogRepository: CompositeCatalogRepository,
    currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
  ): CompositeCatalogManager {
    deps("CompositeCatalogManager")
    return CompositeCatalogManager(
      compositeCatalogRepository,
      currentOpenedDescriptorStateManager
    )
  }

  @Singleton
  @Provides
  fun providePostFilterHighlightManager(): PostFilterHighlightManager {
    deps("PostFilterHighlightManager")
    return PostFilterHighlightManager()
  }

  @Singleton
  @Provides
  fun provideThirdEyeManager(
    appContext: Context,
    kurobaSettings: KurobaSettings,
    chanThreadsCache: ChanThreadsCache,
    appConstants: AppConstants,
    moshi: Moshi,
    fileManager: FileManager
  ): ThirdEyeManager {
    deps("ThirdEyeManager")
    return ThirdEyeManager(
      kurobaSettings,
      appContext,
      appConstants,
      moshi,
      chanThreadsCache,
      fileManager
    )
  }

  @Singleton
  @Provides
  fun provideFirewallBypassManager(
    appScope: CoroutineScope,
    siteManager: SiteManager,
    webViewTaskManager: WebViewTaskManager
  ): FirewallBypassManager {
    deps("FirewallBypassManager")
    return FirewallBypassManager(
      appScope = appScope,
      siteManager = siteManager,
      webViewTaskManager = webViewTaskManager
    )
  }

  @Singleton
  @Provides
  fun provideWebViewTaskManager(
    applicationVisibilityManager: ApplicationVisibilityManager,
    headlessWebViewTaskExecutor: HeadlessWebViewTaskExecutor
  ): WebViewTaskManager {
    deps("WebViewTaskManager")
    return WebViewTaskManager(
      applicationVisibilityManager = applicationVisibilityManager,
      headlessWebViewTaskExecutor = headlessWebViewTaskExecutor
    )
  }

  @Singleton
  @Provides
  fun provideApplicationCrashNotifier(): ApplicationCrashNotifier {
    deps("ApplicationCrashNotifier")
    return ApplicationCrashNotifier()
  }

  @Singleton
  @Provides
  fun provideNotificationAutoDismissManager(
    appScope: CoroutineScope,
    notificationManagerCompat: NotificationManagerCompat
  ): NotificationAutoDismissManager {
    deps("NotificationAutoDismissManager")
    return NotificationAutoDismissManager(
      appScope,
      notificationManagerCompat
    )
  }

  @Singleton
  @Provides
  fun provideKurobaToolbarStateManager(globalUiStateHolder: GlobalUiStateHolder): KurobaToolbarStateManager {
    Logger.deps("KurobaToolbarStateManager")
    return KurobaToolbarStateManager(globalUiStateHolder)
  }

  @Singleton
  @Provides
  fun provideThreadPostSearchManager(
    chanThreadsCache: ChanThreadsCache,
    postFilterManager: PostFilterManager,
    postHideManager: PostHideManager
  ): ThreadPostSearchManager {
    Logger.deps("ThreadPostSearchManager")
    return ThreadPostSearchManager(
      chanThreadsCache = chanThreadsCache,
      postFilterManager = postFilterManager,
      postHideManager = postHideManager
    )
  }

  @Singleton
  @Provides
  fun provideSnackbarManagerFactory(
    appContext: Context,
    globalUiStateHolder: GlobalUiStateHolder
  ): SnackbarManagerFactory {
    Logger.deps("SnackbarManagerFactory")
    return SnackbarManagerFactory(
      appContext = appContext,
      globalUiStateHolder = globalUiStateHolder
    )
  }

  @Singleton
  @Provides
  fun provideRevealedSpoilerImagesManager(): RevealedSpoilerImagesManager {
    Logger.deps("RevealedSpoilerImagesManager")
    return RevealedSpoilerImagesManager()
  }

  @Singleton
  @Provides
  fun provideDownloadedImagesManager(fileManager: FileManager): DownloadedImagesManager {
    Logger.deps("DownloadedImagesManager")
    return DownloadedImagesManager(fileManager)
  }

  @Singleton
  @Provides
  fun provideHapticFeedbackManager(): HapticFeedbackManager {
    Logger.deps("HapticFeedbackManager")
    return HapticFeedbackManager()
  }

  @Singleton
  @Provides
  fun provideChan4CaptchaNotifierManager(
    appScope: CoroutineScope,
    appResources: AppResources,
    kurobaSystemNotifications: KurobaSystemNotifications,
    siteManager: SiteManager,
    chanThreadManager: ChanThreadManager,
    applicationVisibilityManager: ApplicationVisibilityManager
  ): Chan4CaptchaNotifierManager {
    Logger.deps("Chan4CaptchaNotifierManager")
    return Chan4CaptchaNotifierManager(
      appScope = appScope,
      appResources = appResources,
      kurobaSystemNotifications = kurobaSystemNotifications,
      siteManager = siteManager,
      chanThreadManager = chanThreadManager,
      applicationVisibilityManager = applicationVisibilityManager
    )
  }

}
