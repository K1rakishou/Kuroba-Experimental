package com.github.k1rakishou.chan.core.di.module.application

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.manager.SeenPostsManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThirdEyeManager
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsV1UseCase
import com.github.k1rakishou.chan.core.site.parser.ReplyParser
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanGetBoardsUseCase
import com.github.k1rakishou.chan.core.usecase.BookmarkFilterWatchableThreadsUseCase
import com.github.k1rakishou.chan.core.usecase.CatalogDataPreloader
import com.github.k1rakishou.chan.core.usecase.ClearPostingCookies
import com.github.k1rakishou.chan.core.usecase.DownloadThemeJsonFilesUseCase
import com.github.k1rakishou.chan.core.usecase.ExportBackupFileUseCase
import com.github.k1rakishou.chan.core.usecase.ExportDownloadedThreadAsHtmlUseCase
import com.github.k1rakishou.chan.core.usecase.ExportDownloadedThreadMediaUseCase
import com.github.k1rakishou.chan.core.usecase.ExportFiltersUseCase
import com.github.k1rakishou.chan.core.usecase.ExtractPostMapInfoHolderUseCase
import com.github.k1rakishou.chan.core.usecase.FetchThreadBookmarkInfoUseCase
import com.github.k1rakishou.chan.core.usecase.FilterOutHiddenImagesUseCase
import com.github.k1rakishou.chan.core.usecase.GetThreadBookmarkGroupIdsUseCase
import com.github.k1rakishou.chan.core.usecase.GlobalSearchUseCase
import com.github.k1rakishou.chan.core.usecase.ImportBackupFileUseCase
import com.github.k1rakishou.chan.core.usecase.ImportFiltersUseCase
import com.github.k1rakishou.chan.core.usecase.InstallMpvNativeLibrariesFromGithubUseCase
import com.github.k1rakishou.chan.core.usecase.InstallMpvNativeLibrariesFromLocalDirectoryUseCase
import com.github.k1rakishou.chan.core.usecase.KurobaSettingsImportUseCase
import com.github.k1rakishou.chan.core.usecase.LoadBoardFlagsUseCase
import com.github.k1rakishou.chan.core.usecase.LoadChan4CaptchaUseCase
import com.github.k1rakishou.chan.core.usecase.ParsePostRepliesUseCase
import com.github.k1rakishou.chan.core.usecase.RefreshChan4CaptchaTicketUseCase
import com.github.k1rakishou.chan.core.usecase.SearxImageSearchUseCase
import com.github.k1rakishou.chan.core.usecase.ThreadDataPreloader
import com.github.k1rakishou.chan.core.usecase.ThreadDownloaderPersistPostsInDatabaseUseCase
import com.github.k1rakishou.chan.core.usecase.TwoCaptchaCheckBalanceUseCase
import com.github.k1rakishou.chan.core.usecase.UploadFileToCatBoxUseCase
import com.github.k1rakishou.chan.core.usecase.YandexImageSearchUseCase
import com.github.k1rakishou.chan.features.posting.solvers.two_captcha.TwoCaptchaSolver
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger.deps
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanFilterWatchRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.repository.ChanSavedReplyRepository
import com.github.k1rakishou.model.repository.DatabaseMetaRepository
import com.google.gson.Gson
import com.squareup.moshi.Moshi
import dagger.Lazy
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
class UseCaseModule {
  @Provides
  @Singleton
  fun provideExtractReplyPostsPositionsFromPostsListUseCase(
    savedReplyManager: SavedReplyManager,
    siteManager: SiteManager,
    chanThreadManager: ChanThreadManager,
    postFilterManager: PostFilterManager,
    chanFilterManager: ChanFilterManager,
    thirdEyeManager: ThirdEyeManager
  ): ExtractPostMapInfoHolderUseCase {
    deps("ExtractPostMapInfoHolderUseCase")
    return ExtractPostMapInfoHolderUseCase(
      savedReplyManager,
      siteManager,
      chanThreadManager,
      postFilterManager,
      chanFilterManager,
      thirdEyeManager
    )
  }

  @Provides
  @Singleton
  fun provideFetchThreadBookmarkInfoUseCase(
    appScope: CoroutineScope,
    okHttpClient: Lazy<ProxiedOkHttpClient>,
    siteManager: SiteManager,
    bookmarksManager: BookmarksManager,
    appConstants: AppConstants
  ): FetchThreadBookmarkInfoUseCase {
    deps("FetchThreadBookmarkInfoUseCase")
    return FetchThreadBookmarkInfoUseCase(
      AppModuleAndroidUtils.isDevBuild(),
      ChanSettings.verboseLogs.get(),
      appScope,
      okHttpClient,
      siteManager,
      bookmarksManager,
      appConstants
    )
  }

