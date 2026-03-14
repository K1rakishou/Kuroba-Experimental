package com.github.k1rakishou.chan.core.di.module.application

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.helper.KurobaSystemNotifications
import com.github.k1rakishou.chan.core.helper.PostHideHelper
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.manager.ThreadPostSearchManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsV1UseCase
import com.github.k1rakishou.chan.features.download.thread.ThreadDownloadProgressNotifier
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutHelper
import com.github.k1rakishou.chan.features.settings.AppSettingsRestartTracker
import com.github.k1rakishou.chan.features.view.media.helper.AlbumThreadControllerHelpers
import com.github.k1rakishou.chan.features.view.media.helper.ExoPlayerCache
import com.github.k1rakishou.chan.features.view.media.helper.MediaViewerGoToImagePostHelper
import com.github.k1rakishou.chan.features.view.media.helper.MediaViewerGoToPostHelper
import com.github.k1rakishou.chan.features.view.media.helper.MediaViewerOpenAlbumHelper
import com.github.k1rakishou.chan.features.view.media.helper.MediaViewerOpenThreadHelper
import com.github.k1rakishou.chan.features.view.media.helper.MediaViewerScrollerHelper
import com.github.k1rakishou.chan.features.webview.HeadlessWebViewTaskExecutor
import com.github.k1rakishou.chan.features.webview.WebViewLastTouchPositionHolder
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaSolverHelper
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.helper.AppSettingsUpdateAppRefreshHelper
import com.github.k1rakishou.chan.ui.helper.FileHelper
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.helper.picker.LocalFilePicker
import com.github.k1rakishou.chan.ui.helper.picker.RemoteFilePicker
import com.github.k1rakishou.chan.ui.helper.picker.ShareFilePicker
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import com.github.k1rakishou.v2.KurobaSettings
import com.squareup.moshi.Moshi
import dagger.Lazy
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
class HelperModule {

  @Provides
  @Singleton
  fun provideChanThreadLoaderCoordinator(
    kurobaSettings: KurobaSettings,
    proxiedOkHttpClient: ProxiedOkHttpClient,
    chanPostRepository: ChanPostRepository,
    chanCatalogSnapshotRepository: ChanCatalogSnapshotRepository,
    appConstants: AppConstants,
    boardManager: BoardManager,
    siteResolver: SiteResolver,
    chanLoadProgressNotifier: ChanLoadProgressNotifier,
    chanThreadsCache: ChanThreadsCache,
    chanCatalogSnapshotCache: ChanCatalogSnapshotCache,
    threadDownloadManager: ThreadDownloadManager,
    parsePostsV1UseCase: ParsePostsV1UseCase
  ): ChanThreadLoaderCoordinator {
    Logger.deps("ChanThreadLoaderCoordinator")
    return ChanThreadLoaderCoordinator(
      kurobaSettings,
      proxiedOkHttpClient,
      chanPostRepository,
      chanCatalogSnapshotRepository,
      appConstants,
      boardManager,
      siteResolver,
      chanLoadProgressNotifier,
      chanThreadsCache,
      chanCatalogSnapshotCache,
      threadDownloadManager,
      parsePostsV1UseCase
    )
  }

  @Provides
  @Singleton
  fun provideShareFilePicker(
    kurobaSettings: KurobaSettings,
    appConstants: AppConstants,
    appContext: Context,
    fileManager: FileManager,
    replyManager: ReplyManager
  ): ShareFilePicker {
    Logger.deps("ShareFilePicker")
    return ShareFilePicker(
      kurobaSettings,
      appConstants,
      fileManager,
      replyManager,
      appContext
    )
  }

  @Provides
  @Singleton
  fun provideLocalFilePicker(
    kurobaSettings: KurobaSettings,
    appConstants: AppConstants,
    fileManager: FileManager,
    replyManager: ReplyManager,
    applicationScope: CoroutineScope
  ): LocalFilePicker {
    Logger.deps("LocalFilePicker")
    return LocalFilePicker(
      kurobaSettings,
      appConstants,
      fileManager,
      replyManager,
      applicationScope
    )
  }

