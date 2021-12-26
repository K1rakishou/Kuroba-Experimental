package com.github.k1rakishou.chan.core.di.module.application

import android.content.Context
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
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
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadProgressNotifier
import com.github.k1rakishou.chan.ui.helper.AppSettingsUpdateAppRefreshHelper
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
    fileCacheV2: Lazy<FileCacheV2>,
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
      fileCacheV2,
      cacheHandler
    )
  }

  @Provides
  @Singleton
  fun provideImagePickHelper(
    appContext: Context,
    replyManager: Lazy<ReplyManager>,
    imageLoaderV2: Lazy<ImageLoaderV2>,
    shareFilePicker: Lazy<ShareFilePicker>,
    localFilePicker: Lazy<LocalFilePicker>,
    remoteFilePicker: Lazy<RemoteFilePicker>,
  ): ImagePickHelper {
    Logger.deps("ImagePickHelper");
    return ImagePickHelper(
      appContext,
      replyManager,
      imageLoaderV2,
      shareFilePicker,
      localFilePicker,
      remoteFilePicker
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

}