  @Provides
  @Singleton
  fun provideParsePostRepliesUseCase(
    appScope: CoroutineScope,
    replyParser: Lazy<ReplyParser>,
    siteManager: SiteManager,
    chanSavedReplyRepository: Lazy<ChanSavedReplyRepository>
  ): ParsePostRepliesUseCase {
    deps("ParsePostRepliesUseCase")
    return ParsePostRepliesUseCase(
      appScope,
      replyParser,
      siteManager,
      chanSavedReplyRepository
    )
  }

  @Provides
  @Singleton
  fun provideKurobaSettingsImportUseCase(
    gson: Gson,
    fileManager: FileManager,
    siteManager: SiteManager,
    boardManager: BoardManager,
    chanFilterManager: ChanFilterManager,
    postHideManager: PostHideManager,
    bookmarksManager: BookmarksManager,
    chanPostRepository: ChanPostRepository
  ): KurobaSettingsImportUseCase {
    deps("KurobaSettingsImportUseCase")
    return KurobaSettingsImportUseCase(
      gson,
      fileManager,
      siteManager,
      boardManager,
      chanFilterManager,
      postHideManager,
      bookmarksManager,
      chanPostRepository
    )
  }

  @Provides
  @Singleton
  fun provideGlobalSearchUseCase(
    siteManager: SiteManager,
    themeEngine: ThemeEngine,
    simpleCommentParser: Lazy<SimpleCommentParser>
  ): GlobalSearchUseCase {
    deps("GlobalSearchUseCase")
    return GlobalSearchUseCase(
      siteManager,
      themeEngine,
      simpleCommentParser
    )
  }

  @Provides
  @Singleton
  fun provideFilterOutHiddenImagesUseCase(
    postHideManager: PostHideManager,
    postFilterManager: PostFilterManager
  ): FilterOutHiddenImagesUseCase {
    deps("FilterOutHiddenImagesUseCase")
    return FilterOutHiddenImagesUseCase(
      postHideManager,
      postFilterManager
    )
  }

  @Provides
  @Singleton
  fun provideBookmarkFilterWatchableThreadsUseCase(
    appConstants: AppConstants,
    appScope: CoroutineScope,
    boardManager: BoardManager,
    bookmarksManager: BookmarksManager,
    threadBookmarkGroupManager: ThreadBookmarkGroupManager,
    chanFilterManager: ChanFilterManager,
    siteManager: SiteManager,
    proxiedOkHttpClient: Lazy<ProxiedOkHttpClient>,
    simpleCommentParser: Lazy<SimpleCommentParser>,
    filterEngine: FilterEngine,
    chanPostRepository: ChanPostRepository,
    chanFilterWatchRepository: ChanFilterWatchRepository
  ): BookmarkFilterWatchableThreadsUseCase {
    deps("BookmarkFilterWatchableThreadsUseCase")
    return BookmarkFilterWatchableThreadsUseCase(
      ChanSettings.verboseLogs.get(),
      appConstants,
      boardManager,
      bookmarksManager,
      threadBookmarkGroupManager,
      chanFilterManager,
      siteManager,
      appScope,
      proxiedOkHttpClient,
      simpleCommentParser,
      filterEngine,
      chanPostRepository,
      chanFilterWatchRepository
    )
  }

  @Provides
  @Singleton
  fun provideExportBackupFileUseCase(
    appContext: Context,
    appConstants: AppConstants,
    databaseMetaRepository: DatabaseMetaRepository,
    fileManager: FileManager
  ): ExportBackupFileUseCase {
    deps("ExportBackupFileUseCase")
    return ExportBackupFileUseCase(
      appContext,
      appConstants,
      databaseMetaRepository,
      fileManager
    )
  }

  @Provides
  @Singleton
  fun provideImportBackupFileUseCase(
    appContext: Context,
    appConstants: AppConstants,
    fileManager: FileManager
  ): ImportBackupFileUseCase {
    deps("ImportBackupFileUseCase")
    return ImportBackupFileUseCase(
      appContext,
      appConstants,
      fileManager
    )
  }

  @Provides
  @Singleton
  fun provideTwoCaptchaCheckBalanceUseCase(
    twoCaptchaSolver: Lazy<TwoCaptchaSolver>
  ): TwoCaptchaCheckBalanceUseCase {
    deps("TwoCaptchaCheckBalanceUseCase")
    return TwoCaptchaCheckBalanceUseCase(twoCaptchaSolver)
  }