  @Provides
  @Singleton
  fun provideRemoteFilePicker(
    kurobaSettings: KurobaSettings,
    appConstants: AppConstants,
    proxiedOkHttpClient: Lazy<ProxiedOkHttpClient>,
    fileManager: FileManager,
    replyManager: ReplyManager,
    cacheHandler: Lazy<CacheHandler>
  ): RemoteFilePicker {
    Logger.deps("RemoteFilePicker")
    return RemoteFilePicker(
      kurobaSettings,
      appConstants,
      fileManager,
      replyManager,
      proxiedOkHttpClient,
      cacheHandler
    )
  }

  @Provides
  @Singleton
  fun provideImagePickHelper(
    appContext: Context,
    kurobaSettings: KurobaSettings,
    replyManagerLazy: Lazy<ReplyManager>,
    imageLoaderDeprecatedLazy: Lazy<ImageLoaderDeprecated>,
    shareFilePickerLazy: Lazy<ShareFilePicker>,
    localFilePickerLazy: Lazy<LocalFilePicker>,
    remoteFilePickerLazy: Lazy<RemoteFilePicker>,
    currentOpenedDescriptorStateManagerLazy: Lazy<CurrentOpenedDescriptorStateManager>,
    replyLayoutHelperLazy: Lazy<ReplyLayoutHelper>
  ): ImagePickHelper {
    Logger.deps("ImagePickHelper")

    return ImagePickHelper(
      appContext,
      kurobaSettings,
      replyManagerLazy,
      imageLoaderDeprecatedLazy,
      shareFilePickerLazy,
      localFilePickerLazy,
      remoteFilePickerLazy,
      currentOpenedDescriptorStateManagerLazy,
      replyLayoutHelperLazy
    )
  }

  @Provides
  @Singleton
  fun provideMediaViewerScrollerHelper(chanThreadManager: ChanThreadManager): MediaViewerScrollerHelper {
    Logger.deps("MediaViewerScrollerHelper")
    return MediaViewerScrollerHelper(chanThreadManager)
  }

  @Provides
  @Singleton
  fun provideMediaViewerGoToImagePostHelper(chanThreadManager: ChanThreadManager): MediaViewerGoToImagePostHelper {
    Logger.deps("MediaViewerGoToImagePostHelper")
    return MediaViewerGoToImagePostHelper(chanThreadManager)
  }

  @Provides
  @Singleton
  fun provideMediaViewerGoToPostHelper(chanThreadManager: ChanThreadManager): MediaViewerGoToPostHelper {
    Logger.deps("MediaViewerGoToPostHelper")
    return MediaViewerGoToPostHelper(chanThreadManager)
  }

  @Provides
  @Singleton
  fun provideMediaViewerOpenThreadHelper(): MediaViewerOpenThreadHelper {
    Logger.deps("MediaViewerOpenThreadHelper")
    return MediaViewerOpenThreadHelper()
  }

  @Provides
  @Singleton
  fun provideMediaViewerOpenAlbumHelper(chanThreadManager: ChanThreadManager): MediaViewerOpenAlbumHelper {
    Logger.deps("MediaViewerOpenAlbumHelper")
    return MediaViewerOpenAlbumHelper(chanThreadManager)
  }

  @Provides
  @Singleton
  fun provideExoPlayerDiskCache(context: Context, appConstants: AppConstants): ExoPlayerCache {
    Logger.deps("ExoPlayerCache")
    return ExoPlayerCache(context, appConstants)
  }

  @Provides
  @Singleton
  fun provideAppSettingsUpdateAppRefreshHelper(): AppSettingsUpdateAppRefreshHelper {
    Logger.deps("AppSettingsUpdateAppRefreshHelper")
    return AppSettingsUpdateAppRefreshHelper()
  }

  @Provides
  @Singleton
  fun provideChanLoadProgressNotifier(): ChanLoadProgressNotifier {
    Logger.deps("ChanLoadProgressNotifier")
    return ChanLoadProgressNotifier()
  }

  @Provides
  @Singleton
  fun provideThreadDownloadProgressNotifier(): ThreadDownloadProgressNotifier {
    Logger.deps("ThreadDownloadProgressNotifier")
    return ThreadDownloadProgressNotifier()
  }

  @Provides
  @Singleton
  fun provideAppRestarter(): AppRestarter {
    Logger.deps("AppRestarter")
    return AppRestarter()
  }

