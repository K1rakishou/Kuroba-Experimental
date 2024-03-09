package com.github.k1rakishou.chan.core.di.module.application

import android.content.Context
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.helper.SitesSetupControllerOpenNotifier
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsV1UseCase
import com.github.k1rakishou.chan.features.media_viewer.helper.ExoPlayerCache
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerGoToImagePostHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerGoToPostHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerOpenAlbumHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerOpenThreadHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerScrollerHelper
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutHelper
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadProgressNotifier
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
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
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
    proxiedOkHttpClient: RealProxiedOkHttpClient,
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
    appConstants: AppConstants,
    appContext: Context,
    fileManager: FileManager,
    replyManager: ReplyManager
  ): ShareFilePicker {
    Logger.deps("ShareFilePicker");
    return ShareFilePicker(
      appConstants,
      fileManager,
      replyManager,
      appContext
    )
  }

  @Provides
  @Singleton
  fun provideLocalFilePicker(
    appConstants: AppConstants,
    fileManager: FileManager,
    replyManager: ReplyManager,
    applicationScope: CoroutineScope
  ): LocalFilePicker {
    Logger.deps("LocalFilePicker");
    return LocalFilePicker(
      appConstants,
      fileManager,
      replyManager,
      applicationScope
    )
  }

  @Provides
  @Singleton
  fun provideRemoteFilePicker(
    applicationScope: CoroutineScope,
    appConstants: AppConstants,
    proxiedOkHttpClient: Lazy<ProxiedOkHttpClient>,
    fileManager: FileManager,
    replyManager: ReplyManager,
    cacheHandler: Lazy<CacheHandler>
  ): RemoteFilePicker {
    Logger.deps("RemoteFilePicker");
    return RemoteFilePicker(
      appConstants,
      fileManager,
      replyManager,
      applicationScope,
      proxiedOkHttpClient,
      cacheHandler
    )
  }

  @Provides
  @Singleton
  fun provideImagePickHelper(
    appContext: Context,
    replyManagerLazy: Lazy<ReplyManager>,
    imageLoaderV2Lazy: Lazy<ImageLoaderV2>,
    shareFilePickerLazy: Lazy<ShareFilePicker>,
    localFilePickerLazy: Lazy<LocalFilePicker>,
    remoteFilePickerLazy: Lazy<RemoteFilePicker>,
    currentOpenedDescriptorStateManagerLazy: Lazy<CurrentOpenedDescriptorStateManager>,
    replyLayoutHelperLazy: Lazy<ReplyLayoutHelper>
  ): ImagePickHelper {
    Logger.deps("ImagePickHelper");

    return ImagePickHelper(
      appContext,
      replyManagerLazy,
      imageLoaderV2Lazy,
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
    Logger.deps("MediaViewerScrollerHelper");
    return MediaViewerScrollerHelper(chanThreadManager)
  }

  @Provides
  @Singleton
  fun provideMediaViewerGoToImagePostHelper(chanThreadManager: ChanThreadManager): MediaViewerGoToImagePostHelper {
    Logger.deps("MediaViewerGoToImagePostHelper");
    return MediaViewerGoToImagePostHelper(chanThreadManager)
  }

  @Provides
  @Singleton
  fun provideMediaViewerGoToPostHelper(chanThreadManager: ChanThreadManager): MediaViewerGoToPostHelper {
    Logger.deps("MediaViewerGoToPostHelper");
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
    Logger.deps("MediaViewerOpenAlbumHelper");
    return MediaViewerOpenAlbumHelper(chanThreadManager)
  }

  @Provides
  @Singleton
  fun provideExoPlayerDiskCache(context: Context, appConstants: AppConstants): ExoPlayerCache {
    Logger.deps("ExoPlayerCache");
    return ExoPlayerCache(context, appConstants)
  }

  @Provides
  @Singleton
  fun provideAppSettingsUpdateAppRefreshHelper(): AppSettingsUpdateAppRefreshHelper {
    Logger.deps("AppSettingsUpdateAppRefreshHelper");
    return AppSettingsUpdateAppRefreshHelper()
  }

  @Provides
  @Singleton
  fun provideChanLoadProgressNotifier(): ChanLoadProgressNotifier {
    Logger.deps("ChanLoadProgressNotifier");
    return ChanLoadProgressNotifier()
  }

  @Provides
  @Singleton
  fun provideThreadDownloadProgressNotifier(): ThreadDownloadProgressNotifier {
    Logger.deps("ThreadDownloadProgressNotifier");
    return ThreadDownloadProgressNotifier()
  }

  @Provides
  @Singleton
  fun provideAppRestarter(): AppRestarter {
    Logger.deps("AppRestarter");
    return AppRestarter()
  }

  @Provides
  @Singleton
  fun provideChan4CaptchaSolverHelper(moshi: Lazy<Moshi>): Chan4CaptchaSolverHelper {
    Logger.deps("Chan4CaptchaSolverHelper");
    return Chan4CaptchaSolverHelper(moshi)
  }

  @Provides
  @Singleton
  fun provideSiteSelectionControllerOpenNotifier(): SitesSetupControllerOpenNotifier {
    Logger.deps("SiteSelectionControllerOpenNotifier");
    return SitesSetupControllerOpenNotifier()
  }

  @Provides
  @Singleton
  fun provideFileHelper(appContext: Context): FileHelper {
    Logger.deps("FileHelper");
    return FileHelper(appContext)
  }

  @Provides
  @Singleton
  fun provideAppResources(appContext: Context): AppResources {
    return AppResources(appContext)
  }

  @Provides
  @Singleton
  fun provideGlobalUiStateHolder(): GlobalUiStateHolder {
    Logger.deps("GlobalUiStateHolder");
    return GlobalUiStateHolder()
  }

  @Provides
  @Singleton
  fun provideReplyLayoutHelper(
    replyManagerLazy: Lazy<ReplyManager>,
    siteManagerLazy: Lazy<SiteManager>,
    boardManagerLazy: Lazy<BoardManager>,
    postingLimitationsInfoManagerLazy: Lazy<PostingLimitationsInfoManager>,
    imageLoaderV2Lazy: Lazy<ImageLoaderV2>
  ): ReplyLayoutHelper {
    Logger.deps("ReplyLayoutHelper")
    return ReplyLayoutHelper(
      replyManagerLazy,
      siteManagerLazy,
      boardManagerLazy,
      postingLimitationsInfoManagerLazy,
      imageLoaderV2Lazy
    )
  }

}