  @Provides
  @Singleton
  fun provideDownloadThemeJsonFilesUseCase(
    proxiedOkHttpClient: RealProxiedOkHttpClient,
    moshi: Moshi,
    themeEngine: ThemeEngine
  ): DownloadThemeJsonFilesUseCase {
    deps("DownloadThemeJsonFilesUseCase")
    return DownloadThemeJsonFilesUseCase(
      proxiedOkHttpClient,
      moshi,
      themeEngine
    )
  }

  @Provides
  @Singleton
  fun provideExportDownloadedThreadAsHtmlUseCase(
    appContext: Context,
    appConstants: AppConstants,
    fileManager: FileManager,
    chanPostRepository: ChanPostRepository
  ): ExportDownloadedThreadAsHtmlUseCase {
    deps("ExportDownloadedThreadAsHtmlUseCase")
    return ExportDownloadedThreadAsHtmlUseCase(
      appContext,
      appConstants,
      fileManager,
      chanPostRepository
    )
  }

  @Provides
  @Singleton
  fun provideThreadDownloaderPersistPostsInDatabaseUseCase(
    siteManager: SiteManager,
    chanThreadLoaderCoordinator: Lazy<ChanThreadLoaderCoordinator>,
    parsePostsV1UseCase: ParsePostsV1UseCase,
    chanPostRepository: ChanPostRepository,
    proxiedOkHttpClient: RealProxiedOkHttpClient
  ): ThreadDownloaderPersistPostsInDatabaseUseCase {
    deps("ThreadDownloaderPersistPostsInDatabaseUseCase")
    return ThreadDownloaderPersistPostsInDatabaseUseCase(
      siteManager,
      chanThreadLoaderCoordinator,
      parsePostsV1UseCase,
      chanPostRepository,
      proxiedOkHttpClient
    )
  }

  @Provides
  @Singleton
  fun provideParsePostsV1UseCase(
    chanPostRepository: ChanPostRepository,
    filterEngine: FilterEngine,
    postFilterManager: PostFilterManager,
    postHideManager: PostHideManager,
    savedReplyManager: SavedReplyManager,
    boardManager: BoardManager,
    chanLoadProgressNotifier: ChanLoadProgressNotifier
  ): ParsePostsV1UseCase {
    deps("ParsePostsV1UseCase")
    return ParsePostsV1UseCase(
      ChanSettings.verboseLogs.get(),
      chanPostRepository,
      filterEngine,
      postFilterManager,
      postHideManager,
      savedReplyManager,
      boardManager,
      chanLoadProgressNotifier
    )
  }

  @Provides
  @Singleton
  fun provideSearxImageSearchUseCase(
    proxiedOkHttpClient: RealProxiedOkHttpClient,
    moshi: Moshi
  ): SearxImageSearchUseCase {
    deps("SearxImageSearchUseCase")
    return SearxImageSearchUseCase(
      proxiedOkHttpClient,
      moshi
    )
  }

  @Provides
  @Singleton
  fun provideThreadDataPreloadUseCase(
    seenPostsManager: Lazy<SeenPostsManager>,
    chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>,
    savedReplyManager: Lazy<SavedReplyManager>,
    postHideManager: Lazy<PostHideManager>,
    chanPostRepository: Lazy<ChanPostRepository>
  ): ThreadDataPreloader {
    deps("ThreadDataPreloadUseCase")
    return ThreadDataPreloader(
      seenPostsManager,
      chanThreadViewableInfoManager,
      savedReplyManager,
      postHideManager,
      chanPostRepository
    )
  }

  @Provides
  @Singleton
  fun provideCatalogDataPreloadUseCase(
    boardManager: BoardManager,
    postHideManager: PostHideManager,
    chanCatalogSnapshotRepository: ChanCatalogSnapshotRepository,
    seenPostsManager: Lazy<SeenPostsManager>
  ): CatalogDataPreloader {
    deps("CatalogDataPreloadUseCase")
    return CatalogDataPreloader(
      boardManager,
      postHideManager,
      chanCatalogSnapshotRepository,
      seenPostsManager
    )
  }

  @Provides
  @Singleton
  fun provideExportFiltersUseCase(
    fileManager: FileManager,
    chanFilterManager: ChanFilterManager,
    moshi: Moshi
  ): ExportFiltersUseCase {
    deps("ExportFiltersUseCase")
    return ExportFiltersUseCase(
      fileManager,
      chanFilterManager,
      moshi
    )
  }

  @Provides
  @Singleton
  fun provideImportFiltersUseCase(
    fileManager: FileManager,
    chanFilterManager: ChanFilterManager,
    moshi: Moshi
  ): ImportFiltersUseCase {
    deps("ImportFiltersUseCase")
    return ImportFiltersUseCase(
      fileManager,
      chanFilterManager,
      moshi
    )
  }