  @Provides
  @Singleton
  fun provideChan4CaptchaSolverHelper(moshi: Lazy<Moshi>): Chan4CaptchaSolverHelper {
    Logger.deps("Chan4CaptchaSolverHelper")
    return Chan4CaptchaSolverHelper(moshi)
  }

  @Provides
  @Singleton
  fun provideFileHelper(appContext: Context): FileHelper {
    Logger.deps("FileHelper")
    return FileHelper(appContext)
  }

  @Provides
  @Singleton
  fun provideAppResources(appContext: Context): AppResources {
    Logger.deps("AppResources")
    return AppResources(appContext)
  }

  @Provides
  @Singleton
  fun provideGlobalUiStateHolder(appResources: AppResources): GlobalUiStateHolder {
    Logger.deps("GlobalUiStateHolder")
    return GlobalUiStateHolder(appResources)
  }

  @Provides
  @Singleton
  fun provideReplyLayoutHelper(
    appContext: Context,
    replyManagerLazy: Lazy<ReplyManager>,
    siteManagerLazy: Lazy<SiteManager>,
    boardManagerLazy: Lazy<BoardManager>,
    postingLimitationsInfoManagerLazy: Lazy<PostingLimitationsInfoManager>,
    imageLoaderDeprecatedLazy: Lazy<ImageLoaderDeprecated>
  ): ReplyLayoutHelper {
    Logger.deps("ReplyLayoutHelper")
    return ReplyLayoutHelper(
      appContext,
      replyManagerLazy,
      siteManagerLazy,
      boardManagerLazy,
      postingLimitationsInfoManagerLazy,
      imageLoaderDeprecatedLazy
    )
  }

  @Provides
  @Singleton
  fun provideAlbumThreadControllerHelpers(): AlbumThreadControllerHelpers {
    Logger.deps("AlbumThreadControllerHelpers")
    return AlbumThreadControllerHelpers()
  }

  @Provides
  @Singleton
  fun provideKurobaSystemNotifications(
    appContext: Context,
    themeEngine: ThemeEngine,
    notificationManagerCompat: NotificationManagerCompat
  ): KurobaSystemNotifications {
    Logger.deps("KurobaSystemNotifications")
    return KurobaSystemNotifications(
      appContext = appContext,
      themeEngine = themeEngine,
      notificationManagerCompat = notificationManagerCompat
    )
  }

  @Singleton
  @Provides
  fun providePostHideHelper(
    kurobaSettings: KurobaSettings,
    postHideManager: PostHideManager,
    postFilterManager: PostFilterManager,
    threadPostSearchManager: ThreadPostSearchManager,
    chanLoadProgressNotifier: ChanLoadProgressNotifier
  ): PostHideHelper {
    Logger.deps("PostHideHelper")
    return PostHideHelper(
      kurobaSettings = kurobaSettings,
      postHideManager = postHideManager,
      postFilterManager = postFilterManager,
      threadPostSearchManager = threadPostSearchManager,
      chanLoadProgressNotifier = chanLoadProgressNotifier
    )
  }

  @Singleton
  @Provides
  fun provideHeadlessWebViewTaskExecutor(
    appContext: Context,
    appScope: CoroutineScope,
    kurobaSettings: KurobaSettings,
    globalUiStateHolder: GlobalUiStateHolder
  ): HeadlessWebViewTaskExecutor {
    Logger.deps("HeadlessWebViewTaskExecutor")
    return HeadlessWebViewTaskExecutor(
      appContext = appContext,
      appScope = appScope,
      kurobaSettings = kurobaSettings,
      globalUiStateHolder = globalUiStateHolder
    )
  }

  @Singleton
  @Provides
  fun provideWebViewLastTouchPositionHolder(
    appContext: Context,
    moshi: Moshi
  ): WebViewLastTouchPositionHolder {
    Logger.deps("WebViewLastTouchPositionHolder")
    return WebViewLastTouchPositionHolder(
      appContext = appContext,
      moshi = moshi
    )
  }

  @Singleton
  @Provides
  fun provideAppSettingsRestartTracker(): AppSettingsRestartTracker {
    Logger.deps("AppSettingsRestartTracker")
    return AppSettingsRestartTracker()
  }

}