  @Provides
  @Singleton
  fun provideInstallMpvNativeLibrariesUseCase(
    applicationContext: Context,
    appConstants: AppConstants,
    moshi: Moshi,
    proxiedOkHttpClient: ProxiedOkHttpClient
  ): InstallMpvNativeLibrariesFromGithubUseCase {
    deps("InstallMpvNativeLibrariesFromGithubUseCase")
    return InstallMpvNativeLibrariesFromGithubUseCase(
      applicationContext,
      appConstants,
      moshi,
      proxiedOkHttpClient
    )
  }

  @Provides
  @Singleton
  fun provideInstallMpvNativeLibrariesFromLocalDirectoryUseCase(
    appConstants: AppConstants,
    fileManager: FileManager
  ): InstallMpvNativeLibrariesFromLocalDirectoryUseCase {
    deps("InstallMpvNativeLibrariesFromLocalDirectoryUseCase")
    return InstallMpvNativeLibrariesFromLocalDirectoryUseCase(
      appConstants,
      fileManager
    )
  }

  @Provides
  @Singleton
  fun provideGetThreadBookmarkGroupIdsUseCase(
    threadBookmarkGroupManager: Lazy<ThreadBookmarkGroupManager>,
    chanThreadManager: Lazy<ChanThreadManager>
  ): GetThreadBookmarkGroupIdsUseCase {
    deps("GetThreadBookmarkGroupIdsUseCase")
    return GetThreadBookmarkGroupIdsUseCase(
      threadBookmarkGroupManager,
      chanThreadManager
    )
  }

  @Provides
  @Singleton
  fun provideLynxchanGetBoardsUseCase(
    appConstants: AppConstants,
    moshi: Lazy<Moshi>,
    proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>
  ): LynxchanGetBoardsUseCase {
    deps("LynxchanGetBoardsUseCase")
    return LynxchanGetBoardsUseCase(
      appConstants,
      moshi,
      proxiedOkHttpClient
    )
  }

  @Provides
  @Singleton
  fun provideExportDownloadedThreadMediaUseCase(
    appConstants: AppConstants,
    fileManager: FileManager
  ): ExportDownloadedThreadMediaUseCase {
    deps("ExportDownloadedThreadMediaUseCase")
    return ExportDownloadedThreadMediaUseCase(
      appConstants,
      fileManager
    )
  }

  @Provides
  @Singleton
  fun provideYandexImageSearchUseCase(
    proxiedOkHttpClient: RealProxiedOkHttpClient,
    moshi: Moshi
  ): YandexImageSearchUseCase {
    deps("YandexImageSearchUseCase")
    return YandexImageSearchUseCase(proxiedOkHttpClient, moshi)
  }

  @Provides
  @Singleton
  fun provideUploadFileToCatBoxUseCase(
    proxiedOkHttpClient: RealProxiedOkHttpClient,
    fileManager: FileManager
  ): UploadFileToCatBoxUseCase {
    deps("UploadFileToCatBoxUseCase")
    return UploadFileToCatBoxUseCase(proxiedOkHttpClient, fileManager)
  }

  @Provides
  @Singleton
  fun provideLoadChan4CaptchaUseCase(
    moshi: Moshi,
    siteManager: SiteManager,
    proxiedOkHttpClient: ProxiedOkHttpClient
  ): LoadChan4CaptchaUseCase {
    deps("LoadChan4CaptchaUseCase")
    return LoadChan4CaptchaUseCase(moshi, siteManager, proxiedOkHttpClient)
  }

  @Provides
  @Singleton
  fun provideRefreshChan4CaptchaTicketUseCase(
    siteManager: SiteManager,
    loadChan4CaptchaUseCase: LoadChan4CaptchaUseCase
  ): RefreshChan4CaptchaTicketUseCase {
    deps("RefreshChan4CaptchaTicketUseCase")
    return RefreshChan4CaptchaTicketUseCase(siteManager, loadChan4CaptchaUseCase)
  }

  @Provides
  @Singleton
  fun provideLoadBoardFlagsUseCase(proxiedOkHttpClient: ProxiedOkHttpClient): LoadBoardFlagsUseCase {
    deps("LoadBoardFlagsUseCase")
    return LoadBoardFlagsUseCase(proxiedOkHttpClient)
  }

  @Provides
  @Singleton
  fun provideClearSiteCookies(siteManager: SiteManager): ClearPostingCookies {
    return ClearPostingCookies(siteManager)
  }

}
